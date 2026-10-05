package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code POST /inventory/purchases} body (ADMIN/PHARMACIST) → 201
 * {@code StockBatchResponse}. Creates an immutable batch; historical purchase prices
 * are never overwritten.
 *
 * <p><b>Pricing options — send at most ONE of {@code sellingPrice} and
 * {@code profitPerUnit}.</b> Sending both is rejected with 400
 * {@code INVALID_PRICING}:</p>
 * <ul>
 *   <li>{@code sellingPrice} → sets the drug's default selling price directly.</li>
 *   <li>{@code profitPerUnit} → sets the selling price to the weighted-average cost
 *       of all remaining stock <em>including this purchase</em>, plus the profit.</li>
 *   <li>neither → leaves the drug's selling price unchanged.</li>
 * </ul>
 *
 * <p>Other errors: 400 {@code EXPIRATION_IN_PAST} for a past expiration date,
 * 409 {@code DRUG_INACTIVE} for a deactivated drug.</p>
 */
public final class CreatePurchaseRequest {

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("quantity")
    private final Integer quantity;

    @SerializedName("unitPurchasePrice")
    private final BigDecimal unitPurchasePrice;

    @SerializedName("supplier")
    private final String supplier;

    @SerializedName("batchNumber")
    private final String batchNumber;

    @SerializedName("expirationDate")
    private final LocalDate expirationDate;

    @SerializedName("sellingPrice")
    private final BigDecimal sellingPrice;

    @SerializedName("profitPerUnit")
    private final BigDecimal profitPerUnit;

    private CreatePurchaseRequest(@NonNull Long drugId,
                                  @NonNull Integer quantity,
                                  @NonNull BigDecimal unitPurchasePrice,
                                  @Nullable String supplier,
                                  @Nullable String batchNumber,
                                  @Nullable LocalDate expirationDate,
                                  @Nullable BigDecimal sellingPrice,
                                  @Nullable BigDecimal profitPerUnit) {
        this.drugId = drugId;
        this.quantity = quantity;
        this.unitPurchasePrice = unitPurchasePrice;
        this.supplier = supplier;
        this.batchNumber = batchNumber;
        this.expirationDate = expirationDate;
        this.sellingPrice = sellingPrice;
        this.profitPerUnit = profitPerUnit;
    }

    /** Leave the drug's selling price untouched. */
    @NonNull
    public static CreatePurchaseRequest noPricingChange(@NonNull Long drugId,
                                                        @NonNull Integer quantity,
                                                        @NonNull BigDecimal unitPurchasePrice,
                                                        @Nullable String supplier,
                                                        @Nullable String batchNumber,
                                                        @Nullable LocalDate expirationDate) {
        return new CreatePurchaseRequest(drugId, quantity, unitPurchasePrice, supplier,
                batchNumber, expirationDate, null, null);
    }

    /** Option A: set the drug's default selling price to an absolute value. */
    @NonNull
    public static CreatePurchaseRequest withFixedSellingPrice(@NonNull Long drugId,
                                                              @NonNull Integer quantity,
                                                              @NonNull BigDecimal unitPurchasePrice,
                                                              @Nullable String supplier,
                                                              @Nullable String batchNumber,
                                                              @Nullable LocalDate expirationDate,
                                                              @NonNull BigDecimal sellingPrice) {
        return new CreatePurchaseRequest(drugId, quantity, unitPurchasePrice, supplier,
                batchNumber, expirationDate, sellingPrice, null);
    }

    /** Option B: derive the selling price from weighted-average cost + profit per unit. */
    @NonNull
    public static CreatePurchaseRequest withProfitPerUnit(@NonNull Long drugId,
                                                          @NonNull Integer quantity,
                                                          @NonNull BigDecimal unitPurchasePrice,
                                                          @Nullable String supplier,
                                                          @Nullable String batchNumber,
                                                          @Nullable LocalDate expirationDate,
                                                          @NonNull BigDecimal profitPerUnit) {
        return new CreatePurchaseRequest(drugId, quantity, unitPurchasePrice, supplier,
                batchNumber, expirationDate, null, profitPerUnit);
    }

    @NonNull
    public Long getDrugId() {
        return drugId;
    }

    @NonNull
    public Integer getQuantity() {
        return quantity;
    }

    @NonNull
    public BigDecimal getUnitPurchasePrice() {
        return unitPurchasePrice;
    }

    @Nullable
    public String getSupplier() {
        return supplier;
    }

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    @Nullable
    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    @Nullable
    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    @Nullable
    public BigDecimal getProfitPerUnit() {
        return profitPerUnit;
    }

    /** Guards the "at most one pricing option" rule before the request leaves the app. */
    public boolean hasAtMostOnePricingOption() {
        return (sellingPrice == null) || (profitPerUnit == null);
    }
}