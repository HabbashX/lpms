package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.CreateUserRequest;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.UpdateUserPasswordRequest;
import com.lpms.data.dto.UpdateUserRequest;
import com.lpms.data.dto.UpdateUserStatusRequest;
import com.lpms.data.dto.UserResponse;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * {@code /api/v1/users} â€” ADMIN only; the server enforces this with
 * {@code @PreAuthorize}, the client only hides the UI.
 *
 * <p>{@code size} is clamped to 100 server-side and unknown {@code sort} properties are
 * silently ignored, so only these sort fields are offered: {@code id, username, role,
 * enabled, createdAt, lastLoginAt} (default {@code username,asc}).</p>
 */
public interface UserApi {

    @GET("api/v1/users")
    Call<PageResponse<UserResponse>> list(@Nullable @Query("search") String search,
                                          @Nullable @Query("role") String role,
                                          @Nullable @Query("enabled") Boolean enabled,
                                          @Query("page") int page,
                                          @Query("size") int size,
                                          @Nullable @Query("sort") String sort);

    @GET("api/v1/users/{id}")
    Single<UserResponse> get(@Path("id") long id);

    @POST("api/v1/users")
    Single<UserResponse> create(@Body CreateUserRequest request);

    /** 409 {@code SELF_MODIFICATION_FORBIDDEN} / {@code LAST_ADMIN} are possible. */
    @PUT("api/v1/users/{id}")
    Single<UserResponse> update(@Path("id") long id, @Body UpdateUserRequest request);

    /** Disabling a user invalidates their access and refresh tokens. */
    @PATCH("api/v1/users/{id}/status")
    Completable setStatus(@Path("id") long id, @Body UpdateUserStatusRequest request);

    /** Sets a temporary password; the user must change it on next login. */
    @PATCH("api/v1/users/{id}/password")
    Completable resetPassword(@Path("id") long id, @Body UpdateUserPasswordRequest request);
}