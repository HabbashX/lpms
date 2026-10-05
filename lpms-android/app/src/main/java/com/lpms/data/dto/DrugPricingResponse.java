package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code DrugPricingResponse} — {@code GET /inventory/drugs/{drugId}/pricing}.
 * <b>ADMIN/PHARMACIST only</b>: this is the one endpoint that exposes cost data.
 *
 * <p>Send <b>at most one</b> of {@code profitPerUnit} or {@code marginPercent} as a
 * query parameter; both is 400 {@code INVALID_PRICING}, and a margin outside
 * 0 ≤ m &lt; 100 is 400 {@code INVALID_MARGIN}.</p>
 *
 * <p>{@link #getSuggestedSellingPrice()} is only present when one of those
 * parameters was sent:</p>
 * <ul>
 *   <li>{@code profitPerUnit=50} → {@code weightedAverageCost + 50} (265 → 315)</li>
 *   <li>{@code marginPercent=20} → {@code cost ÷ (1 − 0.20)} = 265 ÷ 0.8 = 331.25,
 *       because margin is a percentage <em>of the selling price</em></li>
 * </ul>
 *
 * <p>{@code profitPerUnit}, {@code marginPercent}, {@code markupPercent},
 * {@code expectedTotalProfit} and {@code suggestedSellingPrice} are null when the drug
 * has no selling price or no stock.</p>
 */
public final class DrugPricingResponse {

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("drugName")
    private final String drugName;

    @SerializedName("totalQuantity")
    private final Long totalQuantity;

    @SerializedName("totalInventoryCost")
    private final BigDecimal totalInventoryCost;

    @SerializedName("weightedAverageCost")
    private final BigDecimal weightedAverageCost;

    @SerializedName("sellingPrice")
    private final BigDecimal sellingPrice;

    @SerializedName("profitPerUnit")
    private final BigDecimal profitPerUnit;

    @SerializedName("marginPercent")
    private final BigDecimal marginPercent;

    @SerializedName("markupPercent")
    private final BigDecimal markupPercent;

    @SerializedName("expectedTotalProfit")
    private final BigDecimal expectedTotalProfit;

    @SerializedName("suggestedSellingPrice")
    private final BigDecimal suggestedSellingPrice;

    @SerializedName("batches")
    private final List<PricingBatchResponse> batches;

    public DrugPricingResponse(@Nullable Long drugId,
                               @Nullable String drugName,
                               @Nullable Long totalQuantity,
                               @Nullable BigDecimal totalInventoryCost,
                               @Nullable BigDecimal weightedAverageCost,
                               @Nullable BigDecimal sellingPrice,
                               @Nullable BigDecimal profitPerUnit,
                               @Nullable BigDecimal marginPercent,
                               @Nullable BigDecimal markupPercent,
                               @Nullable BigDecimal expectedTotalProfit,
                               @Nullable BigDecimal suggestedSellingPrice,
                               @Nullable List<PricingBatchResponse> batches) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.totalQuantity = totalQuantity;
        this.totalInventoryCost = totalInventoryCost;
        this.weightedAverageCost = weightedAverageCost;
        this.sellingPrice = sellingPrice;
        this.profitPerUnit = profitPerUnit;
        this.marginPercent = marginPercent;
        this.markupPercent = markupPercent;
        this.expectedTotalProfit = expectedTotalProfit;
        this.suggestedSellingPrice = suggestedSellingPrice;
        this.batches = batches == null ? new ArrayList<>() : batches;
    }

    @Nullable
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public String getDrugName() {
        return drugName;
    }

    public long getTotalQuantity() {
        return totalQuantity == null ? 0L : totalQuantity;
    }

    @Nullable
    public BigDecimal getTotalInventoryCost() {
        return totalInventoryCost;
    }

    @Nullable
    public BigDecimal getWeightedAverageCost() {
        return weightedAverageCost;
    }

    @Nullable
    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    @Nullable
    public BigDecimal getProfitPerUnit() {
        return profitPerUnit;
    }

    @Nullable
    public BigDecimal getMarginPercent() {
        return marginPercent;
    }

    @Nullable
    public BigDecimal getMarkupPercent() {
        return markupPercent;
    }

    @Nullable
    public BigDecimal getExpectedTotalProfit() {
        return expectedTotalProfit;
    }

    /** Only set when a what-if query parameter was sent. */
    @Nullable
    public BigDecimal getSuggestedSellingPrice() {
        return suggestedSellingPrice;
    }

    @NonNull
    public List<PricingBatchResponse> getBatches() {
        return batches;
    }

    public boolean hasStock() {
        return getTotalQuantity() > 0;
    }

    public boolean hasSellingPrice() {
        return sellingPrice != null && sellingPrice.signum() > 0;
    }

    /**
     * Client-side mirror of the server's margin formula, used to label a typed
     * value before the what-if round trip completes.
     *
     * @param cost       weighted-average cost
     * @param marginPct  margin as a percentage of the selling price, 0 ≤ m &lt; 100
     * @return cost ÷ (1 − m/100), or null when the inputs are unusable
     */
    @Nullable
    public static BigDecimal sellingPriceForMargin(@Nullable BigDecimal cost,
                                                   @Nullable BigDecimal marginPct) {
        if (cost == null || marginPct == null) {
            return null;
        }
        BigDecimal fraction = marginPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.subtract(fraction);
        if (divisor.signum() <= 0) {
            return null;
        }
        return cost.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    /** Client-side mirror of the profit-per-unit formula: cost + profit. */
    @Nullable
    public static BigDecimal sellingPriceForProfit(@Nullable BigDecimal cost,
                                                   @Nullable BigDecimal profitPerUnit) {
        if (cost == null || profitPerUnit == null) {
            return null;
        }
        return cost.add(profitPerUnit);
    }
}