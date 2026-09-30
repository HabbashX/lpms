package com.larv.pharmacy;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.exception.ExpiredStockException;
import com.larv.pharmacy.common.exception.InsufficientStockException;
import com.larv.pharmacy.common.exception.InvalidSaleException;
import com.larv.pharmacy.customer.CustomerAccountService;
import com.larv.pharmacy.customer.CustomerService;
import com.larv.pharmacy.customer.dto.CreateCustomerRequest;
import com.larv.pharmacy.customer.dto.CreatePaymentRequest;
import com.larv.pharmacy.customer.dto.CustomerResponse;
import com.larv.pharmacy.dashboard.DashboardService;
import com.larv.pharmacy.dashboard.dto.DashboardResponse;
import com.larv.pharmacy.drug.DosageForm;
import com.larv.pharmacy.drug.DrugRepository;
import com.larv.pharmacy.drug.DrugService;
import com.larv.pharmacy.drug.dto.CreateDrugRequest;
import com.larv.pharmacy.drug.dto.DrugResponse;
import com.larv.pharmacy.inventory.InventoryService;
import com.larv.pharmacy.inventory.dto.CreatePurchaseRequest;
import com.larv.pharmacy.inventory.dto.DrugValuationResponse;
import com.larv.pharmacy.report.ProfitReportService;
import com.larv.pharmacy.report.dto.DailyProfitResponse;
import com.larv.pharmacy.sale.RefundService;
import com.larv.pharmacy.sale.SaleService;
import com.larv.pharmacy.sale.SaleStatus;
import com.larv.pharmacy.sale.dto.CreateRefundRequest;
import com.larv.pharmacy.sale.dto.CreateSaleItemRequest;
import com.larv.pharmacy.sale.dto.CreateSaleRequest;
import com.larv.pharmacy.sale.dto.RefundItemRequest;
import com.larv.pharmacy.sale.dto.RefundResponse;
import com.larv.pharmacy.sale.dto.SaleResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full business flow against the real MySQL schema (flyway + validation):
 * purchases -> weighted-average costing -> cash sale -> credit sale ->
 * payments -> partial refunds (with restock at historical cost) -> ledger,
 * daily profit report and dashboard reconciliation.
 *
 * <p>Every test runs in its own transaction and rolls back, so tests are
 * order-independent. All money assertions use {@code isEqualByComparingTo}
 * (scale-insensitive).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndToEndFlowIntegrationTest {

    @BeforeAll
    static void requireDatabase() {
        TestDatabase.assumeAvailable();
    }

    @Autowired private DrugService drugService;
    @Autowired private DrugRepository drugRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private CustomerService customerService;
    @Autowired private CustomerAccountService accountService;
    @Autowired private SaleService saleService;
    @Autowired private RefundService refundService;
    @Autowired private ProfitReportService reportService;
    @Autowired private DashboardService dashboardService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void purchaseSaleRefundAndReportsReconcile() {
        DrugResponse drug = createDrug("IT Paracetamol " + unique());

        inventoryService.recordPurchase(new CreatePurchaseRequest(drug.id(), 100,
                new BigDecimal("2.50"), "MedCo", "B1", LocalDate.now().plusYears(1)));
        inventoryService.recordPurchase(new CreatePurchaseRequest(drug.id(), 50,
                new BigDecimal("3.00"), "MedCo", "B2", LocalDate.now().plusYears(2)));

        assertThat(currentQuantity(drug)).isEqualTo(150);
        DrugValuationResponse valuation = inventoryService.valuationForDrug(drug.id());
        assertThat(valuation.totalQuantity()).isEqualTo(150);
        assertThat(valuation.totalInventoryCost()).isEqualByComparingTo("400.00");
        assertThat(valuation.weightedAverageCost()).isEqualByComparingTo("2.67");

        CustomerResponse customer = customerService.create(
                new CreateCustomerRequest("IT Customer " + unique(), "0500000111", null, null));

        // ---- cash sale: 10 @ 5.00, FEFO consumes batch B1 --------------
        SaleResponse cashSale = saleService.create(new CreateSaleRequest(customer.id(),
                PaymentMethod.CASH,
                List.of(new CreateSaleItemRequest(drug.id(), 10, new BigDecimal("5.00"))),
                BigDecimal.ZERO, new BigDecimal("50.00")));

        assertThat(cashSale.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(cashSale.subtotal()).isEqualByComparingTo("50.00");
        assertThat(cashSale.total()).isEqualByComparingTo("50.00");
        assertThat(cashSale.amountPaid()).isEqualByComparingTo("50.00");
        assertThat(cashSale.cost()).isEqualByComparingTo("26.67");
        assertThat(cashSale.profit()).isEqualByComparingTo("23.33");
        assertThat(cashSale.items().get(0).unitCostPrice()).isEqualByComparingTo("2.67");
        assertThat(currentQuantity(drug)).isEqualTo(140);

        // cash sale with a customer is fully paid -> no debt
        assertThat(accountService.currentBalance(customer.id())).isEqualByComparingTo("0.00");

        // ---- insufficient stock is rejected ---------------------------
        assertThatThrownBy(() -> saleService.create(new CreateSaleRequest(null, PaymentMethod.CASH,
                List.of(new CreateSaleItemRequest(drug.id(), 1000, new BigDecimal("5.00"))),
                null, null)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        // ---- credit sale: 5 @ 5.00 ------------------------------------
        SaleResponse creditSale = saleService.create(new CreateSaleRequest(customer.id(),
                PaymentMethod.CREDIT,
                List.of(new CreateSaleItemRequest(drug.id(), 5, new BigDecimal("5.00"))),
                null, null));

        assertThat(creditSale.amountDue()).isEqualByComparingTo("25.00");
        assertThat(creditSale.amountPaid()).isEqualByComparingTo("0.00");
        // weighted average over remaining batches: (90*2.50 + 50*3.00) / 140 = 2.6786
        assertThat(creditSale.cost()).isEqualByComparingTo("13.39");
        assertThat(accountService.currentBalance(customer.id())).isEqualByComparingTo("25.00");
        assertThat(currentQuantity(drug)).isEqualTo(135);

        // ---- partial payment against the account ----------------------
        accountService.pay(customer.id(), new CreatePaymentRequest(
                new BigDecimal("10.00"), PaymentMethod.CASH, "part payment"));
        assertThat(accountService.currentBalance(customer.id())).isEqualByComparingTo("15.00");

        // ---- partial refund of the cash sale (restock at 2.6667) ------
        RefundResponse refund1 = refundService.refund(cashSale.id(),
                new CreateRefundRequest(
                        List.of(new RefundItemRequest(cashSale.items().get(0).id(), 2)), "damaged"));
        assertThat(refund1.totalAmount()).isEqualByComparingTo("10.00");
        assertThat(refund1.totalCost()).isEqualByComparingTo("5.33");

        SaleResponse cashAfterRefund = saleService.get(cashSale.id());
        assertThat(cashAfterRefund.status()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(cashAfterRefund.refundedTotal()).isEqualByComparingTo("10.00");
        assertThat(cashAfterRefund.items().get(0).refundedQuantity()).isEqualTo(2);
        assertThat(currentQuantity(drug)).isEqualTo(137);
        // refund credit reduces the debt: 15 - 10 = 5
        assertThat(accountService.currentBalance(customer.id())).isEqualByComparingTo("5.00");

        // ---- refund of one unit of the credit sale (restock at 2.6786)
        RefundResponse refund2 = refundService.refund(creditSale.id(),
                new CreateRefundRequest(
                        List.of(new RefundItemRequest(creditSale.items().get(0).id(), 1)), null));
        assertThat(refund2.totalAmount()).isEqualByComparingTo("5.00");
        assertThat(refund2.totalCost()).isEqualByComparingTo("2.68");
        assertThat(currentQuantity(drug)).isEqualTo(138);
        // debt cleared: 5 - 5 = 0
        assertThat(accountService.currentBalance(customer.id())).isEqualByComparingTo("0.00");

        // ---- daily report is net of refunds ---------------------------
        DailyProfitResponse daily = reportService.daily(null);
        assertThat(daily.salesCount()).isEqualTo(2);
        assertThat(daily.revenue()).isEqualByComparingTo("60.00");   // 40 + 20
        assertThat(daily.cost()).isEqualByComparingTo("32.05");      // 21.34 + 10.71
        assertThat(daily.profit()).isEqualByComparingTo("27.95");
        assertThat(daily.profit()).isEqualByComparingTo(
                daily.revenue().subtract(daily.cost()));

        // ---- dashboard reconciles -------------------------------------
        DashboardResponse dashboard = dashboardService.dashboard();
        assertThat(dashboard.today().sales()).isEqualTo(2);
        assertThat(dashboard.today().revenue()).isEqualByComparingTo("60.00");
        assertThat(dashboard.inventory().value()).isEqualByComparingTo("370.51");
        assertThat(dashboard.customers().totalDebt()).isEqualByComparingTo("0.00");
        assertThat(dashboard.topSellingDrugs())
                .filteredOn(top -> drug.id().equals(top.drugId()))
                .singleElement()
                .satisfies(top -> {
                    assertThat(top.quantity()).isEqualTo(12);        // 10-2 + 5-1
                    assertThat(top.revenue()).isEqualByComparingTo("60.00");
                });

        // ---- final valuation ------------------------------------------
        DrugValuationResponse finalValuation = inventoryService.valuationForDrug(drug.id());
        assertThat(finalValuation.totalQuantity()).isEqualTo(138);
        assertThat(finalValuation.totalInventoryCost()).isEqualByComparingTo("370.51");
    }

    @Test
    void expiredStockCannotBeSold() {
        DrugResponse drug = createDrug("IT Expired " + unique());
        inventoryService.recordPurchase(new CreatePurchaseRequest(drug.id(), 10,
                new BigDecimal("1.00"), "MedCo", "EXP-B1", LocalDate.now().plusYears(1)));
        // purchases reject past expiry dates, so expire the batch after the fact:
        // flush the pending inserts, rewrite the expiry, drop stale entities
        entityManager.flush();
        jdbcTemplate.update(
                "update stock_batches set expiration_date = ? where drug_id = ?",
                LocalDate.now().minusDays(1), drug.id());
        entityManager.clear();

        assertThatThrownBy(() -> saleService.create(new CreateSaleRequest(null, PaymentMethod.CASH,
                List.of(new CreateSaleItemRequest(drug.id(), 1, new BigDecimal("5.00"))),
                null, null)))
                .isInstanceOf(ExpiredStockException.class);
    }

    @Test
    void inactiveDrugCannotBeSold() {
        DrugResponse drug = createDrug("IT Inactive " + unique());
        inventoryService.recordPurchase(new CreatePurchaseRequest(drug.id(), 10,
                new BigDecimal("1.00"), "MedCo", "IA-B1", LocalDate.now().plusYears(1)));
        drugService.deactivate(drug.id());

        assertThatThrownBy(() -> saleService.create(new CreateSaleRequest(null, PaymentMethod.CASH,
                List.of(new CreateSaleItemRequest(drug.id(), 1, new BigDecimal("5.00"))),
                null, null)))
                .isInstanceOf(InvalidSaleException.class)
                .hasMessageContaining("inactive");
    }

    private DrugResponse createDrug(String name) {
        return drugService.create(new CreateDrugRequest(
                name, "Generic", "IT-" + UUID.randomUUID(), "Acme", "IT-Category",
                DosageForm.TABLET, "500mg", "tablet", "integration test drug", 0));
    }

    private int currentQuantity(DrugResponse drug) {
        return drugRepository.findById(drug.id()).orElseThrow().getCurrentQuantity();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
