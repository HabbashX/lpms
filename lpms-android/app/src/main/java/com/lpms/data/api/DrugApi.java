package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.CreateDrugRequest;
import com.lpms.data.dto.DrugResponse;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.UpdateDrugRequest;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * {@code /api/v1/drugs}.
 *
 * <p>{@code search} matches name + genericName. Sortable: {@code id, name,
 * genericName, barcode, currentQuantity, sellingPrice, createdAt, updatedAt} (default
 * {@code name,asc}); unknown sort properties are silently ignored server-side.</p>
 *
 * <p>Writes require ADMIN/PHARMACIST. {@code DELETE} is a soft delete: the drug becomes
 * inactive and keeps its history.</p>
 */
public interface DrugApi {

    @GET("api/v1/drugs")
    Call<PageResponse<DrugResponse>> list(@Nullable @Query("search") String search,
                                          @Nullable @Query("categoryId") Long categoryId,
                                          @Nullable @Query("dosageForm") String dosageForm,
                                          @Nullable @Query("active") Boolean active,
                                          @Nullable @Query("lowStock") Boolean lowStock,
                                          @Nullable @Query("barcode") String barcode,
                                          @Query("page") int page,
                                          @Query("size") int size,
                                          @Nullable @Query("sort") String sort);

    /** 404 {@code NOT_FOUND} for an unknown barcode â€” the POS scanner lookup. */
    @GET("api/v1/drugs/barcode/{barcode}")
    Single<DrugResponse> byBarcode(@Path("barcode") String barcode);

    @GET("api/v1/drugs/{id}")
    Single<DrugResponse> get(@Path("id") long id);

    @POST("api/v1/drugs")
    Single<DrugResponse> create(@Body CreateDrugRequest request);

    /** Also used by the pricing screen's "Apply as selling price". */
    @PUT("api/v1/drugs/{id}")
    Single<DrugResponse> update(@Path("id") long id, @Body UpdateDrugRequest request);

    @DELETE("api/v1/drugs/{id}")
    Completable deactivate(@Path("id") long id);
}