package com.lpms.data.repo;

import androidx.annotation.NonNull;

import com.lpms.core.network.NetworkCall;
import com.lpms.data.api.DashboardApi;
import com.lpms.data.dto.DashboardResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * {@code GET /dashboard}. Available to every authenticated role.
 *
 * <p>The response carries profit figures for all roles, so consumers must hide those
 * tiles for EMPLOYEE rather than expect the server to omit them.</p>
 */
@Singleton
public final class DashboardRepository {

    private final DashboardApi dashboardApi;

    @Inject
    public DashboardRepository(@NonNull DashboardApi dashboardApi) {
        this.dashboardApi = dashboardApi;
    }

    @NonNull
    public Single<DashboardResponse> load() {
        return dashboardApi.dashboard()
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }
}