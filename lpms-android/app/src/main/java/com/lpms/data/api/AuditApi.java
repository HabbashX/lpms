package com.lpms.data.api;

import androidx.annotation.Nullable;

import com.lpms.data.dto.AuditLogResponse;
import com.lpms.data.dto.PageResponse;

import java.time.LocalDate;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * {@code GET /api/v1/audit} â€” ADMIN only.
 *
 * <p>Sortable: {@code createdAt, action, id} (default {@code createdAt,desc}).
 * {@code action} takes an {@link com.lpms.data.dto.AuditAction} name; unknown actions
 * from a newer server are rendered with a generic label and are not filterable.</p>
 */
public interface AuditApi {

    @GET("api/v1/audit")
    Call<PageResponse<AuditLogResponse>> list(@Nullable @Query("action") String action,
                                              @Nullable @Query("userId") Long userId,
                                              @Nullable @Query("entityType") String entityType,
                                              @Nullable @Query("from") LocalDate from,
                                              @Nullable @Query("to") LocalDate to,
                                              @Query("page") int page,
                                              @Query("size") int size,
                                              @Nullable @Query("sort") String sort);
}