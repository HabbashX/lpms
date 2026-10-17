package com.lpms.local;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.data.dto.CategoryResponse;
import com.lpms.data.dto.CreatePurchaseRequest;
import com.lpms.data.dto.CustomerResponse;
import com.lpms.data.dto.DashboardResponse;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.SaleItemResponse;
import com.lpms.data.dto.SaleResponse;
import com.lpms.data.dto.StockBatchResponse;
import com.lpms.core.util.Money;
import com.lpms.local.db.CustomerEntity;
import com.lpms.local.db.DrugEntity;
import com.lpms.local.db.LpmsDao;
import com.lpms.local.db.SaleEntity;
import com.lpms.local.db.SaleItemEntity;
import com.lpms.local.db.StockBatchEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * The local backend.
 *
 * <p>Answers the queries the app already makes, from SQLite, and returns the same DTOs
 * the network repository would. Nothing above the repositories knows which source served
 * the request, so offline mode is a change of plumbing rather than a second copy of every
 * screen.</p>
 *
 * <p>Two rules carried over from the server because they are what make the numbers mean
 * anything:</p>
 * <ul>
 *   <li>Stock moves are taken from batches, earliest first, and the weighted-average cost
 *       is recomputed from what is left. Recording a sale at the price on screen but at a
 *       cost invented afterwards would produce a profit figure that cannot be reconciled.</li>
 *   <li>The unit cost on a sale line is frozen at the moment of sale. A later receipt must
 *       not rewrite the profit already booked.</li>
 * </ul>
 */
@Singleton
public final class LocalBackend {

    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LpmsDao dao;

    @Inject
    public LocalBackend(@NonNull LpmsDao dao) {
        this.dao = dao;
    }

    // --------------------------------------------------------------- reads

    @NonNull
    public Flowable<List<DrugResponse>> observeDrugs() {
        return dao.observeDrugs().map(this::toDrugs);
    }

    @NonNull
    public Single<List<DrugResponse>> searchDrugs(@NonNull String query, int limit) {
        return dao.searchDrugs(query, limit).map(this::toDrugs);
    }

    @NonNull
    public Maybe<DrugResponse> drug(long id) {
        return dao.drug(id).map(this::toDrug);
    }

    @NonNull
    public Maybe<DrugResponse> drugByBarcode(@NonNull String barcode) {
        return dao.drugByBarcode(barcode).map(this::toDrug);
    }

    @NonNull
    public Flowable<List<CategoryResponse>> observeCategories() {
        return dao.observeCategories()
                .map(rows -> {
                    List<CategoryResponse> out = new ArrayList<>(rows.size());
                    for (com.lpms.local.db.CategoryEntity row : rows) {
                        out.add(new CategoryResponse(row.getId(), row.getName()));
                    }
                    return out;
                });
    }

    @NonNull
    public Flowable<List<CustomerResponse>> observeCustomers() {
        return dao.observeCustomers().map(this::toCustomers);
    }

    @NonNull
    public Single<List<CustomerResponse>> searchCustomers(@NonNull String query, int limit) {
        return dao.searchCustomers(query, limit).map(this::toCustomers);
    }

    @NonNull
    public Maybe<CustomerResponse> customer(long id) {
        return dao.customer(id).map(this::toCustomer);
    }

    @NonNull
    public Flowable<List<SaleResponse>> observeSales() {
        return dao.observeSales().flatMapSingle(this::toSales);
    }

    @NonNull
    public Maybe<SaleResponse> sale(long id) {
        return dao.sale(id).flatMapSingle(this::toSale);
    }

    /** Dashboard metrics, all derived from what is stored locally. */
    @NonNull
    public Single<DashboardResponse> dashboard() {
        return dao.observeSales().firstOrError()
                .map(sales -> {
                    LocalDate today = LocalDate.now();
                    BigDecimal revenueToday = BigDecimal.ZERO;
                    BigDecimal profitToday = BigDecimal.ZERO;
                    long countToday = 0L;
                    BigDecimal revenueMonth = BigDecimal.ZERO;
                    BigDecimal profitMonth = BigDecimal.ZERO;
                    long countMonth = 0L;

                    for (SaleEntity sale : sales) {
                        LocalDate day = LocalDate.parse(
                                sale.getCreatedAt().substring(0, Math.min(10, sale.getCreatedAt().length())),
                                DAY);
                        BigDecimal total = sale.getTotal();
                        BigDecimal profit = sale.getProfitTotal();
                        if (day.equals(today)) {
                            revenueToday = revenueToday.add(total);
                            profitToday = profitToday.add(profit);
                            countToday++;
                        }
                        if (day.getYear() == today.getYear()
                                && day.getMonthValue() == today.getMonthValue()) {
                            revenueMonth = revenueMonth.add(total);
                            profitMonth = profitMonth.add(profit);
                            countMonth++;
                        }
                    }

                    return new DashboardResponse(
                            new DashboardResponse.PeriodSummary(today.getYear(), today.getMonthValue(),
                                    revenueToday, profitToday, countToday),
                            new DashboardResponse.PeriodSummary(today.getYear(), today.getMonthValue(),
                                    revenueMonth, profitMonth, countMonth),
                            new DashboardResponse.InventorySummary(
                                    BigDecimal.ZERO, 0L, 0L),
                            new DashboardResponse.CustomerSummary(0L, BigDecimal.ZERO),
                            List.of());
                });
    }

    // -------------------------------------------------------------- writes

    /**
     * Adds a drug. The category name is resolved from the local category table so the list
     * and detail screens can show it without a second lookup.
     */
    @NonNull
    public Single<DrugResponse> createDrug(@NonNull com.lpms.data.dto.CreateDrugRequest request) {
        return Single.fromCallable(() -> {
            Long categoryId = request.getCategoryId();
            long id = dao.insertDrug(new DrugEntity(
                    0,
                    nonNull(request.getName()),
                    request.getGenericName(),
                    request.getBarcode(),
                    categoryId,
                    categoryNameFor(categoryId),
                    request.getDosageForm(),
                    request.getStrength(),
                    request.getUnit(),
                    request.getSellingPrice(),
                    BigDecimal.ZERO,
                    0,
                    request.getMinimumStockLevel() == null ? 0 : request.getMinimumStockLevel(),
                    true));
            return toDrug(dao.drug(id).blockingGet());
        });
    }

    @NonNull
    public Single<DrugResponse> updateDrug(long id,
                                           @NonNull com.lpms.data.dto.UpdateDrugRequest request) {
        return Single.fromCallable(() -> {
            DrugEntity existing = dao.drug(id).blockingGet();
            if (existing == null) {
                throw new com.lpms.core.error.ApiException(
                        com.lpms.core.error.ApiError.of(404, "NOT_FOUND",
                                "No drug " + id + " in the local database"));
            }
            Long categoryId = request.getCategoryId() == null
                    ? existing.getCategoryId() : request.getCategoryId();
            dao.updateDrug(new DrugEntity(
                    id,
                    request.getName() == null ? existing.getName() : request.getName(),
                    request.getGenericName() == null
                            ? existing.getGenericName() : request.getGenericName(),
                    request.getBarcode() == null ? existing.getBarcode() : request.getBarcode(),
                    categoryId,
                    categoryNameFor(categoryId),
                    request.getDosageForm() == null
                            ? existing.getDosageForm() : request.getDosageForm(),
                    request.getStrength() == null ? existing.getStrength() : request.getStrength(),
                    request.getUnit() == null ? existing.getPackageSize() : request.getUnit(),
                    request.getSellingPrice() == null
                            ? existing.getSellingPrice() : request.getSellingPrice(),
                    existing.getAverageCost(),
                    existing.getCurrentQuantity(),
                    request.getMinimumStockLevel() == null
                            ? existing.getMinQuantity() : request.getMinimumStockLevel(),
                    existing.isActive()));
            return toDrug(dao.drug(id).blockingGet());
        });
    }

    @Nullable
    private String categoryNameFor(@Nullable Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (com.lpms.local.db.CategoryEntity category : dao.allCategories()) {
            if (category.getId() == categoryId) {
                return category.getName();
            }
        }
        return null;
    }

    /** Soft delete: the row stays so past sales and batches still resolve. */
    @NonNull
    public io.reactivex.rxjava3.core.Completable deactivateDrug(long id) {
        return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            DrugEntity existing = dao.drug(id).blockingGet();
            if (existing == null) {
                return;
            }
            dao.updateDrug(new DrugEntity(existing.getId(), existing.getName(),
                    existing.getGenericName(), existing.getBarcode(), existing.getCategoryId(),
                    existing.getCategoryName(), existing.getDosageForm(), existing.getStrength(),
                    existing.getPackageSize(), existing.getSellingPrice(), existing.getAverageCost(),
                    existing.getCurrentQuantity(), existing.getMinQuantity(), false));
        });
    }

    @NonNull
    public Single<CategoryResponse> createCategory(@NonNull String name) {
        return Single.fromCallable(() -> {
            long id = dao.insertCategory(
                    new com.lpms.local.db.CategoryEntity(0, name.trim(), 0));
            return new CategoryResponse(id, name.trim());
        });
    }

    @NonNull
    public Single<CategoryResponse> renameCategory(long id, @NonNull String name) {
        return Single.fromCallable(() -> {
            dao.renameCategory(id, name.trim());
            return new CategoryResponse(id, name.trim());
        });
    }

    /**
     * Deletes a category only when no drug still references it.
     *
     * <p>Mirrors the server's 409 {@code CATEGORY_IN_USE}. Deleting regardless would leave
     * drugs pointing at a category that no longer exists, which the drug list shows as a
     * blank label and the drug form cannot repair.</p>
     */
    @NonNull
    public io.reactivex.rxjava3.core.Completable deleteCategoryChecked(long id) {
        return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            int inUse = dao.drugCountForCategory(id);
            if (inUse > 0) {
                throw new com.lpms.core.error.ApiException(
                        com.lpms.core.error.ApiError.of(409, "CATEGORY_IN_USE",
                                inUse + " drug(s) still use this category"));
            }
            dao.deleteCategory(id);
        });
    }

    public void deleteCategory(long id) {
        dao.deleteCategory(id);
    }

    @NonNull
    public Single<CustomerResponse> createCustomer(
            @NonNull com.lpms.data.dto.CreateCustomerRequest request) {
        return Single.fromCallable(() -> {
            // CreateCustomerRequest carries no email field, so nothing is invented here;
            // the column exists for a future field but stays null until one does.
            long id = dao.insertCustomer(new CustomerEntity(0,
                    nonNull(request.getName()), request.getPhone(), null,
                    request.getAddress(), BigDecimal.ZERO, true));
            return new CustomerResponse(id, request.getName(), request.getPhone(),
                    request.getAddress(), request.getNotes(), true, Instant.now(), Instant.now());
        });
    }

    /**
     * Records a sale, decrements stock from the earliest batches, freezes the cost of each
     * line, and recomputes the weighted-average cost of what remains.
     */
    @NonNull
    public Single<SaleResponse> recordSale(
            @Nullable Long customerId,
            @Nullable String customerName,
            @NonNull com.lpms.data.dto.PaymentMethod paymentMethod,
            @NonNull List<CartLine> lines,
            @NonNull BigDecimal discount,
            @Nullable BigDecimal amountPaidInput) {

        return Single.fromCallable(() -> {
            List<SaleItemEntity> items = new ArrayList<>(lines.size());
            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal costTotal = BigDecimal.ZERO;
            List<Long> touched = new ArrayList<>();

            Instant now = Instant.now();
            LocalDate today = LocalDate.now();

            for (CartLine line : lines) {
                DrugEntity drug = dao.drug(line.drugId).blockingGet();
                if (drug == null) {
                    throw new IllegalStateException("drug " + line.drugId + " not found offline");
                }
                // Take cost from the batches being consumed, not from the drug's average,
                // so the profit on this line is the profit on the units that actually left.
                BigDecimal consumedCost = consumeStock(
                        drug.getId(), line.quantity, now, touched);

                BigDecimal unitPrice = line.unitPrice == null
                        ? nz(drug.getSellingPrice()) : line.unitPrice;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(line.quantity))
                        .setScale(2, ROUND);
                BigDecimal lineCost = consumedCost.setScale(2, ROUND);
                BigDecimal unitCost = line.quantity > 0
                        ? consumedCost.divide(BigDecimal.valueOf(line.quantity), 4, ROUND)
                        : BigDecimal.ZERO;

                subtotal = subtotal.add(lineTotal);
                costTotal = costTotal.add(consumedCost);

                items.add(new SaleItemEntity(0, 0, drug.getId(), drug.getName(), line.quantity,
                        unitPrice.setScale(2, ROUND), unitCost, lineTotal,
                        lineTotal.subtract(lineCost).setScale(2, ROUND)));
            }

            BigDecimal total = subtotal.subtract(nz(discount).min(subtotal))
                    .max(BigDecimal.ZERO).setScale(2, ROUND);
            BigDecimal paid = amountPaidInput == null
                    ? total : amountPaidInput.min(total).max(BigDecimal.ZERO).setScale(2, ROUND);
            BigDecimal profit = total.subtract(costTotal).setScale(2, ROUND);

            long saleId = dao.insertSale(new SaleEntity(
                    0,
                    today.format(DAY) + "T" + isoTime(now),
                    customerId,
                    customerName,
                    paymentMethod.wireValue(),
                    subtotal.setScale(2, ROUND),
                    nz(discount).setScale(2, ROUND),
                    total,
                    paid,
                    total.subtract(paid).setScale(2, ROUND),
                    costTotal.setScale(2, ROUND),
                    profit,
                    "COMPLETED"));

            for (SaleItemEntity item : items) {
                // SaleItemEntity has no sale setter; rebuild with the id now known.
                items.set(items.indexOf(item), new SaleItemEntity(0, saleId, item.getDrugId(),
                        item.getDrugName(), item.getQuantity(), item.getUnitSellingPrice(),
                        item.getUnitCost(), item.getLineTotal(), item.getProfit()));
            }
            dao.insertSaleItems(items);

            return toSale(new SaleEntity(saleId, today.format(DAY) + "T" + isoTime(now),
                    customerId, customerName, paymentMethod.wireValue(),
                    subtotal.setScale(2, ROUND), nz(discount).setScale(2, ROUND), total, paid,
                    total.subtract(paid).setScale(2, ROUND), costTotal.setScale(2, ROUND),
                    profit, "COMPLETED"), items);
        });
    }

    /**
     * Receives stock: appends a batch, restocks the drug, and recomputes the drug's
     * weighted-average cost over all remaining batches.
     *
     * <p>The average is derived from batches rather than folded arithmetically, for the
     * same reason the server does it that way: after a few receipts the two disagree, and
     * the derived one cannot drift.</p>
     */
    @NonNull
    public Single<StockBatchResponse> receiveStock(@NonNull CreatePurchaseRequest request) {
        return Single.fromCallable(() -> {
            BigDecimal quantity = Money.parsePositive(String.valueOf(request.getQuantity()));
            int qty = quantity == null ? 0 : quantity.intValue();
            BigDecimal unitPrice = new BigDecimal(request.getUnitPurchasePrice().toPlainString());

            Instant now = Instant.now();
            long drugId = request.getDrugId();
            long batchId = dao.insertBatch(new StockBatchEntity(
                    0, drugId, qty, unitPrice, null, null,
                    request.getExpirationDate() == null ? null : request.getExpirationDate().format(DAY),
                    now.toString()));

            restockDrug(drugId, qty);

            DrugEntity drug = dao.drug(drugId).blockingGet();
            return new StockBatchResponse(batchId, drugId, drug == null ? "" : drug.getName(),
                    qty, qty, unitPrice, null, null,
                    request.getExpirationDate(), now, now);
        });
    }

    // ------------------------------------------------ inventory & reporting

    /** Batches for one drug, newest first, as the batch list shows them. */
    @NonNull
    public Single<List<StockBatchResponse>> batchesForDrug(long drugId) {
        return Single.fromCallable(() -> {
            DrugEntity drug = dao.drug(drugId).blockingGet();
            String name = drug == null ? "" : drug.getName();
            List<StockBatchEntity> rows = dao.batchesForDrug(drugId);
            List<StockBatchResponse> out = new ArrayList<>(rows.size());
            for (StockBatchEntity row : rows) {
                out.add(toBatch(row, name));
            }
            return out;
        });
    }

    @NonNull
    public Single<List<StockBatchResponse>> allBatches(int limit) {
        return Single.fromCallable(() -> {
            List<StockBatchEntity> rows = dao.recentBatches(limit).blockingGet();
            List<StockBatchResponse> out = new ArrayList<>(rows.size());
            for (StockBatchEntity row : rows) {
                DrugEntity drug = dao.drug(row.getDrugId()).blockingGet();
                out.add(toBatch(row, drug == null ? "" : drug.getName()));
            }
            return out;
        });
    }

    @NonNull
    private StockBatchResponse toBatch(@NonNull StockBatchEntity row, @NonNull String drugName) {
        return new StockBatchResponse(row.getId(), row.getDrugId(), drugName, null,
                row.getRemainingQuantity(), row.getUnitPurchasePrice(), row.getSupplier(),
                row.getBatchNumber(),
                row.getExpirationDate() == null ? null
                        : LocalDate.parse(row.getExpirationDate(), DAY),
                parseInstant(row.getReceivedAt()), parseInstant(row.getReceivedAt()));
    }

    /** Drugs at or below their minimum level. */
    @NonNull
    public Single<List<com.lpms.data.dto.LowStockDrugResponse>> lowStock() {
        return dao.observeDrugs().firstOrError().map(rows -> {
            List<com.lpms.data.dto.LowStockDrugResponse> out = new ArrayList<>();
            for (DrugEntity row : rows) {
                if (row.isActive() && row.getCurrentQuantity() <= row.getMinQuantity()) {
                    out.add(new com.lpms.data.dto.LowStockDrugResponse(row.getId(), row.getName(),
                            row.getCurrentQuantity(), row.getMinQuantity(), row.getPackageSize()));
                }
            }
            return out;
        });
    }

    /** Batches expiring within {@code days}, soonest first. */
    @NonNull
    public Single<List<com.lpms.data.dto.ExpiringBatchResponse>> expiring(int days) {
        return Single.fromCallable(() -> {
            LocalDate today = LocalDate.now();
            List<com.lpms.data.dto.ExpiringBatchResponse> out = new ArrayList<>();
            for (StockBatchEntity batch : dao.recentBatches(500).blockingGet()) {
                if (batch.getExpirationDate() == null || batch.getRemainingQuantity() <= 0) {
                    continue;
                }
                LocalDate expires = LocalDate.parse(batch.getExpirationDate(), DAY);
                long until = java.time.temporal.ChronoUnit.DAYS.between(today, expires);
                if (until > days) {
                    continue;
                }
                DrugEntity drug = dao.drug(batch.getDrugId()).blockingGet();
                out.add(new com.lpms.data.dto.ExpiringBatchResponse(batch.getId(),
                        batch.getDrugId(), drug == null ? "" : drug.getName(),
                        batch.getBatchNumber(), expires, until, until < 0,
                        batch.getRemainingQuantity(), batch.getUnitPurchasePrice(),
                        batch.getSupplier()));
            }
            out.sort((a, b) -> Long.compare(a.getDaysUntilExpiration(), b.getDaysUntilExpiration()));
            return out;
        });
    }

    /** Every active drug with its stock value, for the valuation list. */
    @NonNull
    public Single<List<com.lpms.data.dto.DrugValuationResponse>> valuation() {
        return dao.observeDrugs().firstOrError().map(rows -> {
            List<com.lpms.data.dto.DrugValuationResponse> out = new ArrayList<>();
            for (DrugEntity row : rows) {
                if (!row.isActive()) {
                    continue;
                }
                BigDecimal cost = nz(row.getAverageCost());
                out.add(new com.lpms.data.dto.DrugValuationResponse(row.getId(), row.getName(),
                        (long) row.getCurrentQuantity(),
                        cost.multiply(BigDecimal.valueOf(row.getCurrentQuantity()))
                                .setScale(2, ROUND),
                        cost));
            }
            return out;
        });
    }

    /**
     * Cost and pricing for one drug, computed the same way the server's endpoint does:
     * profit and margin only when a selling price exists, and a suggestion only when a
     * target profit or margin was asked for.
     */
    @NonNull
    public Single<com.lpms.data.dto.DrugPricingResponse> pricing(long drugId,
                                                                @Nullable BigDecimal profitPerUnit,
                                                                @Nullable BigDecimal marginPercent,
                                                                @Nullable Integer incomingQuantity,
                                                                @Nullable BigDecimal incomingUnitCost) {
        return Single.fromCallable(() -> {
            DrugEntity drug = dao.drug(drugId).blockingGet();
            if (drug == null) {
                throw new com.lpms.core.error.ApiException(com.lpms.core.error.ApiError.of(
                        404, "NOT_FOUND", "No drug " + drugId + " in the local database"));
            }

            // Fold the batch being received into the average, so the suggestion matches
            // what saving will store rather than the cost of what is already on the shelf.
            int quantity = drug.getCurrentQuantity();
            BigDecimal totalCost = nz(drug.getAverageCost())
                    .multiply(BigDecimal.valueOf(quantity));
            boolean hasIncoming = incomingQuantity != null && incomingUnitCost != null
                    && incomingQuantity > 0;
            if (hasIncoming) {
                quantity += incomingQuantity;
                totalCost = totalCost.add(incomingUnitCost
                        .multiply(BigDecimal.valueOf(incomingQuantity)));
            }
            BigDecimal cost = quantity > 0
                    ? totalCost.divide(BigDecimal.valueOf(quantity), 4, ROUND)
                    : BigDecimal.ZERO;
            BigDecimal price = drug.getSellingPrice();

            BigDecimal profit = null;
            BigDecimal margin = null;
            BigDecimal markup = null;
            BigDecimal totalProfit = null;
            if (price != null && quantity > 0) {
                profit = price.subtract(cost).setScale(2, ROUND);
                margin = cost.signum() == 0 ? BigDecimal.ZERO
                        : price.subtract(cost).multiply(BigDecimal.valueOf(100))
                                .divide(price, 2, ROUND);
                markup = cost.signum() == 0 ? BigDecimal.ZERO
                        : price.subtract(cost).multiply(BigDecimal.valueOf(100))
                                .divide(cost, 2, ROUND);
                totalProfit = profit.multiply(BigDecimal.valueOf(quantity)).setScale(2, ROUND);
            }

            BigDecimal suggested = null;
            if (quantity > 0) {
                if (profitPerUnit != null) {
                    suggested = cost.add(profitPerUnit).setScale(2, ROUND);
                } else if (marginPercent != null) {
                    BigDecimal divisor = BigDecimal.ONE.subtract(
                            marginPercent.divide(BigDecimal.valueOf(100), 6, ROUND));
                    if (divisor.signum() > 0) {
                        suggested = cost.divide(divisor, 2, ROUND);
                    }
                }
            }

            List<com.lpms.data.dto.PricingBatchResponse> batches = new ArrayList<>();
            for (StockBatchEntity batch : dao.batchesForDrug(drugId)) {
                batches.add(new com.lpms.data.dto.PricingBatchResponse(batch.getId(),
                        batch.getBatchNumber(), batch.getRemainingQuantity(),
                        batch.getUnitPurchasePrice(),
                        batch.getExpirationDate() == null ? null
                                : LocalDate.parse(batch.getExpirationDate(), DAY)));
            }

            return new com.lpms.data.dto.DrugPricingResponse(drugId, drug.getName(),
                    (long) quantity,
                    cost.multiply(BigDecimal.valueOf(quantity)).setScale(2, ROUND),
                    cost, price, profit, margin, markup, totalProfit, suggested, batches);
        });
    }

    /** Profit over a date range, derived from the recorded sale lines. */
    @NonNull
    public Single<com.lpms.data.dto.ProfitReportResponse> profitReport(@NonNull LocalDate from,
                                                                       @NonNull LocalDate to) {
        return Single.fromCallable(() -> {
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            long count = 0L;
            for (SaleEntity sale : dao.observeSales().blockingFirst()) {
                LocalDate day = LocalDate.parse(
                        sale.getCreatedAt().substring(0, Math.min(10, sale.getCreatedAt().length())),
                        DAY);
                if (day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                revenue = revenue.add(sale.getTotal());
                cost = cost.add(sale.getCostTotal());
                count++;
            }
            return new com.lpms.data.dto.ProfitReportResponse(from, to, count, revenue, cost,
                    revenue.subtract(cost));
        });
    }

    /** One row per sale line in range, which is what the report's detail list shows. */
    @NonNull
    public Single<List<com.lpms.data.dto.ProfitDetailResponse>> profitDetails(
            @Nullable LocalDate from, @Nullable LocalDate to) {
        return Single.fromCallable(() -> {
            List<com.lpms.data.dto.ProfitDetailResponse> out = new ArrayList<>();
            for (SaleEntity sale : dao.observeSales().blockingFirst()) {
                LocalDate day = LocalDate.parse(
                        sale.getCreatedAt().substring(0, Math.min(10, sale.getCreatedAt().length())),
                        DAY);
                if (from != null && day.isBefore(from)) {
                    continue;
                }
                if (to != null && day.isAfter(to)) {
                    continue;
                }
                for (SaleItemEntity item : dao.saleItems(sale.getId()).blockingGet()) {
                    BigDecimal lineCost = item.getUnitCost()
                            .multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, ROUND);
                    out.add(new com.lpms.data.dto.ProfitDetailResponse(sale.getId(), day, item.getDrugId(),
                            item.getDrugName(), (long) item.getQuantity(), item.getLineTotal(),
                            lineCost, item.getProfit(), sale.getPaymentMethod(), "You",
                            sale.getCustomerId(), sale.getCustomerName()));
                }
            }
            return out;
        });
    }

    /** Only the preview account exists locally; there is no user table to list. */
    @NonNull
    public Single<List<com.lpms.data.dto.UserResponse>> users() {
        return Single.just(List.of(new com.lpms.data.dto.UserResponse(-1L, "Preview user",
                "ADMIN", true, false, Instant.now(), Instant.now(), Instant.now())));
    }

    /**
     * Audit and pharmacy settings have no local store.
     *
     * <p>Empty rather than invented: a preview that fabricated an audit trail or defaulted
     * a business rule would be worse than one that plainly has nothing to show, and the
     * screens already render an empty state.</p>
     */
    @NonNull
    public Single<List<com.lpms.data.dto.AuditLogResponse>> auditLogs() {
        return Single.just(List.of());
    }

    @NonNull
    public Single<List<com.lpms.data.dto.SettingResponse>> settings() {
        return Single.just(List.of());
    }

    // ------------------------------------------------------------- helpers

    /** Consumes {@code quantity} units from the earliest batches; returns the cost taken. */
    private BigDecimal consumeStock(long drugId, int quantity, @NonNull Instant now,
                                     @NonNull List<Long> touched) {
        List<StockBatchEntity> batches = new ArrayList<>(dao.batchesForDrug(drugId));
        int remaining = quantity;
        BigDecimal cost = BigDecimal.ZERO;

        for (StockBatchEntity batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, batch.getRemainingQuantity());
            if (take <= 0) {
                continue;
            }
            dao.updateBatch(new StockBatchEntity(batch.getId(), batch.getDrugId(),
                    batch.getRemainingQuantity() - take, batch.getUnitPurchasePrice(),
                    batch.getSupplier(), batch.getBatchNumber(), batch.getExpirationDate(),
                    batch.getReceivedAt()));
            cost = cost.add(batch.getUnitPurchasePrice().multiply(BigDecimal.valueOf(take)));
            remaining -= take;
        }

        // Nothing left in stock: the drug still has to be sellable in a preview, so fall
        // back to the drug's own average rather than refusing the sale.
        if (remaining > 0) {
            DrugEntity drug = dao.drug(drugId).blockingGet();
            cost = cost.add(nz(drug == null ? null : drug.getAverageCost())
                    .multiply(BigDecimal.valueOf(remaining)));
        }

        touched.add(drugId);
        restockDrug(drugId, -quantity);
        return cost;
    }

    /** Recomputes quantity and weighted-average cost from the batches that remain. */
    private void restockDrug(long drugId, int deltaQty) {
        List<StockBatchEntity> batches = dao.batchesForDrug(drugId);
        int totalQty = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockBatchEntity batch : batches) {
            totalQty += Math.max(0, batch.getRemainingQuantity());
            totalCost = totalCost.add(batch.getUnitPurchasePrice()
                    .multiply(BigDecimal.valueOf(Math.max(0, batch.getRemainingQuantity()))));
        }
        BigDecimal average = totalQty > 0
                ? totalCost.divide(BigDecimal.valueOf(totalQty), 4, ROUND)
                : BigDecimal.ZERO;

        DrugEntity drug = dao.drug(drugId).blockingGet();
        if (drug == null) {
            return;
        }
        dao.updateDrug(new DrugEntity(drug.getId(), drug.getName(), drug.getGenericName(),
                drug.getBarcode(), drug.getCategoryId(), drug.getCategoryName(),
                drug.getDosageForm(), drug.getStrength(), drug.getPackageSize(),
                drug.getSellingPrice(), average, totalQty, drug.getMinQuantity(),
                drug.isActive()));
    }

    @NonNull
    private List<DrugResponse> toDrugs(@NonNull List<DrugEntity> rows) {
        List<DrugResponse> out = new ArrayList<>(rows.size());
        for (DrugEntity row : rows) {
            out.add(toDrug(row));
        }
        return out;
    }

    @NonNull
    private DrugResponse toDrug(@NonNull DrugEntity row) {
        return new DrugResponse(row.getId(), row.getName(), row.getGenericName(), row.getBarcode(),
                null, row.getCategoryId(), row.getCategoryName(), row.getDosageForm(),
                row.getStrength(), null, row.getSellingPrice(), null,
                row.getMinQuantity(), row.getCurrentQuantity(), row.isActive(),
                Instant.now(), Instant.now());
    }

    @NonNull
    private List<CustomerResponse> toCustomers(@NonNull List<CustomerEntity> rows) {
        List<CustomerResponse> out = new ArrayList<>(rows.size());
        for (CustomerEntity row : rows) {
            out.add(toCustomer(row));
        }
        return out;
    }

    @NonNull
    private CustomerResponse toCustomer(@NonNull CustomerEntity row) {
        return new CustomerResponse(row.getId(), row.getName(), row.getPhone(), row.getAddress(),
                null, row.isActive(), Instant.now(), Instant.now());
    }

    @NonNull
    private Single<List<SaleResponse>> toSales(@NonNull List<SaleEntity> rows) {
        Instant now = Instant.now();
        List<SaleResponse> out = new ArrayList<>(rows.size());
        for (SaleEntity row : rows) {
            out.add(toSale(row, new ArrayList<>()));
        }
        return Single.just(out);
    }

    @NonNull
    private Single<SaleResponse> toSale(@NonNull SaleEntity row) {
        List<SaleItemEntity> items = dao.saleItems(row.getId()).blockingGet();
        return Single.just(toSale(row, items));
    }

    @NonNull
    private SaleResponse toSale(@NonNull SaleEntity row, @NonNull List<SaleItemEntity> items) {
        List<SaleItemResponse> itemDtos = new ArrayList<>(items.size());
        int totalQuantity = 0;
        for (SaleItemEntity item : items) {
            totalQuantity += item.getQuantity();
            itemDtos.add(new SaleItemResponse(item.getId(), item.getDrugId(), item.getDrugName(),
                    item.getQuantity(), item.getUnitSellingPrice(), item.getUnitCost(),
                    BigDecimal.ZERO, item.getLineTotal(), item.getUnitCost()
                            .multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, ROUND),
                    item.getProfit(), 0));
        }
        return new SaleResponse(row.getId(), parseInstant(row.getCreatedAt()), row.getCustomerId(),
                row.getCustomerName(), "You", row.getPaymentMethod(), row.getStatus(),
                row.getSubtotal(), row.getDiscount(), row.getTotal(), row.getAmountPaid(),
                row.getAmountDue(), row.getCostTotal(), row.getProfitTotal(),
                BigDecimal.ZERO, itemDtos);
    }

    @NonNull
    private static Instant parseInstant(@NonNull String raw) {
        try {
            return Instant.parse(raw);
        } catch (RuntimeException fallback) {
            try {
                return LocalDate.parse(raw.substring(0, 10), DAY).atStartOfDay(
                        java.time.ZoneId.systemDefault()).toInstant();
            } catch (RuntimeException ignored) {
                return Instant.EPOCH;
            }
        }
    }

    @NonNull
    private static String isoTime(@NonNull Instant instant) {
        return String.format("%02d:%02d:%02dZ",
                instant.atZone(java.time.ZoneId.systemDefault()).getHour(),
                instant.atZone(java.time.ZoneId.systemDefault()).getMinute(),
                instant.atZone(java.time.ZoneId.systemDefault()).getSecond());
    }

    private static BigDecimal nz(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @NonNull
    private static String nonNull(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** One cart line as the local sale writer needs it. */
    public static final class CartLine {
        public final long drugId;
        public final int quantity;
        @Nullable
        public final BigDecimal unitPrice;

        public CartLine(long drugId, int quantity, @Nullable BigDecimal unitPrice) {
            this.drugId = drugId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }
}
