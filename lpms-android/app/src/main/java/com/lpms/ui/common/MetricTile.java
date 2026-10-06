package com.lpms.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.lpms.databinding.ViewMetricTileBinding;

/**
 * Small helper for the reusable {@code view_metric_tile.xml} card.
 *
 * <p>Keeps every dashboard/summary tile consistent: icon, label, value, optional
 * caption. Included layouts are exposed by ViewBinding under their own id, so a tile is
 * just {@code MetricTile.bind(binding.tileTodayRevenue, …)}.</p>
 */
public final class MetricTile {

    private MetricTile() {
    }

    public static void bind(@NonNull ViewMetricTileBinding tile,
                            @DrawableRes int iconRes,
                            @StringRes int labelRes,
                            @Nullable CharSequence value,
                            @Nullable CharSequence caption) {
        tile.tileIcon.setImageResource(iconRes);
        tile.tileLabel.setText(labelRes);
        tile.tileValue.setText(value == null ? "" : value);
        if (caption == null || caption.length() == 0) {
            tile.tileCaption.setVisibility(android.view.View.GONE);
        } else {
            tile.tileCaption.setVisibility(android.view.View.VISIBLE);
            tile.tileCaption.setText(caption);
        }
    }

    /** Updates only the value/caption, leaving the icon and label untouched. */
    public static void update(@NonNull ViewMetricTileBinding tile,
                              @Nullable CharSequence value,
                              @Nullable CharSequence caption) {
        if (value != null) {
            tile.tileValue.setText(value);
        }
        if (caption == null || caption.length() == 0) {
            tile.tileCaption.setVisibility(android.view.View.GONE);
        } else {
            tile.tileCaption.setVisibility(android.view.View.VISIBLE);
            tile.tileCaption.setText(caption);
        }
    }
}