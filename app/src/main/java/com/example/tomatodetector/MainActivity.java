package com.example.tomatodetector;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private YoloDetector detector;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private OverlayView overlayView;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Iniciando MainActivity...");

        detector = new YoloDetector(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlay_view);

        startCamera();
    }

    private void startCamera() {
        Log.d(TAG, "Inicializando câmera...");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isProcessing.get()) {
                        imageProxy.close();
                        return;
                    }
                    isProcessing.set(true);

                    cameraExecutor.execute(() -> {
                        List<DetectionResult> results = detector.detect(imageProxy);

                        boolean anyDetected = false;

                        if (!results.isEmpty()) {
                            anyDetected = true;
                            for (DetectionResult r : results) {
                                Log.d(TAG, "Detectado: " + r.getLabel() + " | Confiança: " + r.getConfidence());
                            }
                        }

                        int colorToShow = anyDetected ? Color.RED : Color.BLUE;
                        runOnUiThread(() -> overlayView.setBoxColor(colorToShow));

                        isProcessing.set(false);
                    });
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                Log.d(TAG, "Câmera ligada com sucesso.");
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Erro ao iniciar câmera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
