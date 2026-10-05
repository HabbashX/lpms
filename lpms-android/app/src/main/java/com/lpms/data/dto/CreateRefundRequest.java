package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code POST /sales/{id}/refund} body (ADMIN/PHARMACIST) → 201
 * {@code RefundResponse}.
 *
 * <p><b>Omitting {@code items} (or sending an empty list) refunds everything still
 * refundable</b> — the full-refund path. Per-line quantities must not exceed
 * {@code quantity − refundedQuantity}; over-refunding is 409
 * {@code REFUND_QUANTITY_EXCEEDED}.</p>
 *
 * <p>Errors: 409 {@code SALE_ALREADY_REFUNDED}, {@code SALE_ITEM_NOT_FOUND},
 * {@code REFUND_QUANTITY_EXCEEDED}, {@code NOTHING_TO_REFUND}. A partial refund moves
 * the sale to {@code PARTIALLY_REFUNDED}, a full one to {@code REFUNDED}.</p>
 *
 * <p>There is no refunds-list endpoint: after a refund the client re-reads the sale
 * to pick up its new status and {@code refundedTotal}.</p>
 */
public final class CreateRefundRequest {

    /** Max length of {@code reason}. */
    public static final int REASON_MAX_LENGTH = 255;

    @SerializedName("items")
    private final List<RefundItemRequest> items;

    @SerializedName("reason")
    private final String reason;

    public CreateRefundRequest(@Nullable List<RefundItemRequest> items, @Nullable String reason) {
        this.items = items == null ? null : new ArrayList<>(items);
        this.reason = reason;
    }

    /** Full refund: no {@code items} key at all. */
    @NonNull
    public static CreateRefundRequest refundEverything(@Nullable String reason) {
        return new CreateRefundRequest(null, trimReason(reason));
    }

    /** Partial refund of specific lines. An empty list is treated as "everything". */
    @NonNull
    public static CreateRefundRequest ofLines(@NonNull List<RefundItemRequest> lines,
                                              @Nullable String reason) {
        return new CreateRefundRequest(lines, trimReason(reason));
    }

    /** Null when the request is a full refund. */
    @Nullable
    public List<RefundItemRequest> getItems() {
        return items;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public boolean isFullRefund() {
        return items == null || items.isEmpty();
    }

    @Nullable
    private static String trimReason(@Nullable String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > REASON_MAX_LENGTH
                ? trimmed.substring(0, REASON_MAX_LENGTH)
                : trimmed;
    }

    @NonNull
    static List<RefundItemRequest> noItems() {
        return Collections.emptyList();
    }
}