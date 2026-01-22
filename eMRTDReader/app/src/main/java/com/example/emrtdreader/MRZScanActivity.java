package com.example.emrtdreader;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
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
import com.example.emrtdreader.sdk.analysis.ScanState;
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
import java.util.Locale;
import java.util.Objects;

public class MRZScanActivity extends AppCompatActivity implements MrzImageAnalyzer.Listener {

    static final String EXTRA_DISABLE_CAMERA = "com.example.emrtdreader.EXTRA_DISABLE_CAMERA";
    private static final int OCR_PREVIEW_LINES = 2;
    private static final long OVERLAY_ANIMATION_MS = 180L;

    private PreviewView previewView;
    private View analysisOverlayView;
    private Spinner ocrSpinner;
    private TextView mrzTextView;
    private TextView metricsTextView;
    private ScrollView logScrollView;
    private TextView logTextView;
    private Button logCopyButton;
    private Button manualButton;
    private Button continueButton;
    private LinearLayout manualPanel;
    private EditText docEdit, dobEdit, doeEdit;
    private Button confirmManualButton;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    // Scan state logging (Logcat) — keeps UI and logs in sync for debugging.
    private ScanState lastScanState = null;
    private String lastScanMessage = null;
    private long lastScanStateLogAtMs = 0L;


    private final MlKitOcrEngine mlKit = new MlKitOcrEngine();
    private final TesseractOcrEngine tess = new TesseractOcrEngine();
    private DualOcrRunner.Mode mode = DualOcrRunner.Mode.AUTO_DUAL;
    private MrzImageAnalyzer analyzer;
    private MrzResult latestMrz;
    private OcrResult latestOcr;
    private ValueAnimator overlayAnimator;
    private int overlayCurrentColor;
    private int overlayTargetColor;
    private boolean disableOverlayAnimationForTesting;

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
        analysisOverlayView = findViewById(R.id.analysisOverlayView);
        ocrSpinner = findViewById(R.id.ocrSpinner);
        mrzTextView = findViewById(R.id.mrzTextView);
        metricsTextView = findViewById(R.id.metricsTextView);
        logScrollView = findViewById(R.id.logScrollView);
        logTextView = findViewById(R.id.logTextView);
        logCopyButton = findViewById(R.id.logCopyButton);
        manualButton = findViewById(R.id.manualButton);
        continueButton = findViewById(R.id.continueButton);
        manualPanel = findViewById(R.id.manualPanel);
        docEdit = findViewById(R.id.docNumberEdit);
        dobEdit = findViewById(R.id.dobEdit);
        doeEdit = findViewById(R.id.doeEdit);
        confirmManualButton = findViewById(R.id.confirmManualButton);
        overlayCurrentColor = ContextCompat.getColor(this, R.color.overlay_waiting_gray);
        overlayTargetColor = overlayCurrentColor;
        applyOverlayColor(overlayCurrentColor);

        setupOcrSpinner();
        setupButtons();

        if (shouldStartCamera()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA);
            }
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
        appendLogLine("NFC: launching read flow");
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
            sb.append("OCR source: ").append(latestOcr.engine.name()).append("\n");
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

                analyzer = new MrzImageAnalyzer(getApplicationContext(), mlKit, tess, mode, 200, "back", this);
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
                appendLogLine("MRZ candidate (score " + bestSingle.confidence + "):\n" + bestSingle.asMrzText());
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
                appendLogLine(buildFrameLogLine(ocr, bestSingle));
                appendRawOcrLines(ocr);
                metricsTextView.setText(
                        "Mode: " + mode.name() +
                        " | " + ocr.elapsedMs + "ms" +
                        " | source " + ocr.engine.name() +
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
            updateOverlayColor(resolveOverlayColor(ScanState.MRZ_FOUND));
            mrzTextView.setText(finalMrz.asMrzText());
            appendLogLine("MRZ locked (burst):\n" + finalMrz.asMrzText());
            Toast.makeText(this, "MRZ locked (burst)", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onAnalyzerError(String message, Throwable error) {
        runOnUiThread(() -> {
            mrzTextView.setText("Analyzer error: " + message);
            appendLogLine("ERROR: Analyzer error: " + message);
            Toast.makeText(this, "Analyzer error: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onScanState(ScanState state, String message) {
        // Logcat: emit on state change (or if message changed), with basic throttling.
        long now = android.os.SystemClock.elapsedRealtime();
        boolean changed = (state != lastScanState) || !Objects.equals(message, lastScanMessage);
        if (changed || (now - lastScanStateLogAtMs) > 1000) {
            android.util.Log.i("MRZ_STATE", "state=" + state + " msg=" + (message == null ? "" : message));
            lastScanState = state;
            lastScanMessage = message;
            lastScanStateLogAtMs = now;
        }

        runOnUiThread(() -> {
            updateOverlayColor(resolveOverlayColor(state));
            appendScanStateLogLine(state, message);
        });
    }

    @Override
    public void onFrameProcessed(ScanState state, String message, long timestampMs) {
        runOnUiThread(() -> appendLogLine(buildHeartbeatLogLine(timestampMs, state, message)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
        }
        analysisExecutor.shutdownNow();
        mlKit.close();
        tess.close();
    }

    @ColorInt
    private int resolveOverlayColor(ScanState state) {
        if (state == null) {
            return ContextCompat.getColor(this, R.color.overlay_waiting_gray);
        }
        switch (state) {
            case MRZ_FOUND:
                return ContextCompat.getColor(this, R.color.overlay_mrz_green);
            case ML_TEXT_FOUND:
                return ContextCompat.getColor(this, R.color.overlay_mlkit_purple);
            case TESS_TEXT_FOUND:
                return ContextCompat.getColor(this, R.color.overlay_tess_blue);
            case ERROR:
                return ContextCompat.getColor(this, R.color.overlay_error_red);
            case WAITING:
            default:
                return ContextCompat.getColor(this, R.color.overlay_waiting_gray);
        }
    }

    private void updateOverlayColor(@ColorInt int targetColor) {
        if (analysisOverlayView == null) {
            return;
        }
        if (targetColor == overlayTargetColor) {
            return;
        }
        overlayTargetColor = targetColor;
        int startColor = overlayCurrentColor;
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
        }
        long duration = getOverlayAnimationDuration();
        if (duration == 0L) {
            applyOverlayColor(targetColor);
            overlayCurrentColor = targetColor;
            return;
        }
        overlayAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        overlayAnimator.setDuration(duration);
        overlayAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        overlayAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            applyOverlayColor(color);
            overlayCurrentColor = color;
        });
        overlayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                overlayCurrentColor = targetColor;
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                overlayCurrentColor = targetColor;
            }
        });
        overlayAnimator.start();
    }

    private void applyOverlayColor(@ColorInt int color) {
        Drawable background = analysisOverlayView.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable drawable = (GradientDrawable) background.mutate();
            int strokeWidth = getResources().getDimensionPixelSize(R.dimen.overlay_stroke_width);
            drawable.setColor(android.graphics.Color.TRANSPARENT);
            drawable.setStroke(strokeWidth, color);
        } else {
            analysisOverlayView.setBackgroundColor(color);
        }
    }

    private long getOverlayAnimationDuration() {
        if (disableOverlayAnimationForTesting) {
            return 0L;
        }
        return "robolectric".equalsIgnoreCase(Build.FINGERPRINT) ? 0L : OVERLAY_ANIMATION_MS;
    }

    private String buildFrameLogLine(OcrResult ocr, MrzResult bestSingle) {
        long timestamp = System.currentTimeMillis();
        boolean mrzValid = bestSingle != null;
        String rawText = ocr != null ? ocr.rawText : "";
        int mlLen = 0;
        int tessLen = 0;
        if (ocr != null) {
            if (ocr.engine == OcrResult.Engine.ML_KIT) {
                mlLen = rawLength(rawText);
            } else if (ocr.engine == OcrResult.Engine.TESSERACT) {
                tessLen = rawLength(rawText);
            }
        }
        return "[frame] ts=" + timestamp
                + " mean=" + formatMetric(ocr.metrics.brightness)
                + " contrast=" + formatMetric(ocr.metrics.contrast)
                + " sharp=" + formatMetric(ocr.metrics.sharpness)
                + " engine=" + ocr.engine.name()
                + " mrzValid=" + mrzValid
                + " mlLen=" + mlLen
                + " tessLen=" + tessLen;
    }

    private String buildHeartbeatLogLine(long timestampMs, ScanState state, String message) {
        long ts = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
        String stateLabel = state != null ? state.name() : "UNKNOWN";
        String msg = message != null ? message : "";
        return "[heartbeat] ts=" + ts + " state=" + stateLabel + " msg=" + msg;
    }

    private String buildScanStateLogLine(ScanState state, String message) {
        if (state == null) {
            return null;
        }

        String detail = (message == null) ? "" : message.trim();
        if (!detail.isEmpty()) {
            detail = " | " + detail;
        }

        switch (state) {
            case ML_TEXT_FOUND:
                return buildTimestampedLogLine("ML text detected" + detail);
            case TESS_TEXT_FOUND:
                return buildTimestampedLogLine("Tess text detected" + detail);
            case WAITING:
                return buildTimestampedLogLine("Waiting for MRZ" + detail);

            case OCR_IN_FLIGHT:
                return buildTimestampedLogLine("OCR in progress" + detail);

            case MRZ_NOT_FOUND:
                return buildTimestampedLogLine("MRZ not found" + detail);

            case MRZ_OCR_REJECTED:
                return buildTimestampedLogLine("MRZ OCR rejected" + detail);

            case MRZ_INVALID:
                return buildTimestampedLogLine("MRZ invalid (checksum/format)" + detail);

            case MRZ_OCR_TIMEOUT:
                return buildTimestampedLogLine("MRZ OCR timeout" + detail);

            case MRZ_RETRY_REQUIRED:
                return buildTimestampedLogLine("Retry required" + detail);

            case MRZ_FOUND:
                return buildTimestampedLogLine("MRZ found" + detail);

            case ERROR:
                String err = (message == null || message.trim().isEmpty()) ? "Unknown error" : message.trim();
                return buildTimestampedLogLine("Error: " + err);

            default:
                // Fallback: always log unknown/new states to avoid silent loss of information.
                return buildTimestampedLogLine(state.name() + detail);
        }
    }

    private void appendScanStateLogLine(ScanState state, String message) {
        String logLine = buildScanStateLogLine(state, message);
        if (logLine != null) {
            appendLogLine(logLine);
        }
    }

    private String buildTimestampedLogLine(String message) {
        long timestamp = System.currentTimeMillis();
        return "[state] ts=" + timestamp + " " + message;
    }

    private String formatMetric(double metric) {
        return String.format(Locale.US, "%.1f", metric);
    }

    private int rawLength(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return 0;
        }
        return rawText.length();
    }

    private String normalizeRawOcrText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "(empty)";
        }
        return rawText;
    }

    private void appendRawOcrLines(OcrResult ocr) {
        if (logTextView == null) {
            return;
        }
        if (ocr == null) {
            return;
        }
        String prefix = ocr.engine == OcrResult.Engine.ML_KIT ? "ML:" : "TESS:";
        String normalized = normalizeRawOcrText(ocr.rawText);
        String[] lines = normalized.split("\\R");
        for (String line : lines) {
            appendLogLine(prefix + " " + line);
        }
    }

    private void appendLogLine(String line) {
        if (logTextView == null) {
            return;
        }
        if (logTextView.length() > 0) {
            logTextView.append("\n");
        }
        logTextView.append(line);
        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private boolean shouldStartCamera() {
        Intent intent = getIntent();
        return intent == null || !intent.getBooleanExtra(EXTRA_DISABLE_CAMERA, false);
    }

    @androidx.annotation.VisibleForTesting
    void setOverlayAnimationDisabledForTesting(boolean disabled) {
        disableOverlayAnimationForTesting = disabled;
    }
}