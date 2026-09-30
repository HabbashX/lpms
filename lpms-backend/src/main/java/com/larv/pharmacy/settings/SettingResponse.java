package com.larv.pharmacy.settings;

import java.time.Instant;

public record SettingResponse(
        String key,
        String value,
        String description,
        String type,
        Long updatedBy,
        Instant updatedAt) {
}
