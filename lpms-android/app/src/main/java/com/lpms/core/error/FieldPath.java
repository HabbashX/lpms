package com.lpms.core.error;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Helpers to match a backend {@code field} path against a form control.
 *
 * <p>Backend validation paths observed in the contract:</p>
 * <ul>
 *   <li>{@code name}, {@code username}, {@code password}, {@code newPassword},
 *       {@code currentPassword}, {@code phone}, {@code address}, {@code notes}</li>
 *   <li>{@code items[0].quantity}, {@code items[1].unitSellingPrice}</li>
 *   <li>{@code sellingPrice}, {@code unitPurchasePrice}, {@code quantity},
 *       {@code amount}, {@code discount}, {@code profitPerUnit}, {@code marginPercent}</li>
 *   <li>{@code expirationDate}, {@code settings.inventory.expiring_soon_days}</li>
 * </ul>
 */
public final class FieldPath {

    private FieldPath() {
    }

    /**
     * @return true when {@code path} refers to {@code field} or to a nested/indexed
     * child of it (e.g. field {@code items}, path {@code items[0].quantity}).
     */
    public static boolean isOrIsUnder(@Nullable String path, @Nullable String field) {
        if (path == null || field == null) {
            return false;
        }
        if (path.equals(field)) {
            return true;
        }
        String prefix = field + ".";
        String indexedPrefix = field + "[";
        return path.startsWith(prefix) || path.startsWith(indexedPrefix);
    }

    /**
     * @return true when {@code path} points at exactly the given cart line, e.g.
     * {@code items[2].quantity} for line index {@code 2}.
     */
    public static boolean isItemField(@Nullable String path, int index, @Nullable String leaf) {
        if (path == null || leaf == null) {
            return false;
        }
        String head = "items[" + index + "]";
        return path.equals(head + "." + leaf) || path.startsWith(head + ".");
    }

    /** Normalises a path for logging: never surfaces token or password values. */
    public static String describe(@Nullable String path) {
        return path == null ? "<none>" : path.toLowerCase(Locale.ROOT);
    }
}