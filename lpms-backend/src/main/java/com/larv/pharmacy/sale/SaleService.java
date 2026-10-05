package com.larv.pharmacy.sale;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.DrugNotFoundException;
import com.larv.pharmacy.common.exception.ExpiredStockException;
import com.larv.pharmacy.common.exception.InsufficientStockException;
import com.larv.pharmacy.common.exception.InvalidSaleException;
import com.larv.pharmacy.common.exception.SaleNotFoundException;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.customer.Customer;
import com.larv.pharmacy.customer.CustomerAccountService;
import com.larv.pharmacy.customer.CustomerRepository;
import com.larv.pharmacy.customer.TransactionDirection;
import com.larv.pharmacy.customer.TransactionType;
import com.larv.pharmacy.drug.Drug;
import com.larv.pharmacy.inventory.CostingService.CostBasis;
import com.larv.pharmacy.inventory.InventoryService;
import com.larv.pharmacy.inventory.StockBatch;
import com.larv.pharmacy.sale.dto.CreateSaleRequest;
import com.larv.pharmacy.sale.dto.CreateSaleItemRequest;
import com.larv.pharmacy.sale.dto.SaleItemResponse;
import com.larv.pharmacy.sale.dto.SaleResponse;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sale processing. A sale is a single atomic transaction that validates the
 * customer and drugs, locks the stock rows, computes revenue / weighted-average
 * COGS / profit, decrements inventory, writes immutable sale lines with the
 * historical cost basis and appends customer-ledger entries when applicable.
 * Any failure rolls the whole sale back.
 */
@Service
public class SaleService {

    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "total", "profitTotal", "status");

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;
    private final CustomerRepository customerRepository;
    private final CustomerAccountService customerAccountService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public SaleService(SaleRepository saleRepository,
                       InventoryService inventoryService,
                       CustomerRepository customerRepository,
                       CustomerAccountService customerAccountService,
                       UserRepository userRepository,
                       AuditService auditService,
                       Clock clock) {
        this.saleRepository = saleRepository;
        this.inventoryService = inventoryService;
        this.customerRepository = customerRepository;
        this.customerAccountService = customerAccountService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Transactional
    public SaleResponse create(CreateSaleRequest request) {
        List<CreateSaleItemRequest> items = request.items();
        Set<Long> drugIds = new HashSet<>();
        for (CreateSaleItemRequest item : items) {
            if (!drugIds.add(item.drugId())) {
                throw new InvalidSaleException("Duplicate drugId " + item.drugId()
                        + " in sale: each drug may appear only once per sale");
            }
        }

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findById(request.customerId()).orElse(null);
            if (customer == null) {
                throw new com.larv.pharmacy.common.exception.CustomerNotFoundException(request.customerId());
            }
            if (!customer.isActive()) {
                throw new com.larv.pharmacy.common.exception.BusinessRuleException("CUSTOMER_INACTIVE",
                        "Customer '" + customer.getName() + "' is inactive");
            }
        }

        PaymentMethod paymentMethod = request.paymentMethod();
        if (paymentMethod == PaymentMethod.CREDIT && customer == null) {
            throw new InvalidSaleException("Credit sales require a customer");
        }

        // Serialize all stock access for the involved drugs (ascending id order).
        Map<Long, Drug> lockedDrugs = inventoryService.lockDrugs(drugIds);
        LocalDate today = LocalDate.now(clock);
        boolean allowExpired = inventoryService.salesAllowExpiredStock();

        // ---- validate stock and compute per-line figures -------------
        List<PreparedLine> lines = new ArrayList<>();
        for (CreateSaleItemRequest item : items) {
            Drug drug = lockedDrugs.get(item.drugId());
            if (drug == null) {
                throw new DrugNotFoundException(item.drugId());
            }
            if (!drug.isActive()) {
                throw new InvalidSaleException("Drug '" + drug.getName() + "' is inactive and cannot be sold");
            }

            int quantity = item.quantity();
            List<StockBatch> batches = inventoryService.remainingBatches(drug.getId());
            int sellable = inventoryService.sellableQuantity(batches, today, allowExpired);
            if (sellable < quantity) {
                int remaining = batches.stream().mapToInt(StockBatch::getRemainingQuantity).sum();
                if (!allowExpired && sellable == 0 && remaining >= quantity) {
                    throw new ExpiredStockException(
                            "Stock for " + drug.getName() + " is expired and cannot be sold");
                }
                throw new InsufficientStockException(drug.getName(), sellable, quantity);
            }

            CostBasis basis = inventoryService.costBasis(batches);
            BigDecimal unitPrice = item.unitSellingPrice() != null ? item.unitSellingPrice() : drug.getSellingPrice();
            if (unitPrice == null) {
                throw new InvalidSaleException("Drug '" + drug.getName()
                        + "' has no default selling price; provide unitSellingPrice");
            }
            BigDecimal lineTotal = MoneyUtil.round2(
                    MoneyUtil.multiply(unitPrice, quantity));
            BigDecimal cost = MoneyUtil.round2(
                    MoneyUtil.multiply(basis.weightedAverageCost(), quantity));
            lines.add(new PreparedLine(drug, batches, quantity, unitPrice,
                    basis.weightedAverageCost(), lineTotal, cost));
        }

        // ---- totals ---------------------------------------------------
        BigDecimal subtotal = lines.stream()
                .map(PreparedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = MoneyUtil.round2(
                request.discount() == null ? BigDecimal.ZERO : request.discount());
        if (discount.compareTo(subtotal) > 0) {
            throw new InvalidSaleException("Discount " + discount + " exceeds subtotal " + subtotal);
        }
        BigDecimal total = subtotal.subtract(discount);

        BigDecimal amountPaid;
        if (request.amountPaid() != null) {
            amountPaid = MoneyUtil.round2(request.amountPaid());
        } else {
            amountPaid = paymentMethod == PaymentMethod.CREDIT ? BigDecimal.ZERO.setScale(2) : total;
        }
        if (amountPaid.signum() < 0 || amountPaid.compareTo(total) > 0) {
            throw new InvalidSaleException("amountPaid must be between 0 and the sale total");
        }
        if (customer == null && amountPaid.compareTo(total) < 0) {
            throw new InvalidSaleException("Sales without a customer must be paid in full");
        }
        BigDecimal amountDue = total.subtract(amountPaid);

        BigDecimal[] discountAllocation = allocateDiscount(discount, lines);

        // ---- persist sale + immutable lines ---------------------------
        Sale sale = new Sale();
        sale.setCustomer(customer);
        sale.setCreatedBy(AuditService.currentUserId().orElse(null));
        sale.setPaymentMethod(paymentMethod);
        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setAmountPaid(amountPaid);
        sale.setAmountDue(amountDue);
        sale.setStatus(SaleStatus.COMPLETED);
        sale = saleRepository.save(sale);

        BigDecimal costTotal = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            PreparedLine line = lines.get(i);
            BigDecimal allocatedDiscount = discountAllocation[i];
            BigDecimal revenue = line.lineTotal().subtract(allocatedDiscount);
            BigDecimal cost = line.cost();
            BigDecimal profit = revenue.subtract(cost);
            costTotal = costTotal.add(cost);

            SaleItem saleItem = new SaleItem(sale, line.drug(), line.drug().getName(), line.quantity(),
                    line.unitSellingPrice(), line.unitCostPrice(), allocatedDiscount,
                    line.lineTotal(), revenue, cost, profit);
            sale.addItem(saleItem);
            // decrement stock (validated above); managed entities flush with the tx
            inventoryService.consume(line.drug(), line.batches(), line.quantity(), today, allowExpired);
        }
        sale.setCostTotal(costTotal);
        sale.setProfitTotal(total.subtract(costTotal));
        saleRepository.save(sale);

        // ---- customer ledger ------------------------------------------
        if (customer != null) {
            customerAccountService.addEntry(customer.getId(), TransactionType.SALE,
                    TransactionDirection.DEBIT, total, "SALE", sale.getId(),
                    "Sale #" + sale.getId());
            if (amountPaid.signum() > 0) {
                customerAccountService.addEntry(customer.getId(), TransactionType.PAYMENT,
                        TransactionDirection.CREDIT, amountPaid, "SALE", sale.getId(),
                        "Payment at sale #" + sale.getId());
            }
        }

        auditService.record(AuditAction.CREATE_SALE, "Sale", sale.getId(),
                "Sale #" + sale.getId() + " total " + total + ", cost " + costTotal
                        + ", profit " + sale.getProfitTotal()
                        + (customer != null ? ", customer '" + customer.getName() + "'" : ""));

        return toResponse(sale, loadItems(sale));
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> list(Long customerId, PaymentMethod paymentMethod, SaleStatus status,
                                           LocalDate from, LocalDate to, Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable, SORTABLE, "createdAt", Sort.Direction.DESC);
        Page<Sale> page = saleRepository.findAll(buildSpec(customerId, paymentMethod, status, from, to), sanitized);
        Map<Long, String> usernames = usernames(page.getContent().stream().map(Sale::getCreatedBy).toList());
        return PageResponse.from(page, sale -> toResponse(sale, sale.getItems(), usernames));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        Sale sale = saleRepository.findByIdWithItems(id).orElseThrow(() -> new SaleNotFoundException(id));
        return toResponse(sale, sale.getItems(),
                usernames(java.util.Arrays.asList(sale.getCreatedBy())));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<SaleItem> loadItems(Sale sale) {
        // cascade ALL persists the items; they are managed within the transaction
        return sale.getItems();
    }

    private SaleResponse toResponse(Sale sale, List<SaleItem> items) {
        return toResponse(sale, items, usernames(java.util.Arrays.asList(sale.getCreatedBy())));
    }

    private SaleResponse toResponse(Sale sale, List<SaleItem> items, Map<Long, String> usernames) {
        List<SaleItemResponse> itemResponses = items.stream()
                .map(item -> new SaleItemResponse(
                        item.getId(),
                        item.getDrug().getId(),
                        item.getDrugName(),
                        item.getQuantity(),
                        MoneyUtil.round2(item.getUnitSellingPrice()),
                        MoneyUtil.round2(item.getUnitCostPrice()),
                        MoneyUtil.round2(item.getDiscountAmount()),
                        MoneyUtil.round2(item.getRevenue()),
                        MoneyUtil.round2(item.getCostTotal()),
                        MoneyUtil.round2(item.getProfit()),
                        item.getRefundedQuantity()))
                .toList();

        return new SaleResponse(
                sale.getId(),
                sale.getCreatedAt(),
                sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                sale.getCreatedBy() == null ? null : usernames.get(sale.getCreatedBy()),
                sale.getPaymentMethod(),
                sale.getStatus(),
                MoneyUtil.round2(sale.getSubtotal()),
                MoneyUtil.round2(sale.getDiscount()),
                MoneyUtil.round2(sale.getTotal()),
                MoneyUtil.round2(sale.getAmountPaid()),
                MoneyUtil.round2(sale.getAmountDue()),
                MoneyUtil.round2(sale.getCostTotal()),
                MoneyUtil.round2(sale.getProfitTotal()),
                MoneyUtil.round2(sale.getRefundedTotal()),
                itemResponses);
    }

    private Map<Long, String> usernames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    /**
     * Pro-rata discount allocation using the largest-remainder method so the
     * allocated amounts always sum exactly to the sale-level discount.
     */
    private static BigDecimal[] allocateDiscount(BigDecimal discount, List<PreparedLine> lines) {
        BigDecimal[] allocation = new BigDecimal[lines.size()];
        if (discount.signum() == 0) {
            java.util.Arrays.fill(allocation, BigDecimal.ZERO.setScale(2));
            return allocation;
        }
        BigDecimal subtotal = lines.stream().map(PreparedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int discountPennies = discount.movePointRight(2).intValueExact();
        long[] floorPennies = new long[lines.size()];
        BigDecimal[] remainder = new BigDecimal[lines.size()];
        long distributed = 0;
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal exactPennies = discount.multiply(lines.get(i).lineTotal())
                    .divide(subtotal, 6, MoneyUtil.ROUNDING)
                    .movePointRight(2);
            floorPennies[i] = exactPennies.longValue();
            remainder[i] = exactPennies.subtract(BigDecimal.valueOf(floorPennies[i]));
            distributed += floorPennies[i];
        }
        long leftover = discountPennies - distributed;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> remainder[b].compareTo(remainder[a]));
        for (int k = 0; leftover > 0; k++, leftover--) {
            floorPennies[order.get((int) (k % order.size()))] += 1;
        }
        for (int i = 0; i < lines.size(); i++) {
            allocation[i] = BigDecimal.valueOf(floorPennies[i]).movePointLeft(2).setScale(2);
        }
        return allocation;
    }

    private Specification<Sale> buildSpec(Long customerId, PaymentMethod paymentMethod, SaleStatus status,
                                          LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        from.atStartOfDay(zone).toInstant()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        to.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private record PreparedLine(Drug drug, List<StockBatch> batches, int quantity,
                                BigDecimal unitSellingPrice, BigDecimal unitCostPrice,
                                BigDecimal lineTotal, BigDecimal cost) {
    }
}
