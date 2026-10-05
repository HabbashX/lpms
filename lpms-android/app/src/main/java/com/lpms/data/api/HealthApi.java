package com.lpms.data.api;

import com.lpms.data.dto.HealthResponse;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;

/**
 * Public health probe. Declared at the server root, outside the {@code /api/v1}
 * prefix, and never carries an Authorization header — see
 * {@link com.lpms.core.network.PublicEndpoints}.
 *
 * <p>Used for the connectivity check on the login screen. The backend may be on a
 * free tier host and cold-start slowly, so the client uses 30 s timeouts and shows a
 * "waking up server…" state while this call is in flight.</p>
 */
public interface HealthApi {

    @GET(".")
    Single<HealthResponse> health();
}