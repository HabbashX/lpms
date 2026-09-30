package com.larv.pharmacy.dashboard;

import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.customer.CustomerAccountRepository;
import com.larv.pharmacy.customer.CustomerRepository;
import com.larv.pharmacy.dashboard.dto.DashboardResponse;
import com.larv.pharmacy.drug.DrugRepository;
import com.larv.pharmacy.inventory.StockBatchRepository;
import com.larv.pharmacy.report.ProfitAggregateRow;
import com.larv.pharmacy.report.TopDrugRow;
import com.larv.pharmacy.sale.SaleItemRepository;
import com.larv.pharmacy.sale.SaleRepository;
import com.larv.pharmacy.settings.SettingKey;
import com.larv.pharmacy.settings.SettingsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Aggregated dashboard statistics. */
@Service
public class DashboardService {

    private static final int TOP_SELLING_LIMIT = 10;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final DrugRepository drugRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public DashboardService(SaleRepository saleRepository,
                            SaleItemRepository saleItemRepository,
                            StockBatchRepository stockBatchRepository,
                            DrugRepository drugRepository,
                            CustomerRepository customerRepository,
                            CustomerAccountRepository accountRepository,
                            SettingsService settingsService,
                            Clock clock) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.drugRepository = drugRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);

        ProfitAggregateRow todayRow = aggregate(today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        ProfitAggregateRow monthRow = aggregate(monthStart.atStartOfDay(zone).toInstant(),
                monthStart.plusMonths(1).atStartOfDay(zone).toInstant());

        int expiringDays = settingsService.getInt(SettingKey.INVENTORY_EXPIRING_SOON_DAYS);
        BigDecimal inventoryValue = MoneyUtil.round2(stockBatchRepository.totalInventoryValue());
        long lowStock = drugRepository.countLowStock();
        long expiringSoon = stockBatchRepository.countExpiringBetween(today, today.plusDays(expiringDays));
        long customerCount = customerRepository.count();
        BigDecimal totalDebt = MoneyUtil.round2(accountRepository.sumBalance());

        List<TopDrugRow> topRows = saleItemRepository.topSelling(
                monthStart.atStartOfDay(zone).toInstant(),
                monthStart.plusMonths(1).atStartOfDay(zone).toInstant(),
                PageRequest.of(0, TOP_SELLING_LIMIT));

        return new DashboardResponse(
                toPeriod(todayRow),
                new DashboardResponse.Month(today.getYear(), today.getMonthValue(),
                        round2(monthRow.revenue()),
                        round2(monthRow.revenue()).subtract(round2(monthRow.cost())),
                        monthRow.salesCount()),
                new DashboardResponse.Inventory(inventoryValue, lowStock, expiringSoon),
                new DashboardResponse.Customers(customerCount, totalDebt),
                topRows.stream()
                        .map(row -> new DashboardResponse.TopSellingDrug(
                                row.drugId(), row.drugName(),
                                row.quantity() == null ? 0 : row.quantity(),
                                MoneyUtil.round2(row.revenue())))
                        .toList());
    }

    private DashboardResponse.Today toPeriod(ProfitAggregateRow row) {
        BigDecimal revenue = MoneyUtil.round2(row.revenue());
        BigDecimal cost = MoneyUtil.round2(row.cost());
        return new DashboardResponse.Today(revenue, revenue.subtract(cost), row.salesCount());
    }

    private ProfitAggregateRow aggregate(java.time.Instant from, java.time.Instant to) {
        ProfitAggregateRow row = saleRepository.aggregateProfit(from, to, null, null, null);
        return row == null ? new ProfitAggregateRow(0L, BigDecimal.ZERO, BigDecimal.ZERO) : row;
    }

    private static BigDecimal round2(BigDecimal value) {
        return MoneyUtil.round2(value);
    }
}
