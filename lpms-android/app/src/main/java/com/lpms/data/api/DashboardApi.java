package com.lpms.data.api;

import com.lpms.data.dto.DashboardResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;

/**
 * {@code GET /api/v1/dashboard} — available to every authenticated role.
 *
 * <p>The response includes profit figures even for EMPLOYEE; the client hides those
 * tiles rather than relying on the server to omit them.</p>
 */
public interface DashboardApi {

    @GET("api/v1/dashboard")
    Single<DashboardResponse> dashboard();
}