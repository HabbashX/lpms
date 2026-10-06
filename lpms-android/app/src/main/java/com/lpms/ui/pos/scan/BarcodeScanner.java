package com.lpms.ui.pos.scan;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lpms.R;

/**
 * Launches {@link BarcodeScannerFragment} and delivers one scanned code back to the
 * caller through the fragment result API.
 *
 * <pre>{@code
 * BarcodeScanner.launch(this, code -> viewModel.addByBarcode(code));
 * }</pre>
 */
public final class BarcodeScanner {

    /** Fragment result key the scanner writes to. */
    public static final String RESULT_KEY = "pos_barcode";

    private BarcodeScanner() {
    }

    public static void launch(@NonNull Fragment caller,
                             @NonNull androidx.core.util.Consumer<String> onScanned) {
        if (caller.isStateSaved()) {
            return;
        }
        caller.getParentFragmentManager()
                .setFragmentResultListener(RESULT_KEY, caller, (key, bundle) -> {
                    String value = bundle == null ? null : bundle.getString(RESULT_KEY);
                    if (value != null && !value.trim().isEmpty()) {
                        onScanned.accept(value.trim());
                    }
                });
        caller.getParentFragmentManager()
                .beginTransaction()
                .add(R.id.pos_scanner_host, new BarcodeScannerFragment(), "barcode_scanner")
                .commit();
    }

    /** Called by the scanner with the decoded value. */
    public static void deliver(@NonNull Fragment scannerFragment, @NonNull String code) {
        Bundle result = new Bundle();
        result.putString(RESULT_KEY, code);
        scannerFragment.getParentFragmentManager()
                .setFragmentResult(RESULT_KEY, result);
        scannerFragment.getParentFragmentManager()
                .beginTransaction()
                .remove(scannerFragment)
                .commit();
    }
}