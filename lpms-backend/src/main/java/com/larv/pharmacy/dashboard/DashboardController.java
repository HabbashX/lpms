package com.larv.pharmacy.dashboard;

import com.larv.pharmacy.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregated statistics")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Dashboard statistics",
            description = "Today/month revenue, profit and sales; inventory value, low stock and expiring "
                    + "counts; customer count and total debt; top-selling drugs of the current month.")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
