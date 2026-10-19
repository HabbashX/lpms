package com.lpms.core.network;

import androidx.annotation.NonNull;

import com.lpms.core.auth.Session;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.error.OfflineModeException;

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

        // Offline mode must never reach the network. The session there is a local marker,
        // not a real token, so a request would be sent with a credential the server will
        // reject - and that 401 would then run the refresh path, fail, and sign the user
        // out of the preview they were in the middle of.
        //
        // Failing here rather than calling proceed() also gives screens that have not been
        // taught about offline mode a clear transport error instead of a server answer
        // they would misread as an authentication problem.
        if (sessionManager.isOffline()) {
            throw new OfflineModeException(request.url().encodedPath());
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