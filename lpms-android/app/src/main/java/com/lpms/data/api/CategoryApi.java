package com.lpms.data.api;

import com.lpms.data.dto.CategoryRequest;
import com.lpms.data.dto.CategoryResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * {@code /api/v1/categories}.
 *
 * <p>{@code GET /categories} returns a plain, <b>non-paginated</b>
 * {@code List<{id, name}>} sorted by name and is available to every role; the client
 * caches it for the drug-form dropdown and the drug-list filter chip, and refreshes the
 * cache after any write.</p>
 *
 * <p>Writes require ADMIN/PHARMACIST. Duplicate names are 409
 * {@code CATEGORY_ALREADY_EXISTS} (case-insensitive); deleting a category still in use
 * by any drug is 409 {@code CATEGORY_IN_USE}.</p>
 */
public interface CategoryApi {

    @GET("api/v1/categories")
    Single<List<CategoryResponse>> list();

    @GET("api/v1/categories/{id}")
    Single<CategoryResponse> get(@Path("id") long id);

    @POST("api/v1/categories")
    Single<CategoryResponse> create(@Body CategoryRequest request);

    @PUT("api/v1/categories/{id}")
    Single<CategoryResponse> rename(@Path("id") long id, @Body CategoryRequest request);

    @DELETE("api/v1/categories/{id}")
    Completable delete(@Path("id") long id);
}