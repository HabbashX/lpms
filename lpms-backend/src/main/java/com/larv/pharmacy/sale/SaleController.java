package com.larv.pharmacy.sale;

import com.larv.pharmacy.common.domain.PaymentMethod;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.sale.dto.CreateRefundRequest;
import com.larv.pharmacy.sale.dto.CreateSaleRequest;
import com.larv.pharmacy.sale.dto.RefundResponse;
import com.larv.pharmacy.sale.dto.SaleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "Sales", description = "Point-of-sale, sale history and refunds")
public class SaleController {

    private final SaleService saleService;
    private final RefundService refundService;

    public SaleController(SaleService saleService, RefundService refundService) {
        this.saleService = saleService;
        this.refundService = refundService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a sale",
            description = "Atomically validates stock, computes weighted-average COGS and profit, "
                    + "decrements inventory and appends customer-ledger entries for credit sales.")
    public SaleResponse create(@Valid @RequestBody CreateSaleRequest request) {
        return saleService.create(request);
    }

    @GetMapping
    @Operation(summary = "List sales",
            description = "Filters: customerId, paymentMethod, status and an inclusive from/to date range.")
    public PageResponse<SaleResponse> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return saleService.list(customerId, paymentMethod, status, from, to, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sale with its items")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @PostMapping("/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Refund a sale (ADMIN/PHARMACIST)",
            description = "Partial or full refund. Restores inventory, reverses revenue/cost/profit, "
                    + "updates customer debt for credit sales and writes an audit record. "
                    + "The original sale is never deleted.")
    public RefundResponse refund(@PathVariable Long id,
                                 @Valid @RequestBody CreateRefundRequest request) {
        return refundService.refund(id, request);
    }
}
