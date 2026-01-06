package com.example.emrtdreader;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.emrtdreader.sdk.analyzer.MrzImageAnalyzer;
import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.MlKitOcrEngine;
import com.example.emrtdreader.sdk.ocr.TesseractOcrEngine;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;

import android.graphics.Rect;
import com.example.emrtdreader.sdk.utils.MrzParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MRZScanActivity extends AppCompatActivity implements MrzImageAnalyzer.Listener {

    private static final int OCR_PREVIEW_LINES = 2;

    private PreviewView previewView;
    private Spinner ocrSpinner;
    private TextView mrzTextView;
    private TextView metricsTextView;
    private Button logCopyButton;
    private Button manualButton;
    private Button continueButton;
    private LinearLayout manualPanel;
    private EditText docEdit, dobEdit, doeEdit;
    private Button confirmManualButton;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    private final MlKitOcrEngine mlKit = new MlKitOcrEngine();
    private final TesseractOcrEngine tess = new TesseractOcrEngine();
    private DualOcrRunner.Mode mode = DualOcrRunner.Mode.AUTO_DUAL;
    private MrzImageAnalyzer analyzer;
    private MrzResult latestMrz;
    private OcrResult latestOcr;

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mrz_scan);

        previewView = findViewById(R.id.cameraPreviewView);
        ocrSpinner = findViewById(R.id.ocrSpinner);
        mrzTextView = findViewById(R.id.mrzTextView);
        metricsTextView = findViewById(R.id.metricsTextView);
        logCopyButton = findViewById(R.id.logCopyButton);
        manualButton = findViewById(R.id.manualButton);
        continueButton = findViewById(R.id.continueButton);
        manualPanel = findViewById(R.id.manualPanel);
        docEdit = findViewById(R.id.docNumberEdit);
        dobEdit = findViewById(R.id.dobEdit);
        doeEdit = findViewById(R.id.doeEdit);
        confirmManualButton = findViewById(R.id.confirmManualButton);

        setupOcrSpinner();
        setupButtons();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupOcrSpinner() {
        String[] items = new String[]{"Auto (dual)", "ML Kit", "Tesseract"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        ocrSpinner.setAdapter(adapter);
        ocrSpinner.setSelection(0);
        ocrSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position == 0) {
                    mode = DualOcrRunner.Mode.AUTO_DUAL;
                } else if (position == 1) {
                    mode = DualOcrRunner.Mode.MLKIT_ONLY;
                } else {
                    mode = DualOcrRunner.Mode.TESS_ONLY;
                }

                if (analyzer != null) {
                    analyzer.setMode(mode);
                    analyzer.resetBurst();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupButtons() {
        logCopyButton.setOnClickListener(v -> {
            String payload = buildLogPayload();
            android.util.Log.i("MRZ", payload);
            copyToClipboard(payload);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        manualButton.setOnClickListener(v -> {
            manualPanel.setVisibility(manualPanel.getVisibility() == android.view.View.VISIBLE ? android.view.View.GONE : android.view.View.VISIBLE);
        });

        confirmManualButton.setOnClickListener(v -> {
            String doc = docEdit.getText().toString().trim();
            String dob = dobEdit.getText().toString().trim();
            String doe = doeEdit.getText().toString().trim();
            if (doc.isEmpty() || dob.length() != 6 || doe.length() != 6) {
                Toast.makeText(this, "Fill doc number + YYMMDD dates", Toast.LENGTH_LONG).show();
                return;
            }
            AccessKey.Mrz key = new AccessKey.Mrz(doc, dob, doe);
            goToNfc(key, null);
        });

        continueButton.setOnClickListener(v -> {
            if (latestMrz == null) {
                Toast.makeText(this, "MRZ not ready yet", Toast.LENGTH_SHORT).show();
                return;
            }
            AccessKey.Mrz key = MrzParser.toAccessKey(latestMrz);
            if (key == null) {
                Toast.makeText(this, "Cannot parse MRZ", Toast.LENGTH_SHORT).show();
                return;
            }
            goToNfc(key, latestMrz);
        });
    }

    private void goToNfc(AccessKey.Mrz key, MrzResult mrz) {
        Intent i = new Intent(this, NFCReadActivity.class);
        i.putExtra("accessKey", key);
        i.putExtra("mrz", mrz);
        startActivity(i);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("mrz-log", text));
    }

    private String buildLogPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append("OCR mode: ").append(mode.name()).append("\n");
        if (latestOcr != null) {
            sb.append("OCR elapsed: ").append(latestOcr.elapsedMs).append("ms\n");
            sb.append("Brightness: ").append(String.format("%.1f", latestOcr.metrics.brightness)).append("\n");
            sb.append("Contrast: ").append(String.format("%.1f", latestOcr.metrics.contrast)).append("\n");
            sb.append("Sharpness: ").append(String.format("%.1f", latestOcr.metrics.sharpness)).append("\n");
            sb.append("RAW OCR:\n").append(latestOcr.rawText).append("\n");
        }
        if (latestMrz != null) {
            sb.append("MRZ (best):\n").append(latestMrz.asMrzText()).append("\n");
            sb.append("Score: ").append(latestMrz.confidence).append("\n");
        }
        return sb.toString();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analyzer = new MrzImageAnalyzer(getApplicationContext(), mlKit, tess, mode, 200, this);
                analysis.setAnalyzer(analysisExecutor, analyzer);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

            } catch (Throwable e) {
                Toast.makeText(this, "Camera start error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi) {
        latestOcr = ocr;
        if (bestSingle != null) latestMrz = bestSingle;

        runOnUiThread(() -> {
            if (bestSingle != null) {
                mrzTextView.setText(bestSingle.asMrzText());
            } else if (ocr != null) {
                String preview = buildOcrPreview(ocr.rawText);
                StringBuilder status = new StringBuilder("MRZ not detected yet\nOCR running...");
                if (!preview.isEmpty()) {
                    status.append("\n").append(preview);
                }
                mrzTextView.setText(status.toString());
            } else {
                mrzTextView.setText("Camera not delivering frames");
            }
            if (ocr != null) {
                metricsTextView.setText(
                        "Mode: " + mode.name() +
                        " | " + ocr.elapsedMs + "ms" +
                        " | brightness " + String.format("%.0f", ocr.metrics.brightness) +
                        " | contrast " + String.format("%.0f", ocr.metrics.contrast) +
                        " | sharpness " + String.format("%.0f", ocr.metrics.sharpness)
                );
            }
        });
    }

    private String buildOcrPreview(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "";
        }
        String[] lines = rawText.split("\\R");
        StringBuilder preview = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (preview.length() > 0) {
                preview.append("\n");
            }
            preview.append(trimmed);
            count++;
            if (count >= OCR_PREVIEW_LINES) {
                break;
            }
        }
        return preview.toString();
    }

    @Override
    public void onFinalMrz(MrzResult finalMrz, Rect roi) {
        latestMrz = finalMrz;
        runOnUiThread(() -> {
            mrzTextView.setText(finalMrz.asMrzText());
            Toast.makeText(this, "MRZ locked (burst)", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onAnalyzerError(String message, Throwable error) {
        runOnUiThread(() -> {
            mrzTextView.setText("Analyzer error: " + message);
            Toast.makeText(this, "Analyzer error: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdownNow();
        mlKit.close();
        tess.close();
    }
}
