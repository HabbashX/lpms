package com.larv.pharmacy.report;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.report.dto.DailyProfitResponse;
import com.larv.pharmacy.report.dto.MonthlyProfitResponse;
import com.larv.pharmacy.report.dto.ProfitDetailResponse;
import com.larv.pharmacy.report.dto.ProfitReportResponse;
import com.larv.pharmacy.report.dto.WeeklyProfitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Profit reporting (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ProfitReportService profitReportService;

    public ReportController(ProfitReportService profitReportService) {
        this.profitReportService = profitReportService;
    }

    @GetMapping("/profit/daily")
    @Operation(summary = "Daily profit report", description = "Defaults to today when no date is given.")
    public DailyProfitResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return profitReportService.daily(date);
    }

    @GetMapping("/profit/weekly")
    @Operation(summary = "Weekly profit report",
            description = "ISO week Monday..Sunday containing the given date (defaults to today).")
    public WeeklyProfitResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return profitReportService.weekly(date);
    }

    @GetMapping("/profit/monthly")
    @Operation(summary = "Monthly profit report",
            description = "GET /api/v1/reports/profit/monthly?year=2026&month=9 (defaults to the current month).")
    public MonthlyProfitResponse monthly(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month) {
        return profitReportService.monthly(year, month);
    }

    @GetMapping("/profit")
    @Operation(summary = "Profit for a custom date range or preset period",
            description = "Parameters: from/to (inclusive dates) or preset="
                    + "today|yesterday|this_week|last_week|this_month|last_month. "
                    + "Defaults to today when nothing is provided.")
    public ProfitReportResponse custom(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitReportService.custom(preset, from, to);
    }

    @GetMapping("/profit/details")
    @Operation(summary = "Detailed profit report (per sale line)",
            description = "Filters: from, to, drug, category, employee, paymentMethod, customer. Paginated.")
    public PageResponse<ProfitDetailResponse> details(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long drug,
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Long employee,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) Long customer,
            Pageable pageable) {
        return profitReportService.details(from, to, drug, category, employee, paymentMethod, customer, pageable);
    }
}
