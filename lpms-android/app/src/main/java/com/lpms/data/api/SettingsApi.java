package com.lpms.data.api;

import com.lpms.data.dto.SettingResponse;
import com.lpms.data.dto.UpdateSettingsRequest;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

/**
 * {@code /api/v1/settings} — ADMIN only ({@code @PreAuthorize("hasRole('ADMIN')")}).
 *
 * <p>Reads return the full list and the update returns the full updated list, so the
 * screen always re-renders from the server's answer rather than from local guesses.</p>
 */
public interface SettingsApi {

    @GET("api/v1/settings")
    Single<List<SettingResponse>> getAll();

    /** Body values are always strings: {@code {"settings":{"key":"value"}}}. */
    @PUT("api/v1/settings")
    Single<List<SettingResponse>> update(@Body UpdateSettingsRequest request);
}