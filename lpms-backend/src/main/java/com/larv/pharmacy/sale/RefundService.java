package com.larv.pharmacy.sale;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.exception.RefundException;
import com.larv.pharmacy.common.exception.SaleNotFoundException;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.customer.CustomerAccountService;
import com.larv.pharmacy.customer.TransactionDirection;
import com.larv.pharmacy.customer.TransactionType;
import com.larv.pharmacy.drug.Drug;
import com.larv.pharmacy.inventory.InventoryService;
import com.larv.pharmacy.sale.dto.CreateRefundRequest;
import com.larv.pharmacy.sale.dto.RefundItemRequest;
import com.larv.pharmacy.sale.dto.RefundItemResponse;
import com.larv.pharmacy.sale.dto.RefundResponse;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Refunds reverse a (partial) sale without ever deleting it:
 * revenue, COGS and profit are reversed, stock is restored as a new batch with
 * the original cost basis, the customer ledger is credited when the sale was
 * on credit, and an audit record is written.
 */
@Service
public class RefundService {

    private final SaleRepository saleRepository;
    private final RefundRepository refundRepository;
    private final InventoryService inventoryService;
    private final CustomerAccountService customerAccountService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RefundService(SaleRepository saleRepository,
                         RefundRepository refundRepository,
                         InventoryService inventoryService,
                         CustomerAccountService customerAccountService,
                         UserRepository userRepository,
                         AuditService auditService) {
        this.saleRepository = saleRepository;
        this.refundRepository = refundRepository;
        this.inventoryService = inventoryService;
        this.customerAccountService = customerAccountService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RefundResponse refund(Long saleId, CreateRefundRequest request) {
        Sale sale = saleRepository.findWithLockById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        if (sale.getStatus() == SaleStatus.REFUNDED) {
            throw new RefundException("SALE_ALREADY_REFUNDED", "Sale #" + saleId + " is already fully refunded");
        }

        List<SaleItem> items = sale.getItems();
        Map<Long, SaleItem> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));

        List<RefundPlan> plan = new ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            for (SaleItem item : items) {
                if (item.refundableQuantity() > 0) {
                    plan.add(new RefundPlan(item, item.refundableQuantity()));
                }
            }
        } else {
            for (RefundItemRequest requested : request.items()) {
                SaleItem item = byId.get(requested.saleItemId());
                if (item == null) {
                    throw new RefundException("SALE_ITEM_NOT_FOUND",
                            "Sale item " + requested.saleItemId() + " does not belong to sale #" + saleId);
                }
                int quantity = requested.quantity();
                if (quantity > item.refundableQuantity()) {
                    throw new RefundException("REFUND_QUANTITY_EXCEEDED",
                            "Cannot refund " + quantity + " of '" + item.getDrugName()
                                    + "'; only " + item.refundableQuantity() + " remaining refundable");
                }
                plan.add(new RefundPlan(item, quantity));
            }
        }
        if (plan.isEmpty()) {
            throw new RefundException("NOTHING_TO_REFUND", "Sale #" + saleId + " has nothing left to refund");
        }

        // Serialize stock access for the involved drugs (ascending id order).
        List<Long> drugIds = plan.stream().map(p -> p.item().getDrug().getId()).distinct().sorted().toList();
        Map<Long, Drug> lockedDrugs = inventoryService.lockDrugs(drugIds);

        Refund refund = new Refund(sale,
                sale.getCustomer() == null ? null : sale.getCustomer().getId(),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                trimToNull(request.reason()), AuditService.currentUserId().orElse(null));
        refundRepository.save(refund);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<RefundItem> refundItems = new ArrayList<>();

        for (RefundPlan entry : plan) {
            SaleItem item = entry.item();
            int quantity = entry.quantity();

            BigDecimal amount;
            BigDecimal cost;
            boolean completesLine = item.getRefundedQuantity() + quantity == item.getQuantity();
            if (completesLine) {
                // snap to exact remaining values so line totals always reconcile
                amount = item.getRevenue().subtract(item.getRefundedAmount());
                cost = item.getCostTotal().subtract(item.getRefundedCost());
            } else {
                BigDecimal unitRevenue = item.getRevenue()
                        .divide(BigDecimal.valueOf(item.getQuantity()), MoneyUtil.UNIT_COST_SCALE,
                                MoneyUtil.ROUNDING);
                amount = MoneyUtil.round2(unitRevenue.multiply(BigDecimal.valueOf(quantity)));
                cost = MoneyUtil.round2(MoneyUtil.multiply(item.getUnitCostPrice(), quantity));
            }
            BigDecimal profit = amount.subtract(cost);

            RefundItem refundItem = new RefundItem(item, quantity,
                    item.getUnitSellingPrice(), item.getUnitCostPrice(), amount, cost, profit);
            refund.addItem(refundItem);
            refundItems.add(refundItem);

            item.setRefundedQuantity(item.getRefundedQuantity() + quantity);
            item.setRefundedAmount(item.getRefundedAmount().add(amount));
            item.setRefundedCost(item.getRefundedCost().add(cost));

            // restore stock with the historical cost basis (never the current price)
            Drug drug = lockedDrugs.get(item.getDrug().getId());
            inventoryService.addBatch(drug, quantity, item.getUnitCostPrice(),
                    null, "REFUND-" + sale.getId(), null);

            totalAmount = totalAmount.add(amount);
            totalCost = totalCost.add(cost);
        }

        refund.setTotalAmount(totalAmount);
        refund.setTotalCost(totalCost);
        refund.setTotalProfit(totalAmount.subtract(totalCost));
        refundRepository.save(refund);

        // ---- update sale (never delete) --------------------------------
        sale.setRefundedTotal(sale.getRefundedTotal().add(totalAmount));
        sale.setRefundedCost(sale.getRefundedCost().add(totalCost));
        boolean fullyRefunded = items.stream().allMatch(i -> i.getRefundedQuantity() >= i.getQuantity());
        sale.setStatus(fullyRefunded ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED);
        saleRepository.save(sale);

        // ---- customer ledger -------------------------------------------
        if (sale.getCustomer() != null) {
            BigDecimal credit = customerAccountService.refundCredit(sale.getCustomer().getId(), totalAmount);
            if (credit.signum() > 0) {
                customerAccountService.addEntry(sale.getCustomer().getId(), TransactionType.REFUND,
                        TransactionDirection.CREDIT, credit, "REFUND", refund.getId(),
                        "Refund of sale #" + saleId);
            }
        }

        auditService.record(AuditAction.REFUND, "Sale", saleId,
                "Refund " + refund.getId() + " on sale #" + saleId + ": amount " + totalAmount
                        + ", cost " + totalCost
                        + (request.reason() == null ? "" : ", reason: " + request.reason()));

        String username = refund.getCreatedBy() == null ? null : userRepository.findById(refund.getCreatedBy())
                .map(User::getUsername).orElse(null);
        List<RefundItemResponse> itemResponses = refundItems.stream()
                .map(ri -> new RefundItemResponse(
                        ri.getSaleItem().getId(),
                        ri.getSaleItem().getDrugName(),
                        ri.getQuantity(),
                        MoneyUtil.round2(ri.getUnitSellingPrice()),
                        MoneyUtil.round2(ri.getUnitCostPrice()),
                        MoneyUtil.round2(ri.getAmount()),
                        MoneyUtil.round2(ri.getCost()),
                        MoneyUtil.round2(ri.getProfit())))
                .toList();

        return new RefundResponse(refund.getId(), saleId, refund.getCreatedAt(), refund.getReason(),
                MoneyUtil.round2(totalAmount), MoneyUtil.round2(totalCost),
                MoneyUtil.round2(totalAmount.subtract(totalCost)), username, itemResponses);
    }

    private record RefundPlan(SaleItem item, int quantity) {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
