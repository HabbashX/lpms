package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.CreatePurchaseRequest;
import com.lpms.data.dto.DrugPricingResponse;
import com.lpms.data.dto.DrugValuationResponse;
import com.lpms.data.dto.ExpiringBatchResponse;
import com.lpms.data.dto.LowStockDrugResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.StockBatchResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Body;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * {@code /api/v1/inventory}.
 *
 * <p>Stock is FEFO with weighted-average costing, both computed server-side; the app
 * only displays the results.</p>
 *
 * <p>Sortable fields â€” anything else is silently ignored by the server:
 * batches {@code id, receivedAt, expirationDate, remainingQuantity} (default
 * {@code receivedAt,desc}); valuation {@code drugId, drugName, totalQuantity,
 * totalInventoryCost}.</p>
 */
public interface InventoryApi {

    /**
     * Records a purchase and creates an immutable batch (ADMIN/PHARMACIST).
     * Send <b>at most one</b> of {@code sellingPrice} / {@code profitPerUnit} in the
     * body â€” both is 400 {@code INVALID_PRICING}. See the
     * {@link CreatePurchaseRequest} factories.
     */
    @POST("api/v1/inventory/purchases")
    Single<StockBatchResponse> purchase(@Body CreatePurchaseRequest request);

    @GET("api/v1/inventory/batches")
    Call<PageResponse<StockBatchResponse>> batches(@Nullable @Query("drugId") Long drugId,
                                                   @Nullable @Query("supplier") String supplier,
                                                   @Nullable @Query("search") String search,
                                                   @Nullable @Query("expired") Boolean expired,
                                                   @Nullable @Query("expiringDays") Integer expiringDays,
                                                   @Query("page") int page,
                                                   @Query("size") int size,
                                                   @Nullable @Query("sort") String sort);

    /** All batches of one drug in FEFO order; plain list, not paginated. */
    @GET("api/v1/inventory/drugs/{drugId}")
    Single<List<StockBatchResponse>> batchesForDrug(@Path("drugId") long drugId);

    @GET("api/v1/inventory/valuation")
    Call<PageResponse<DrugValuationResponse>> valuation(@Nullable @Query("search") String search,
                                                         @Query("page") int page,
                                                         @Query("size") int size,
                                                         @Nullable @Query("sort") String sort);

    @GET("api/v1/inventory/drugs/{drugId}/valuation")
    Single<DrugValuationResponse> valuationForDrug(@Path("drugId") long drugId);

    /**
     * <b>ADMIN/PHARMACIST only</b> â€” the one endpoint that exposes cost data.
     * Send <b>at most one</b> of {@code profitPerUnit} / {@code marginPercent}; both is
     * 400 {@code INVALID_PRICING} and a margin outside {@code 0 â‰¤ m < 100} is 400
     * {@code INVALID_MARGIN}. The response's {@code suggestedSellingPrice} is only
     * populated when one of them was sent.
     */
    @GET("api/v1/inventory/drugs/{drugId}/pricing")
    Single<DrugPricingResponse> pricing(@Path("drugId") long drugId,
                                        @Nullable @Query("profitPerUnit") String profitPerUnit,
                                        @Nullable @Query("marginPercent") String marginPercent);

    @GET("api/v1/inventory/low-stock")
    Call<PageResponse<LowStockDrugResponse>> lowStock(@Nullable @Query("search") String search,
                                                       @Query("page") int page,
                                                       @Query("size") int size);

    /** {@code days} is 7 / 30 / 90; omitting it uses the server's setting. */
    @GET("api/v1/inventory/expiring")
    Call<PageResponse<ExpiringBatchResponse>> expiring(@Nullable @Query("days") Integer days,
                                                       @Nullable @Query("expired") Boolean expired,
                                                       @Nullable @Query("search") String search,
                                                       @Query("page") int page,
                                                       @Query("size") int size);
}