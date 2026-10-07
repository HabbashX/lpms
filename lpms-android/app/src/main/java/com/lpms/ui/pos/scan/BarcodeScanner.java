package com.lpms.ui.pos.scan;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.lpms.R;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Launches {@link BarcodeScannerFragment} and delivers one scanned code back to the
 * caller through the fragment result API.
 *
 * <pre>{@code
 * BarcodeScanner.launch(this, code -> viewModel.addByBarcode(code));
 * }</pre>
 *
 * <p>The scanner is a navigation destination, so any screen can open it and closing it
 * returns there. Callers do not have to host a container view, and a dismissed scan cannot
 * be left stranded behind the screen that opened it.</p>
 */
public final class BarcodeScanner {

    /** Fragment result key the scanner writes to. */
    public static final String RESULT_KEY = "scanned_barcode";

    /**
     * Fragments that already observe {@link #RESULT_KEY}. Weak so a destroyed fragment is
     * not retained; a Fragment has no {@code getTag(int)}, hence the map.
     */
    private static final Map<Fragment, Boolean> LISTENERS = new WeakHashMap<>();

    private BarcodeScanner() {
    }

    /**
     * Opens the scanner. {@code onScanned} runs only when a code was actually read, so an
     * empty field is never overwritten by a cancelled scan.
     *
     * <p>Safe to call on every tap: the result listener is registered only on the first
     * call, and the scanner is not opened twice if it is already showing. Without that,
     * two taps would register two listeners and one scan would be handled twice - adding
     * the same drug to the cart twice.</p>
     */
    public static void launch(@NonNull Fragment caller,
                             @NonNull androidx.core.util.Consumer<String> onScanned) {
        if (caller.isStateSaved()) {
            return;
        }
        NavController controller = NavHostFragment.findNavController(caller);
        if (controller.getCurrentDestination() != null
                && controller.getCurrentDestination().getId() == R.id.barcodeScannerFragment) {
            return;
        }
        synchronized (LISTENERS) {
            if (LISTENERS.put(caller, Boolean.TRUE) == null) {
                caller.getParentFragmentManager()
                        .setFragmentResultListener(RESULT_KEY, caller, (key, bundle) -> {
                            String value = bundle == null ? null : bundle.getString(RESULT_KEY);
                            if (value != null && !value.trim().isEmpty()) {
                                onScanned.accept(value.trim());
                            }
                        });
            }
        }
        controller.navigate(R.id.barcodeScannerFragment);
    }

    /** Called by the scanner with the decoded value. */
    public static void deliver(@NonNull Fragment scannerFragment, @NonNull String code) {
        Bundle result = new Bundle();
        result.putString(RESULT_KEY, code);
        scannerFragment.getParentFragmentManager()
                .setFragmentResult(RESULT_KEY, result);
        close(scannerFragment);
    }

    /** Leaves the scanner without reporting a code. */
    public static void close(@NonNull Fragment scannerFragment) {
        if (scannerFragment.isStateSaved()) {
            return;
        }
        NavHostFragment.findNavController(scannerFragment).navigateUp();
    }
}