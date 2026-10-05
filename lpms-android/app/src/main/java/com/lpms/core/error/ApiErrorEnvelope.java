package com.lpms.core.error;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wire shape of the backend error envelope. Deserialized leniently: any field may
 * be absent, so every member is nullable-tolerant.
 *
 * <pre>
 * { "timestamp":"...", "status":409, "code":"INSUFFICIENT_STOCK",
 *   "message":"...", "path":"/api/v1/sales",
 *   "errors":[{"field":"items[0].quantity","message":"quantity must be positive"}] }
 * </pre>
 */
public final class ApiErrorEnvelope {

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("status")
    private Integer status;

    @SerializedName("code")
    private String code;

    @SerializedName("message")
    private String message;

    @SerializedName("path")
    private String path;

    @SerializedName("errors")
    private List<FieldError> errors;

    public ApiErrorEnvelope() {
    }

    @Nullable
    public String getTimestamp() {
        return timestamp;
    }

    @Nullable
    public Integer getStatus() {
        return status;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getPath() {
        return path;
    }

    @Nullable
    public List<FieldError> getErrors() {
        return errors;
    }
}