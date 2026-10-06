package com.lpms.ui.pos.scan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.lpms.R;
import com.lpms.databinding.FragmentBarcodeScannerBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * CameraX + ML Kit barcode scanner for the POS.
 *
 * <p>The first decoded value is delivered through the fragment result API and the
 * scanner closes itself, so a single scan can never add the same item twice. Analysis
 * runs on a background executor and everything is released in {@code onDestroyView}.</p>
 */
@AndroidEntryPoint
public final class BarcodeScannerFragment extends Fragment {

    private FragmentBarcodeScannerBinding binding;
    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private boolean resultDelivered;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (Boolean.TRUE.equals(granted)) {
                            startCamera();
                        } else {
                            requireActivity().finish();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBarcodeScannerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        analysisExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build());

        binding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ContextCompat.getMainExecutor(requireContext()).execute(() -> {
            if (binding == null) {
                return;
            }
            try {
                ProcessCameraProvider cameraProvider =
                        ProcessCameraProvider.getInstance(requireContext()).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.preview.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        // Only analyse the newest frame so scanning cannot lag behind.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                binding.hint.setText(R.string.pos_scanner_unavailable);
            }
        });
    }

    private void analyze(@NonNull ImageProxy imageProxy) {
        if (resultDelivered || scanner == null) {
            imageProxy.close();
            return;
        }
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees()))
                .addOnSuccessListener(this::onBarcodes)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onBarcodes(@NonNull List<Barcode> barcodes) {
        if (resultDelivered || barcodes.isEmpty()) {
            return;
        }
        String value = barcodes.get(0).getRawValue();
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        resultDelivered = true;
        com.lpms.ui.pos.scan.BarcodeScanner.deliver(this, value.trim());
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
        binding = null;
    }
}