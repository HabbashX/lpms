package com.larv.pharmacy.inventory;

import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.inventory.dto.CreatePurchaseRequest;
import com.larv.pharmacy.inventory.dto.DrugPricingResponse;
import com.larv.pharmacy.inventory.dto.DrugValuationResponse;
import com.larv.pharmacy.inventory.dto.ExpiringBatchResponse;
import com.larv.pharmacy.inventory.dto.LowStockDrugResponse;
import com.larv.pharmacy.inventory.dto.StockBatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Stock batches, purchases, valuation, low stock and expiration management")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a stock purchase (ADMIN/PHARMACIST)",
            description = "Creates a new immutable batch; historical prices are never overwritten.")
    public StockBatchResponse purchase(@Valid @RequestBody CreatePurchaseRequest request) {
        return inventoryService.recordPurchase(request);
    }

    @GetMapping("/batches")
    @Operation(summary = "List stock batches",
            description = "Filters: drugId, supplier, drug-name search, expired=true, expiringDays=N.")
    public PageResponse<StockBatchResponse> batches(
            @RequestParam(required = false) Long drugId,
            @RequestParam(required = false) String supplier,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) Integer expiringDays,
            Pageable pageable) {
        return inventoryService.listBatches(drugId, supplier, search, expired, expiringDays, pageable);
    }

    @GetMapping("/drugs/{drugId}")
    @Operation(summary = "All batches of one drug (FEFO order)")
    public List<StockBatchResponse> batchesForDrug(@PathVariable Long drugId) {
        return inventoryService.batchesForDrug(drugId);
    }

    @GetMapping("/valuation")
    @Operation(summary = "Weighted-average valuation of every drug in stock")
    public PageResponse<DrugValuationResponse> valuation(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return inventoryService.valuation(search, pageable);
    }

    @GetMapping("/drugs/{drugId}/valuation")
    @Operation(summary = "Weighted-average valuation of one drug",
            description = "totalInventoryCost = sum(remaining x purchase price); "
                    + "weightedAverageCost = totalInventoryCost / totalQuantity.")
    public DrugValuationResponse valuationForDrug(@PathVariable Long drugId) {
        return inventoryService.valuationForDrug(drugId);
    }

    @GetMapping("/drugs/{drugId}/pricing")
    @Operation(summary = "Weighted-average cost, current profit and suggested selling price (ADMIN/PHARMACIST)",
            description = "Average cost is quantity-weighted over all remaining batches. "
                    + "Pass profitPerUnit (cost + profit) or marginPercent (profit as % of price) to get "
                    + "suggestedSellingPrice; passing both is rejected.")
    public DrugPricingResponse pricing(@PathVariable Long drugId,
                                       @RequestParam(required = false) java.math.BigDecimal profitPerUnit,
                                       @RequestParam(required = false) java.math.BigDecimal marginPercent) {
        return inventoryService.pricing(drugId, profitPerUnit, marginPercent);
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Drugs at or below their minimum stock level")
    public PageResponse<LowStockDrugResponse> lowStock(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return inventoryService.lowStock(search, pageable);
    }

    @GetMapping("/expiring")
    @Operation(summary = "Batches expiring soon or already expired",
            description = "days=7|30|90 (default from settings), expired=true returns only expired batches.")
    public PageResponse<ExpiringBatchResponse> expiring(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Boolean expired,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return inventoryService.expiring(days, expired, search, pageable);
    }
}
