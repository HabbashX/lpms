package com.lpms.data.repo;

import androidx.annotation.NonNull;

import com.lpms.core.network.NetworkCall;
import com.lpms.core.util.AppPreferences;
import com.lpms.data.api.DashboardApi;
import com.lpms.data.dto.DashboardResponse;
import com.lpms.local.LocalBackend;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * {@code GET /dashboard}. Available to every authenticated role.
 *
 * <p>The response carries profit figures for all roles, so consumers must hide those
 * tiles for EMPLOYEE rather than expect the server to omit them.</p>
 *
 * <p>In offline mode the figures are derived from the local database instead. The summary is
 * computed from the same sale rows the list shows, so the dashboard can never disagree with
 * the sales history underneath it.</p>
 */
@Singleton
public final class DashboardRepository {

    private final DashboardApi dashboardApi;
    private final LocalBackend localBackend;
    private final AppPreferences preferences;

    @Inject
    public DashboardRepository(@NonNull DashboardApi dashboardApi,
                              @NonNull LocalBackend localBackend,
                              @NonNull AppPreferences preferences) {
        this.dashboardApi = dashboardApi;
        this.localBackend = localBackend;
        this.preferences = preferences;
    }

    @NonNull
    public Single<DashboardResponse> load() {
        if (preferences.isOffline()) {
            return localBackend.dashboard();
        }
        return dashboardApi.dashboard()
                .onErrorResumeNext(error -> Single.error(
                        new com.lpms.core.error.ApiException(NetworkCall.asApiError(error))))
                .subscribeOn(Schedulers.io());
    }
}