package com.larv.pharmacy.customer;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.CustomerAccountException;
import com.larv.pharmacy.common.exception.CustomerNotFoundException;
import com.larv.pharmacy.common.exception.InvalidRequestException;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.customer.dto.CreateAdjustmentRequest;
import com.larv.pharmacy.customer.dto.CreatePaymentRequest;
import com.larv.pharmacy.customer.dto.CustomerAccountResponse;
import com.larv.pharmacy.customer.dto.CustomerResponse;
import com.larv.pharmacy.customer.dto.PaymentResponse;
import com.larv.pharmacy.customer.dto.TransactionResponse;
import com.larv.pharmacy.settings.SettingKey;
import com.larv.pharmacy.settings.SettingsService;
import com.larv.pharmacy.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Customer debt ledger. Every balance change appends an immutable
 * {@link CustomerTransaction} inside a transaction that locks the account row,
 * so concurrent sales/payments cannot lose updates and the balance is never
 * overwritten without a ledger entry.
 */
@Service
public class CustomerAccountService {

    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;
    private final CustomerTransactionRepository transactionRepository;
    private final CustomerPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final CustomerService customerService;

    public CustomerAccountService(CustomerRepository customerRepository,
                                  CustomerAccountRepository accountRepository,
                                  CustomerTransactionRepository transactionRepository,
                                  CustomerPaymentRepository paymentRepository,
                                  UserRepository userRepository,
                                  SettingsService settingsService,
                                  AuditService auditService,
                                  CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.customerService = customerService;
    }

    // ------------------------------------------------------------------
    // Account page
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CustomerAccountResponse accountPage(Long customerId, Pageable pageable) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        CustomerAccount account = accountRepository.findById(customerId).orElse(null);

        Pageable sanitized = PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), PaginationUtil.MAX_PAGE_SIZE));
        Page<CustomerTransaction> page = transactionRepository.history(customerId, sanitized);

        Map<Long, String> usernames = userRepository.findAllById(
                        page.getContent().stream()
                                .map(CustomerTransaction::getCreatedBy)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        com.larv.pharmacy.user.User::getId,
                        com.larv.pharmacy.user.User::getUsername,
                        (a, b) -> a));

        PageResponse<TransactionResponse> transactions = PageResponse.from(page,
                tx -> TransactionResponse.from(tx,
                        tx.getCreatedBy() == null ? null : usernames.get(tx.getCreatedBy())));

        return new CustomerAccountResponse(
                CustomerResponse.from(customer),
                MoneyUtil.round2(account == null ? null : account.getTotalPurchases()),
                MoneyUtil.round2(account == null ? null : account.getTotalPaid()),
                MoneyUtil.round2(account == null ? null : account.getTotalRefunds()),
                MoneyUtil.round2(account == null ? BigDecimal.ZERO : account.getBalance()),
                transactions);
    }

    // ------------------------------------------------------------------
    // Ledger primitives
    // ------------------------------------------------------------------

    /**
     * Appends a ledger entry and updates the cached account aggregates.
     * Locks the account row for the duration of the surrounding transaction.
     */
    @Transactional
    public CustomerTransaction addEntry(Long customerId, TransactionType type, TransactionDirection direction,
                                        BigDecimal amount, String referenceType, Long referenceId,
                                        String description) {
        CustomerAccount account = accountRepository.findWithLockById(customerId)
                .orElseThrow(() -> new CustomerAccountException("ACCOUNT_MISSING",
                        "No account found for customer " + customerId));
        return append(account, customerId, type, direction, amount, referenceType, referenceId, description);
    }

    private CustomerTransaction append(CustomerAccount account, Long customerId, TransactionType type,
                                       TransactionDirection direction, BigDecimal amount,
                                       String referenceType, Long referenceId, String description) {
        BigDecimal value = MoneyUtil.round2(amount);
        if (value.signum() <= 0) {
            throw new InvalidRequestException("INVALID_AMOUNT", "Transaction amount must be positive");
        }

        BigDecimal delta = direction == TransactionDirection.DEBIT ? value : value.negate();
        BigDecimal newBalance = MoneyUtil.round2(account.getBalance().add(delta));

        if (newBalance.signum() < 0
                && !settingsService.getBoolean(SettingKey.CUSTOMER_ALLOW_NEGATIVE_BALANCE)) {
            throw new CustomerAccountException("NEGATIVE_BALANCE",
                    "Operation would make the customer balance negative ("
                            + MoneyUtil.round2(newBalance) + "). Enable customer credit balances "
                            + "or reduce the amount.");
        }

        account.setBalance(newBalance);
        switch (type) {
            case SALE -> account.setTotalPurchases(account.getTotalPurchases().add(value));
            case PAYMENT -> account.setTotalPaid(account.getTotalPaid().add(value));
            case REFUND -> account.setTotalRefunds(account.getTotalRefunds().add(value));
            case ADJUSTMENT -> {
                // adjustments only affect the balance
            }
        }
        accountRepository.save(account);

        Long actor = AuditService.currentUserId().orElse(null);
        CustomerTransaction transaction = new CustomerTransaction(customerId, type, value, direction,
                newBalance, referenceType, referenceId, description, actor);
        return transactionRepository.save(transaction);
    }

    /** Current outstanding debt (zero when the account does not exist yet). */
    @Transactional(readOnly = true)
    public BigDecimal currentBalance(Long customerId) {
        return accountRepository.findById(customerId)
                .map(a -> MoneyUtil.round2(a.getBalance()))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /**
     * Portion of a refund that may be credited to the customer's debt.
     * Returns zero when there is no outstanding debt (refund is handled as a
     * cash refund), or the full amount when customer credit balances are enabled.
     */
    @Transactional(readOnly = true)
    public BigDecimal refundCredit(Long customerId, BigDecimal refundAmount) {
        BigDecimal amount = MoneyUtil.round2(refundAmount);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (settingsService.getBoolean(SettingKey.CUSTOMER_ALLOW_NEGATIVE_BALANCE)) {
            return amount;
        }
        BigDecimal balance = currentBalance(customerId);
        if (balance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.min(balance);
    }

    // ------------------------------------------------------------------
    // Payments and adjustments
    // ------------------------------------------------------------------

    @Transactional
    public PaymentResponse pay(Long customerId, CreatePaymentRequest request) {
        Customer customer = customerService.findOrThrow(customerId);
        customerService.assertActive(customer);

        BigDecimal amount = MoneyUtil.round2(request.amount());
        CustomerAccount account = accountRepository.findWithLockById(customerId)
                .orElseThrow(() -> new CustomerAccountException("ACCOUNT_MISSING",
                        "No account found for customer " + customerId));

        if (!settingsService.getBoolean(SettingKey.CUSTOMER_ALLOW_NEGATIVE_BALANCE)
                && amount.compareTo(MoneyUtil.round2(account.getBalance())) > 0) {
            throw new CustomerAccountException("PAYMENT_EXCEEDS_DEBT",
                    "Payment of " + amount + " exceeds the outstanding debt of "
                            + MoneyUtil.round2(account.getBalance()));
        }

        Long actor = AuditService.currentUserId().orElse(null);
        CustomerPayment payment = new CustomerPayment(customerId, amount, request.paymentMethod(),
                trimToNull(request.notes()), actor);
        paymentRepository.save(payment);

        CustomerTransaction transaction = append(account, customerId, TransactionType.PAYMENT,
                TransactionDirection.CREDIT, amount, "PAYMENT", payment.getId(),
                request.notes() == null || request.notes().isBlank() ? "Debt payment" : request.notes());
        payment.setTransactionId(transaction.getId());
        paymentRepository.save(payment);

        auditService.record(AuditAction.CUSTOMER_PAYMENT, "Customer", customerId,
                "Received payment of " + amount + " from '" + customer.getName()
                        + "' (" + request.paymentMethod() + "), balance after: "
                        + transaction.getBalanceAfter());

        return new PaymentResponse(payment.getId(), customerId, amount, request.paymentMethod(),
                request.notes(), transaction.getBalanceAfter(), transaction.getCreatedAt());
    }

    @Transactional
    public TransactionResponse adjust(Long customerId, CreateAdjustmentRequest request) {
        Customer customer = customerService.findOrThrow(customerId);
        customerService.assertActive(customer);

        CustomerTransaction transaction = addEntry(customerId, TransactionType.ADJUSTMENT,
                request.direction(), request.amount(), null, null, request.description());

        auditService.record(AuditAction.CUSTOMER_ADJUSTMENT, "Customer", customerId,
                "Manual " + request.direction() + " adjustment of " + MoneyUtil.round2(request.amount())
                        + " for '" + customer.getName() + "': " + request.description());

        return TransactionResponse.from(transaction, AuditService.currentUserId()
                .flatMap(userRepository::findById)
                .map(com.larv.pharmacy.user.User::getUsername)
                .orElse(null));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
