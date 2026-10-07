package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.NetworkCall;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.data.api.UserApi;
import com.lpms.data.dto.CreateUserRequest;
import com.lpms.data.dto.PageResponse;
import com.lpms.data.dto.UpdateUserPasswordRequest;
import com.lpms.data.dto.UpdateUserRequest;
import com.lpms.data.dto.UpdateUserStatusRequest;
import com.lpms.data.dto.UserResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;

/**
 * Staff accounts. ADMIN only, apart from {@code me}, which every role uses.
 *
 * <p>Writes are never retried: creating a user twice would either collide on the username
 * or leave two accounts where one was intended.</p>
 */
@Singleton
public final class UserRepository {

    private final UserApi userApi;

    @Inject
    public UserRepository(@NonNull UserApi userApi) {
        this.userApi = userApi;
    }

    /** Immutable filter for the staff list. */
    public static final class Filter {

        @Nullable
        final String search;
        @Nullable
        final String role;
        @Nullable
        final Boolean enabled;

        public Filter(@Nullable String search, @Nullable String role, @Nullable Boolean enabled) {
            this.search = search;
            this.role = role;
            this.enabled = enabled;
        }

        @NonNull
        public static Filter none() {
            return new Filter(null, null, null);
        }

        @Nullable
        public String getSearch() {
            return search;
        }

        @Nullable
        public String getRole() {
            return role;
        }

        @Nullable
        public Boolean getEnabled() {
            return enabled;
        }

        @NonNull
        public Filter withSearch(@Nullable String value) {
            return new Filter(value, role, enabled);
        }

        @NonNull
        public Filter withRole(@Nullable String value) {
            return new Filter(search, value, enabled);
        }

        @NonNull
        public Filter withEnabled(@Nullable Boolean value) {
            return new Filter(search, role, value);
        }

        public boolean hasAnyFilter() {
            return search != null || role != null || enabled != null;
        }
    }

    private final class UserPagingSource extends PageablePagingSource<UserResponse> {

        private final Filter filter;

        UserPagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<UserResponse>> callForPage(int page, int size) {
            return userApi.list(filter.getSearch(), filter.getRole(), filter.getEnabled(), page,
                    size, "username,asc");
        }
    }

    @NonNull
    public PageablePagingSource<UserResponse> paging(@NonNull Filter filter,
                                                    @NonNull ApiErrorMapper errorMapper) {
        return new UserPagingSource(filter, errorMapper);
    }

    @NonNull
    public Single<UserResponse> get(long id) {
        return userApi.get(id)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Creates a staff account. The server sets {@code mustChangePassword}, so the new user is
     * forced through the change-password screen on first sign-in.
     */
    @NonNull
    public Single<UserResponse> create(@NonNull CreateUserRequest request) {
        return userApi.create(request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Username and role. There is no password field here; see {@link #resetPassword}. */
    @NonNull
    public Single<UserResponse> update(long id, @NonNull UpdateUserRequest request) {
        return userApi.update(id, request)
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Enables or disables an account. A disabled user cannot sign in, and any token they
     * already hold stops working on their next request.
     */
    @NonNull
    public Completable setEnabled(long id, boolean enabled) {
        return userApi.setStatus(id, new UpdateUserStatusRequest(enabled))
                .onErrorResumeNext(error -> Completable.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }

    /** Sets a new password and forces a change at next sign-in. */
    @NonNull
    public Completable resetPassword(long id, @NonNull String password) {
        return userApi.resetPassword(id, new UpdateUserPasswordRequest(password))
                .onErrorResumeNext(error -> Completable.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }
}