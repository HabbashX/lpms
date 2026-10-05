package com.lpms.data.api;

import com.lpms.data.dto.ChangePasswordRequest;
import com.lpms.data.dto.AuthResponse;
import com.lpms.data.dto.LoginRequest;
import com.lpms.data.dto.LogoutRequest;
import com.lpms.data.dto.RefreshTokenRequest;
import com.lpms.data.dto.UserResponse;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * {@code /api/v1/auth}.
 *
 * <p>{@code login} and {@code refresh} are the only authenticated-contract exceptions:
 * no Authorization header is attached (see
 * {@link com.lpms.core.network.PublicEndpoints}).</p>
 *
 * <p>Rotation invariant: {@link #refresh} consumes the refresh token it is given. The
 * response always carries a new access token <em>and</em> a new refresh token; sending
 * a consumed token again makes the server revoke every session of that user. That call
 * is therefore issued only from
 * {@link com.lpms.core.network.TokenAuthenticator}, under a mutex, exactly once per
 * rotation — never from a repository.</p>
 */
public interface AuthApi {

    @POST("api/v1/auth/login")
    Single<AuthResponse> login(@Body LoginRequest request);

    /**
     * Public, rotating, single-use. The response is identical in shape to login but
     * always yields a brand-new token pair.
     */
    @POST("api/v1/auth/refresh")
    Single<AuthResponse> refresh(@Body RefreshTokenRequest request);

    /**
     * 204. Include {@code refreshToken} in the body so the refresh token is revoked as
     * well; local storage is cleared regardless of the outcome.
     */
    @POST("api/v1/auth/logout")
    Completable logout(@Body LogoutRequest request);

    /**
     * Current user. The only endpoint besides change-password/logout that stays
     * reachable while {@code mustChangePassword} is true. Re-read on app resume so a
     * role change, disablement or forced password change takes effect immediately.
     */
    @GET("api/v1/auth/me")
    Single<UserResponse> me();

    /**
     * 204. On success all existing access and refresh tokens are invalidated, so the
     * client discards local tokens and forces a fresh sign-in.
     */
    @POST("api/v1/auth/change-password")
    Completable changePassword(@Body ChangePasswordRequest request);
}