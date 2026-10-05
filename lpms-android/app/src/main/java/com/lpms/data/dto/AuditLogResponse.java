package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;

/** {@code AuditLogResponse} — {@code GET /audit} (ADMIN only), paginated. */
public final class AuditLogResponse {

    @SerializedName("id")
    private final Long id;

    @SerializedName("userId")
    private final Long userId;

    @SerializedName("username")
    private final String username;

    @SerializedName("action")
    private final String action;

    @SerializedName("entityType")
    private final String entityType;

    @SerializedName("entityId")
    private final Long entityId;

    @SerializedName("description")
    private final String description;

    @SerializedName("ipAddress")
    private final String ipAddress;

    @SerializedName("createdAt")
    private final Instant createdAt;

    public AuditLogResponse(@Nullable Long id,
                            @Nullable Long userId,
                            @Nullable String username,
                            @Nullable String action,
                            @Nullable String entityType,
                            @Nullable Long entityId,
                            @Nullable String description,
                            @Nullable String ipAddress,
                            @Nullable Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.description = description;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nullable
    public Long getUserId() {
        return userId;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @NonNull
    public AuditAction auditAction() {
        return AuditAction.fromNullable(action);
    }

    @Nullable
    public String getAction() {
        return action;
    }

    @Nullable
    public String getEntityType() {
        return entityType;
    }

    @Nullable
    public Long getEntityId() {
        return entityId;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    @Nullable
    public String getIpAddress() {
        return ipAddress;
    }

    @Nullable
    public Instant getCreatedAt() {
        return createdAt;
    }
}