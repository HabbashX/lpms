package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

/**
 * One cart line of {@code CreateSaleRequest}.
 *
 * <p>{@code unitSellingPrice} is <b>optional</b>. Omit it (send null) to use the
 * drug's default {@code sellingPrice}. It must be &gt; 0 when sent, and if both the
 * line price and the drug default are missing the server rejects the sale with
 * 400 {@code INVALID_SALE}.</p>
 */
public final class CreateSaleItemRequest {

    @SerializedName("drugId")
    private final Long drugId;

    @SerializedName("quantity")
    private final Integer quantity;

    @SerializedName("unitSellingPrice")
    private final BigDecimal unitSellingPrice;

    public CreateSaleItemRequest(@NonNull Long drugId,
                                 @NonNull Integer quantity,
                                 @Nullable BigDecimal unitSellingPrice) {
        this.drugId = drugId;
        this.quantity = quantity;
        this.unitSellingPrice = unitSellingPrice;
    }

    /** Uses the drug's default selling price. */
    @NonNull
    public static CreateSaleItemRequest withDefaultPrice(@NonNull Long drugId, int quantity) {
        return new CreateSaleItemRequest(drugId, quantity, null);
    }

    /** Overrides the drug's default selling price for this line only. */
    @NonNull
    public static CreateSaleItemRequest withPrice(@NonNull Long drugId,
                                                  int quantity,
                                                  @NonNull BigDecimal unitSellingPrice) {
        return new CreateSaleItemRequest(drugId, quantity, unitSellingPrice);
    }

    @NonNull
    public Long getDrugId() {
        return drugId;
    }

    @NonNull
    public Integer getQuantity() {
        return quantity;
    }

    @Nullable
    public BigDecimal getUnitSellingPrice() {
        return unitSellingPrice;
    }

    public boolean usesDefaultPrice() {
        return unitSellingPrice == null;
    }
}