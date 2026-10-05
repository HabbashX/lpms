package com.larv.pharmacy.inventory;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.BusinessRuleException;
import com.larv.pharmacy.common.exception.DrugNotFoundException;
import com.larv.pharmacy.common.exception.InsufficientStockException;
import com.larv.pharmacy.common.exception.InvalidRequestException;
import com.larv.pharmacy.common.util.MoneyUtil;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.drug.Drug;
import com.larv.pharmacy.drug.DrugRepository;
import com.larv.pharmacy.inventory.CostingService.CostBasis;
import com.larv.pharmacy.inventory.StockBatchRow;
import com.larv.pharmacy.inventory.dto.CreatePurchaseRequest;
import com.larv.pharmacy.inventory.dto.DrugPricingResponse;
import com.larv.pharmacy.inventory.dto.DrugValuationResponse;
import com.larv.pharmacy.inventory.dto.ExpiringBatchResponse;
import com.larv.pharmacy.inventory.dto.LowStockDrugResponse;
import com.larv.pharmacy.inventory.dto.StockBatchResponse;
import com.larv.pharmacy.settings.SettingKey;
import com.larv.pharmacy.settings.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inventory operations: stock purchases, batch listings, valuation,
 * low-stock and expiration reports, plus the stock mutation primitives used
 * by sales and refunds.
 *
 * <p>Every stock writer (purchase, sale, refund) first acquires the drug row
 * lock via {@link #lockDrugs}; that lock serializes batch reads/writes for the
 * drug and prevents concurrent oversell.</p>
 */
@Service
public class InventoryService {

    private static final Set<String> BATCH_SORTABLE = Set.of("id", "receivedAt", "expirationDate", "remainingQuantity");
    private static final Set<String> VALUATION_SORTABLE = Set.of("drugId", "drugName", "totalQuantity", "totalInventoryCost");

    private final DrugRepository drugRepository;
    private final StockBatchRepository stockBatchRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    public InventoryService(DrugRepository drugRepository,
                            StockBatchRepository stockBatchRepository,
                            SettingsService settingsService,
                            AuditService auditService,
                            Clock clock) {
        this.drugRepository = drugRepository;
        this.stockBatchRepository = stockBatchRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Purchases
    // ------------------------------------------------------------------

    @Transactional
    public StockBatchResponse recordPurchase(CreatePurchaseRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.expirationDate() != null && request.expirationDate().isBefore(today)) {
            throw new InvalidRequestException("EXPIRATION_IN_PAST",
                    "expirationDate is in the past (" + request.expirationDate() + ")");
        }

        Drug drug = lockDrug(request.drugId());
        if (!drug.isActive()) {
            throw new BusinessRuleException("DRUG_INACTIVE",
                    "Cannot purchase stock for inactive drug: " + drug.getName());
        }

        int quantity = request.quantity();
        BigDecimal price = MoneyUtil.round4(request.unitPurchasePrice());
        StockBatch batch = new StockBatch(
                drug,
                quantity,
                quantity,
                price,
                trimToNull(request.supplier()),
                trimToNull(request.batchNumber()),
                request.expirationDate(),
                clock.instant());
        stockBatchRepository.save(batch);

        drug.setCurrentQuantity(drug.getCurrentQuantity() + quantity);
        applyPricing(drug, request.sellingPrice(), request.profitPerUnit());
        drugRepository.save(drug);

        auditService.record(AuditAction.PURCHASE_STOCK, "StockBatch", batch.getId(),
                "Purchased " + quantity + " x " + drug.getName() + " @ " + price.setScale(2, MoneyUtil.ROUNDING)
                        + (batch.getSupplier() != null ? " from " + batch.getSupplier() : "")
                        + (batch.getBatchNumber() != null ? " (batch " + batch.getBatchNumber() + ")" : ""));

        return StockBatchResponse.from(batch, drug.getName());
    }

    /** Sets the drug's default selling price from an explicit price or from weighted cost + wanted profit. */
    private void applyPricing(Drug drug, BigDecimal sellingPrice, BigDecimal profitPerUnit) {
        if (sellingPrice != null && profitPerUnit != null) {
            throw new InvalidRequestException("INVALID_PRICING",
                    "Provide either sellingPrice or profitPerUnit, not both");
        }
        if (sellingPrice != null) {
            drug.setSellingPrice(MoneyUtil.round2(sellingPrice));
        } else if (profitPerUnit != null) {
            CostBasis basis = costBasis(stockBatchRepository.findRemainingByDrug(drug.getId()));
            drug.setSellingPrice(PricingCalculator.priceForProfit(basis.weightedAverageCost(), profitPerUnit));
        }
    }

    @Transactional(readOnly = true)
    public DrugPricingResponse pricing(Long drugId, BigDecimal profitPerUnit, BigDecimal marginPercent) {
        if (profitPerUnit != null && marginPercent != null) {
            throw new InvalidRequestException("INVALID_PRICING",
                    "Provide either profitPerUnit or marginPercent, not both");
        }
        if (profitPerUnit != null && profitPerUnit.signum() < 0) {
            throw new InvalidRequestException("INVALID_PRICING", "profitPerUnit must not be negative");
        }
        Drug drug = drugRepository.findById(drugId).orElseThrow(() -> new DrugNotFoundException(drugId));
        List<StockBatch> batches = stockBatchRepository.findRemainingByDrug(drugId);
        CostBasis basis = costBasis(batches);
        BigDecimal cost = basis.weightedAverageCost();
        BigDecimal price = drug.getSellingPrice();

        BigDecimal profit = null;
        BigDecimal margin = null;
        BigDecimal markup = null;
        BigDecimal totalProfit = null;
        if (price != null && basis.totalQuantity() > 0) {
            BigDecimal unitProfit = price.subtract(cost);
            profit = MoneyUtil.round2(unitProfit);
            margin = PricingCalculator.marginPercent(cost, price);
            markup = PricingCalculator.markupPercent(cost, price);
            totalProfit = MoneyUtil.round2(unitProfit.multiply(BigDecimal.valueOf(basis.totalQuantity())));
        }
        BigDecimal suggested = null;
        if (basis.totalQuantity() > 0) {
            if (profitPerUnit != null) {
                suggested = PricingCalculator.priceForProfit(cost, profitPerUnit);
            } else if (marginPercent != null) {
                suggested = PricingCalculator.priceForMargin(cost, marginPercent);
            }
        }
        List<DrugPricingResponse.BatchCost> batchCosts = batches.stream()
                .map(b -> new DrugPricingResponse.BatchCost(b.getId(), b.getBatchNumber(),
                        b.getRemainingQuantity(), MoneyUtil.round2(b.getUnitPurchasePrice()), b.getExpirationDate()))
                .toList();
        return new DrugPricingResponse(
                drug.getId(),
                drug.getName(),
                basis.totalQuantity(),
                MoneyUtil.round2(basis.totalInventoryCost()),
                MoneyUtil.round2(cost),
                price == null ? null : MoneyUtil.round2(price),
                profit, margin, markup, totalProfit, suggested, batchCosts);
    }

    // ------------------------------------------------------------------
    // Listings, valuation, low stock, expirations
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<StockBatchResponse> listBatches(Long drugId, String supplier, String search,
                                                        Boolean expired, Integer expiringDays,
                                                        Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable, BATCH_SORTABLE, "receivedAt", Sort.Direction.DESC);
        String supplierPattern = toLike(supplier);
        String searchPattern = toLike(search);
        LocalDate today = LocalDate.now(clock);

        Page<StockBatchRow> page;
        if (Boolean.TRUE.equals(expired)) {
            page = stockBatchRepository.expired(drugId, supplierPattern, searchPattern, today, sanitized);
        } else if (expiringDays != null) {
            page = stockBatchRepository.expiringBetween(drugId, supplierPattern, searchPattern,
                    today, today.plusDays(expiringDays), sanitized);
        } else {
            page = stockBatchRepository.search(drugId, supplierPattern, searchPattern, sanitized);
        }
        return PageResponse.from(page, row -> StockBatchResponse.from(row.batch(), row.drugName()));
    }

    @Transactional(readOnly = true)
    public List<StockBatchResponse> batchesForDrug(Long drugId) {
        Drug drug = drugRepository.findById(drugId).orElseThrow(() -> new DrugNotFoundException(drugId));
        return stockBatchRepository.findAllByDrugWithDrug(drugId).stream()
                .map(batch -> StockBatchResponse.from(batch, drug.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<DrugValuationResponse> valuation(String search, Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable, VALUATION_SORTABLE, "drugName", Sort.Direction.ASC);
        // The aggregate query has a fixed ORDER BY; only page and size apply.
        Pageable paging = PageRequest.of(sanitized.getPageNumber(), sanitized.getPageSize());
        Page<ValuationRow> page = stockBatchRepository.valuationByDrug(toLike(search), paging);
        return PageResponse.from(page, DrugValuationResponse::from);
    }

    @Transactional(readOnly = true)
    public DrugValuationResponse valuationForDrug(Long drugId) {
        Drug drug = drugRepository.findById(drugId).orElseThrow(() -> new DrugNotFoundException(drugId));
        return stockBatchRepository.valuationForDrug(drugId).stream()
                .findFirst()
                .map(DrugValuationResponse::from)
                .orElseGet(() -> DrugValuationResponse.empty(drug.getId(), drug.getName()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LowStockDrugResponse> lowStock(String search, Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), PaginationUtil.MAX_PAGE_SIZE);
        Page<Drug> result = drugRepository.findLowStock(trimToNull(search), PageRequest.of(page, size));
        return PageResponse.from(result, d -> new LowStockDrugResponse(
                d.getId(), d.getName(), d.getCurrentQuantity(), d.getMinimumStockLevel(), d.getUnit()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpiringBatchResponse> expiring(Integer days, Boolean expiredOnly, String search,
                                                        Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        int effectiveDays = days != null ? days : settingsService.getInt(SettingKey.INVENTORY_EXPIRING_SOON_DAYS);
        Pageable sanitized = PaginationUtil.sanitize(pageable, Set.of("expirationDate", "remainingQuantity", "id"),
                "expirationDate", Sort.Direction.ASC);
        String searchPattern = toLike(search);

        Page<StockBatchRow> page = Boolean.TRUE.equals(expiredOnly)
                ? stockBatchRepository.expired(null, null, searchPattern, today, sanitized)
                : stockBatchRepository.expiringBetween(null, null, searchPattern, today,
                        today.plusDays(effectiveDays), sanitized);

        return PageResponse.from(page, row -> {
            StockBatch batch = row.batch();
            LocalDate expiration = batch.getExpirationDate();
            long daysUntil = expiration == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, expiration);
            return new ExpiringBatchResponse(
                    batch.getId(),
                    batch.getDrug().getId(),
                    row.drugName(),
                    batch.getBatchNumber(),
                    expiration,
                    daysUntil,
                    expiration != null && expiration.isBefore(today),
                    batch.getRemainingQuantity(),
                    batch.getUnitPurchasePrice().setScale(2, MoneyUtil.ROUNDING),
                    batch.getSupplier());
        });
    }

    // ------------------------------------------------------------------
    // Stock mutation primitives (used by sales and refunds inside their tx)
    // ------------------------------------------------------------------

    /**
     * Acquires the row lock of every given drug. Drug ids are locked in
     * ascending order to guarantee a consistent lock order between concurrent
     * multi-drug operations (no deadlocks).
     */
    @Transactional
    public Map<Long, Drug> lockDrugs(Collection<Long> drugIds) {
        Map<Long, Drug> locked = new LinkedHashMap<>();
        List<Long> ordered = drugIds.stream().distinct().sorted().toList();
        for (Long id : ordered) {
            Drug drug = drugRepository.findWithLockById(id)
                    .orElseThrow(() -> new DrugNotFoundException(id));
            locked.put(id, drug);
        }
        return locked;
    }

    @Transactional
    public Drug lockDrug(Long drugId) {
        return drugRepository.findWithLockById(drugId)
                .orElseThrow(() -> new DrugNotFoundException(drugId));
    }

    /** Remaining batches in FEFO order. Must be called while holding the drug lock. */
    @Transactional(readOnly = true)
    public List<StockBatch> remainingBatches(Long drugId) {
        return stockBatchRepository.findRemainingByDrug(drugId);
    }

    /** Number of units in the given batches that may legally be sold today. */
    public int sellableQuantity(List<StockBatch> batches, LocalDate today, boolean allowExpired) {
        int total = 0;
        for (StockBatch batch : batches) {
            if (isSellable(batch, today, allowExpired)) {
                total += batch.getRemainingQuantity();
            }
        }
        return total;
    }

    /**
     * Atomically consumes {@code quantity} units first-expiry-first-out.
     * Batch rows are managed by the surrounding transaction, so dirty checking
     * flushes the decrements; the cached drug quantity is updated explicitly.
     *
     * @throws InsufficientStockException when the sellable batches cannot cover the request
     */
    @Transactional
    public void consume(Drug drug, List<StockBatch> batches, int quantity,
                        LocalDate today, boolean allowExpired) {
        int toConsume = quantity;
        for (StockBatch batch : batches) {
            if (toConsume == 0) {
                break;
            }
            if (!isSellable(batch, today, allowExpired)) {
                continue;
            }
            int take = Math.min(batch.getRemainingQuantity(), toConsume);
            if (take <= 0) {
                continue;
            }
            batch.setRemainingQuantity(batch.getRemainingQuantity() - take);
            toConsume -= take;
        }
        if (toConsume > 0) {
            throw new InsufficientStockException(drug.getName(), quantity - toConsume, quantity);
        }
        drug.setCurrentQuantity(drug.getCurrentQuantity() - quantity);
        drugRepository.save(drug);
    }

    /**
     * Adds stock as a new batch (purchase or refund restoration) and updates
     * the cached drug quantity. Callers must hold the drug lock.
     */
    @Transactional
    public StockBatch addBatch(Drug drug, int quantity, BigDecimal unitPurchasePrice,
                               String supplier, String batchNumber, LocalDate expirationDate) {
        StockBatch batch = new StockBatch(drug, quantity, quantity, MoneyUtil.round4(unitPurchasePrice),
                trimToNull(supplier), trimToNull(batchNumber), expirationDate, clock.instant());
        stockBatchRepository.save(batch);
        drug.setCurrentQuantity(drug.getCurrentQuantity() + quantity);
        drugRepository.save(drug);
        return batch;
    }

    /** Weighted-average cost basis over the given remaining batches. */
    public CostBasis costBasis(List<StockBatch> batches) {
        return CostingService.calculate(batches);
    }

    public boolean salesAllowExpiredStock() {
        return settingsService.getBoolean(SettingKey.INVENTORY_ALLOW_EXPIRED_SALES);
    }

    private boolean isSellable(StockBatch batch, LocalDate today, boolean allowExpired) {
        if (batch.getRemainingQuantity() <= 0) {
            return false;
        }
        if (allowExpired) {
            return true;
        }
        return batch.getExpirationDate() == null || !batch.getExpirationDate().isBefore(today);
    }

    private static String toLike(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + PaginationUtil.escapeLike(value.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
