package com.lpms.core.di;

import android.content.Context;

import com.google.gson.Gson;
import com.lpms.BuildConfig;
import com.lpms.core.auth.OkHttpTokenRefresher;
import com.lpms.core.auth.SessionManager;
import com.lpms.core.auth.SessionStore;
import com.lpms.core.auth.TokenRefresher;
import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.AuthInterceptor;
import com.lpms.core.network.ServerUrl;
import com.lpms.core.network.TokenAuthenticator;
import com.lpms.core.network.json.LpmsGson;
import com.lpms.core.util.AppPreferences;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Network graph.
 *
 * <p>OkHttp interceptor order (application interceptors run in registration
 * order):</p>
 * <ol>
 *   <li>{@link AuthInterceptor} — attaches the bearer token.</li>
 *   <li>logging interceptor — debug builds only.</li>
 * </ol>
 *
 * <p>All traffic goes to the single HTTPS endpoint in {@link ServerUrl}.</p>
 *
 * <p>{@link TokenAuthenticator} is registered via {@code authenticator()} rather
 * than as an interceptor so it runs only after a 401 response, and OkHttp caps it at
 * one extra attempt per call.</p>
 */
@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    private static final long TIMEOUT_SECONDS = 30L;

    private NetworkModule() {
    }

    @Provides
    @Singleton
    public static Gson provideGson() {
        return LpmsGson.get();
    }

    @Provides
    @Singleton
    public static SessionStore provideSessionStore(@ApplicationContext Context context) {
        return new SessionStore(context);
    }

    @Provides
    @Singleton
    public static AppPreferences provideAppPreferences(@ApplicationContext Context context) {
        return new AppPreferences(context);
    }

    @Provides
    @Singleton
    public static ServerUrl provideServerUrl() {
        return new ServerUrl();
    }

    @Provides
    @Singleton
    public static ApiErrorMapper provideApiErrorMapper(Gson gson) {
        return new ApiErrorMapper(gson);
    }

    @Provides
    @Singleton
    public static OkHttpClient provideOkHttpClient(AuthInterceptor authInterceptor,
                                                  TokenAuthenticator authenticator) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor(authInterceptor)
                .authenticator(authenticator);

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(loggingInterceptor());
        }
        return builder.build();
    }

    /** Debug-only logger with the Authorization header redacted. Never in release. */
    private static HttpLoggingInterceptor loggingInterceptor() {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BASIC);
        logger.redactHeader("Authorization");
        logger.redactHeader("Cookie");
        return logger;
    }

    @Provides
    @Singleton
    public static Retrofit provideRetrofit(Gson gson, OkHttpClient client, ServerUrl serverUrl) {
        return new Retrofit.Builder()
                .baseUrl(serverUrl.baseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build();
    }
}

/** Interface → implementation bindings for the network/auth graph. */
@Module
@InstallIn(SingletonComponent.class)
abstract class AuthBindingsModule {

    private AuthBindingsModule() {
    }

    /**
     * The OkHttp authenticator depends on the {@link com.lpms.core.auth.TokenRefresher}
     * interface, never on the concrete implementation.
     */
    @Binds
    @Singleton
    abstract TokenRefresher bindTokenRefresher(OkHttpTokenRefresher refresher);
}