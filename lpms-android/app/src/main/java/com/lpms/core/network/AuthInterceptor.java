package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches {@code Authorization: Bearer <accessToken>} to every request except the
 * public endpoints listed in {@link PublicEndpoints}.
 *
 * <p>Runs on the calling thread (application interceptor) and is deliberately cheap:
 * it does no I/O and never refreshes. Refreshing is the authenticator's job, which
 * only fires when a response actually says the token is no longer valid.</p>
 */
@Singleton
public final class AuthInterceptor implements Interceptor {

    static final String AUTHORIZATION = "Authorization";
    static final String BEARER = "Bearer ";

    private final SessionManager sessionManager;

    @Inject
    public AuthInterceptor(@NonNull SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        if (PublicEndpoints.isPublic(request)) {
            return chain.proceed(request);
        }

        Session session = sessionManager.current();
        if (session == null || session.getAccessToken().isEmpty()) {
            return chain.proceed(request);
        }
        if (request.header(AUTHORIZATION) != null) {
            // Already set (retry after a refresh, or a caller that manages it itself).
            return chain.proceed(request);
        }
        return chain.proceed(request.newBuilder()
                .header(AUTHORIZATION, BEARER + session.getAccessToken())
                .build());
    }
}