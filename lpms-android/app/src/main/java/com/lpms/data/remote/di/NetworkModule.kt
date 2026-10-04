package com.lpms.data.remote.di

import com.lpms.BuildConfig
import com.lpms.data.remote.AuthInterceptor
import com.lpms.data.remote.LpmsApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Networking wiring.
 *
 * Nothing here carries credentials: the base URL comes from `BuildConfig`
 * (gitignored file) and the Bearer token is attached per-request by
 * [AuthInterceptor], never baked into a singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * The backend drops unknown fields when it evolves; we must not fall over
     * when it does. `coerceInputValues` also means a backend that omits an
     * optional field still deserializes against our default.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Innermost: sees the URL, appends Authorization, runs first on the
            // way out and last on the way back — so its 401 handler sees the
            // final response.
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG_LOGGING) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    // Never let the token reach logcat.
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, client: OkHttpClient): Retrofit = Retrofit.Builder()
        // Trailing slash is load-bearing: Retrofit resolves relative paths
        // against it, and every endpoint in LpmsApi is written as one.
        .baseUrl("${BuildConfig.BASE_URL}${BuildConfig.API_PREFIX}/")
        .client(client)

        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideLpmsApi(retrofit: Retrofit): LpmsApi = retrofit.create(LpmsApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
}
