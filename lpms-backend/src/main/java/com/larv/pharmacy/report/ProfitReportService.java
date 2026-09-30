package com.larv.pharmacy.report;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.util.DateRangeUtil;
import com.larv.pharmacy.common.util.DateRangeUtil.DateRange;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.report.dto.DailyProfitResponse;
import com.larv.pharmacy.report.dto.MonthlyProfitResponse;
import com.larv.pharmacy.report.dto.ProfitDetailResponse;
import com.larv.pharmacy.report.dto.ProfitReportResponse;
import com.larv.pharmacy.report.dto.WeeklyProfitResponse;
import com.larv.pharmacy.sale.SaleItemRepository;
import com.larv.pharmacy.sale.SaleRepository;
import com.larv.pharmacy.user.User;
import com.larv.pharmacy.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Profit reporting. All figures are derived from actual sales in the requested
 * period, net of the refunds attached to those sales, using the cost basis
 * stored on each sale line at sale time (historical data never changes).
 *
 * <p>Date semantics: {@code from}/{@code to} are inclusive dates; internally
 * the half-open interval {@code [from 00:00, to+1 00:00)} is used so the
 * entire "to" day is included.</p>
 */
@Service
public class ProfitReportService {

    /** Sort keys that exist on the SaleItem root of the profitDetails query. */
    private static final Set<String> DETAIL_SORTABLE = Set.of("createdAt", "quantity", "drugName");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ProfitReportService(SaleRepository saleRepository,
                               SaleItemRepository saleItemRepository,
                               UserRepository userRepository,
                               Clock clock) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyProfitResponse daily(LocalDate date) {
        LocalDate target = date == null ? today() : date;
        Totals totals = totals(new DateRange(target, target));
        return new DailyProfitResponse(target, totals.count(), totals.revenue(), totals.cost(), totals.profit());
    }

    @Transactional(readOnly = true)
    public WeeklyProfitResponse weekly(LocalDate date) {
        DateRange range = DateRangeUtil.weekContaining(date == null ? today() : date);
        Totals totals = totals(range);
        return new WeeklyProfitResponse(range.from(), range.to(), totals.count(),
                totals.revenue(), totals.cost(), totals.profit());
    }

    @Transactional(readOnly = true)
    public MonthlyProfitResponse monthly(Integer year, Integer month) {
        LocalDate reference = today();
        int effectiveYear = year == null ? reference.getYear() : year;
        int effectiveMonth = month == null ? reference.getMonthValue() : month;
        DateRange range = DateRangeUtil.monthOf(effectiveYear, effectiveMonth);
        Totals totals = totals(range);
        BigDecimal average = totals.count() == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totals.revenue().divide(BigDecimal.valueOf(totals.count()), 2, RoundingMode.HALF_UP);
        return new MonthlyProfitResponse(effectiveYear, effectiveMonth, range.from(), range.to(),
                totals.count(), totals.revenue(), totals.cost(), totals.profit(), average);
    }

    @Transactional(readOnly = true)
    public ProfitReportResponse custom(String preset, LocalDate from, LocalDate to) {
        DateRange range = DateRangeUtil.resolve(preset, from, to, today(), clock.getZone());
        Totals totals = totals(range);
        return new ProfitReportResponse(range.from(), range.to(), totals.count(),
                totals.revenue(), totals.cost(), totals.profit());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfitDetailResponse> details(LocalDate from, LocalDate to, Long drugId, Long categoryId,
                                                      Long employeeId, PaymentMethod paymentMethod, Long customerId,
                                                      Pageable pageable) {
        DateRange range = DateRangeUtil.resolve(null, from, to, today(), clock.getZone());
        Pageable sanitized = PaginationUtil.sanitize(pageable, DETAIL_SORTABLE, "createdAt", Sort.Direction.DESC);

        Page<ProfitDetailRow> page = saleItemRepository.profitDetails(
                range.startInstant(clock.getZone()),
                range.endExclusiveInstant(clock.getZone()),
                drugId, categoryId, employeeId, paymentMethod, customerId, sanitized);

        Map<Long, String> usernames = usernames(page.getContent().stream()
                .map(ProfitDetailRow::createdBy).toList());

        return PageResponse.from(page, row -> {
            BigDecimal revenue = MoneyUtil.round2(row.revenue());
            BigDecimal cost = MoneyUtil.round2(row.cost());
            return new ProfitDetailResponse(
                    row.saleId(),
                    row.createdAt(),
                    row.drugId(),
                    row.drugName(),
                    row.quantity(),
                    revenue,
                    cost,
                    revenue.subtract(cost),
                    row.paymentMethod(),
                    row.createdBy() == null ? null : usernames.get(row.createdBy()),
                    row.customerId(),
                    row.customerName());
        });
    }

    // ------------------------------------------------------------------

    private Totals totals(DateRange range) {
        ProfitAggregateRow row = saleRepository.aggregateProfit(
                range.startInstant(clock.getZone()),
                range.endExclusiveInstant(clock.getZone()),
                null, null, null);
        // Round revenue/cost first and derive profit from the rounded values so
        // every response reconciles exactly (profit == revenue - cost).
        BigDecimal revenue = MoneyUtil.round2(row.revenue());
        BigDecimal cost = MoneyUtil.round2(row.cost());
        return new Totals(row.salesCount() == null ? 0 : row.salesCount(), revenue, cost,
                revenue.subtract(cost));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Map<Long, String> usernames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private record Totals(long count, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {
    }
}
