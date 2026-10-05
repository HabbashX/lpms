package com.lpms.core.di;

import com.lpms.data.api.AuditApi;
import com.lpms.data.api.AuthApi;
import com.lpms.data.api.CategoryApi;
import com.lpms.data.api.CustomerApi;
import com.lpms.data.api.DashboardApi;
import com.lpms.data.api.DrugApi;
import com.lpms.data.api.HealthApi;
import com.lpms.data.api.InventoryApi;
import com.lpms.data.api.ReportApi;
import com.lpms.data.api.SalesApi;
import com.lpms.data.api.SettingsApi;
import com.lpms.data.api.UserApi;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import retrofit2.Retrofit;

/**
 * Exposes each feature's Retrofit interface from the single shared
 * {@link Retrofit} instance. No interface carries state, so all are effectively
 * stateless singletons.
 */
@Module
@InstallIn(SingletonComponent.class)
public final class ApiModule {

    private ApiModule() {
    }

    @Provides
    @Singleton
    public static AuthApi provideAuthApi(Retrofit retrofit) {
        return retrofit.create(AuthApi.class);
    }

    @Provides
    @Singleton
    public static HealthApi provideHealthApi(Retrofit retrofit) {
        return retrofit.create(HealthApi.class);
    }

    @Provides
    @Singleton
    public static UserApi provideUserApi(Retrofit retrofit) {
        return retrofit.create(UserApi.class);
    }

    @Provides
    @Singleton
    public static DrugApi provideDrugApi(Retrofit retrofit) {
        return retrofit.create(DrugApi.class);
    }

    @Provides
    @Singleton
    public static CategoryApi provideCategoryApi(Retrofit retrofit) {
        return retrofit.create(CategoryApi.class);
    }

    @Provides
    @Singleton
    public static InventoryApi provideInventoryApi(Retrofit retrofit) {
        return retrofit.create(InventoryApi.class);
    }

    @Provides
    @Singleton
    public static SalesApi provideSalesApi(Retrofit retrofit) {
        return retrofit.create(SalesApi.class);
    }

    @Provides
    @Singleton
    public static CustomerApi provideCustomerApi(Retrofit retrofit) {
        return retrofit.create(CustomerApi.class);
    }

    @Provides
    @Singleton
    public static DashboardApi provideDashboardApi(Retrofit retrofit) {
        return retrofit.create(DashboardApi.class);
    }

    @Provides
    @Singleton
    public static ReportApi provideReportApi(Retrofit retrofit) {
        return retrofit.create(ReportApi.class);
    }

    @Provides
    @Singleton
    public static AuditApi provideAuditApi(Retrofit retrofit) {
        return retrofit.create(AuditApi.class);
    }

    @Provides
    @Singleton
    public static SettingsApi provideSettingsApi(Retrofit retrofit) {
        return retrofit.create(SettingsApi.class);
    }
}