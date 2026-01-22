package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.NormalizedMrz;
import com.example.emrtdreader.sdk.models.MrzParseResult;
import com.example.emrtdreader.sdk.utils.MrzParserValidator;
import com.example.emrtdreader.sdk.ocr.MrzTextProcessor;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.FrameStats;
import com.example.emrtdreader.sdk.ocr.MrzAutoDetector;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.ocr.RectAverager;
import com.example.emrtdreader.sdk.utils.MrzBurstAggregator;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraX analyzer that:
 * - finds MRZ band automatically (deterministic)
 * - stabilizes ROI across frames
 * - runs OCR (MLKit/Tesseract or dual)
 * - aggregates MRZ across bursts
 *
 * IMPORTANT POLICY:
 * - MRZ is accepted ONLY from Tesseract (even in AUTO_DUAL mode)
 * - ML Kit can provide text feedback only (UI), not MRZ authority
 */
public final class MrzImageAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "MRZ";

    private static final String MSG_NO_ROI = "No MRZ ROI detected; using fallback ROI";
    private static final String MSG_SKIP_INTERVAL = "Frame skipped: interval";
    private static final String MSG_SKIP_OCR_IN_FLIGHT = "Frame skipped: OCR in flight";

    private static final float FALLBACK_ROI_HEIGHT_RATIO = 0.38f;
    private static final float FALLBACK_ROI_SIDE_MARGIN_RATIO = 0.05f;

    // ROI scaling for OCR stability (especially for Tesseract on small MRZ)
    private static final int TARGET_MRZ_LINE_PX = 110; // ~100-120px per line is a good baseline
    private static final float MIN_SCALE = 1.25f;
    private static final float MAX_SCALE = 4.0f;

    public interface Listener {
        void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi);
        void onFinalMrz(MrzResult finalMrz, Rect roi);
        void onAnalyzerError(String message, Throwable error);
        default void onScanState(ScanState state, String message) {}
        default void onFrameProcessed(ScanState state, String message, long timestampMs) {}
    }

    private final Context appContext;
    private final Listener listener;

    private final MrzBurstAggregator aggregator;
    private final RectAverager rectAverager;

    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean ocrInFlight = new AtomicBoolean(false);

    private volatile OcrEngine mlKitEngine;
    private volatile OcrEngine tessEngine;
    private volatile DualOcrRunner.Mode mode;

    private long lastTs = 0L;
    private final long intervalMs;
    private final String cameraId;

    // Heavy pipeline off main thread
    private final MrzPipelineExecutor pipelineExecutor = new MrzPipelineExecutor();

    public MrzImageAnalyzer(Context ctx,
                            OcrEngine mlKit,
                            OcrEngine tess,
                            DualOcrRunner.Mode mode,
                            long intervalMs,
                            Listener listener) {
        this(ctx, mlKit, tess, mode, intervalMs, "default", listener);
    }

    public MrzImageAnalyzer(Context ctx,
                            OcrEngine mlKit,
                            OcrEngine tess,
                            DualOcrRunner.Mode mode,
                            long intervalMs,
                            String cameraId,
                            Listener listener) {
        this.appContext = ctx.getApplicationContext();
        this.mlKitEngine = mlKit;
        this.tessEngine = tess;
        this.mode = mode;
        this.intervalMs = intervalMs;
        this.listener = listener;
        this.cameraId = (cameraId == null || cameraId.isBlank()) ? "default" : cameraId;

        this.aggregator = new MrzBurstAggregator(3, 12);
        this.rectAverager = new RectAverager(6, 0.35f);
    }

    @VisibleForTesting
    void setModeForTests(DualOcrRunner.Mode mode) {
        this.mode = mode;
    }

    public void setMode(DualOcrRunner.Mode mode) {
        this.mode = mode;
    }

    public void resetBurst() {
        finished.set(false);
        aggregator.reset();
        rectAverager.reset();
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        long now = System.currentTimeMillis();

        try {
            if (finished.get()) return;

            if (now - lastTs < intervalMs) {
                if (listener != null) listener.onScanState(ScanState.WAITING, "Analyzing...");
                notifyFrameProcessed(ScanState.WAITING, MSG_SKIP_INTERVAL, now);
                return;
            }
            lastTs = now;

            final int rotationDeg = image.getImageInfo().getRotationDegrees();

            // Convert quickly; heavy work goes to pipeline thread
            Bitmap frame = ImageProxyUtils.toBitmap(image);
            if (frame == null) {
                if (listener != null) listener.onScanState(ScanState.ERROR, "Frame->Bitmap failed");
                notifyFrameProcessed(ScanState.ERROR, "Frame->Bitmap failed", now);
                return;
            }

            // Always work with ARGB_8888 immutable copy
            Bitmap safe = frame.copy(Bitmap.Config.ARGB_8888, false);
            if (safe == null) {
                notifyFrameProcessed(ScanState.WAITING, "Bitmap copy failed", now);
                return;
            }

            // Rotate to upright BEFORE detection/OCR (critical)
            final Bitmap upright = (rotationDeg != 0) ? rotateBitmap(safe, rotationDeg) : safe;

            // Offload whole pipeline
            pipelineExecutor.submit(() -> runPipeline(upright));

        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = "Analyzer error while processing frame";
            notifyError(msg, e);
        } finally {
            image.close();
        }
    }

    private void runPipeline(Bitmap uprightFrame) {
        if (uprightFrame == null) return;
        if (finished.get()) return;

        // Prevent piling OCR jobs
        if (!ocrInFlight.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            // Expose the state to UI (no silent waiting).
            if (listener != null) listener.onScanState(ScanState.OCR_IN_FLIGHT, "Processing...");
            notifyFrameProcessed(ScanState.OCR_IN_FLIGHT, MSG_SKIP_OCR_IN_FLIGHT, now);
            return;
        }

        try {
            // Frame metrics (for UI/logs)
            FrameStats stats = FrameStats.compute(uprightFrame);
            OcrMetrics metrics = new OcrMetrics(stats.brightness, stats.contrast, stats.sharpness);

            Log.d(TAG, "FRAME_STATS mean=" + String.format(Locale.US, "%.1f", stats.brightness)
                    + " contrast=" + String.format(Locale.US, "%.1f", stats.contrast)
                    + " sharp=" + String.format(Locale.US, "%.1f", stats.sharpness)
                    + " noise=" + String.format(Locale.US, "%.2f", stats.noise));

            int w = uprightFrame.getWidth();
            int h = uprightFrame.getHeight();

            Rect detected = MrzAutoDetector.detect(uprightFrame);
            boolean roiDetected = (detected != null);
            if (detected == null) {
                detected = buildFallbackRoi(w, h);
                long ts = System.currentTimeMillis();
                if (listener != null) listener.onScanState(ScanState.MRZ_NOT_FOUND, "MRZ ROI not detected");
                notifyFrameProcessed(ScanState.MRZ_NOT_FOUND, MSG_NO_ROI, ts);
            }

            Rect stable = rectAverager.update(detected, w, h);

            Bitmap rawRoi = Bitmap.createBitmap(
                    uprightFrame,
                    stable.left,
                    stable.top,
                    stable.width(),
                    stable.height()
            );

            Bitmap roiForOcr = scaleMrzRoi(rawRoi);

            runOcrAsync(roiForOcr, metrics, stable, roiDetected);

        } catch (Throwable t) {
            ocrInFlight.set(false);
            notifyError("Pipeline error", t);
        }
    }

    private void runOcrAsync(Bitmap roiBmp, OcrMetrics metrics, Rect stable, boolean roiDetected) {
        DualOcrRunner.Mode m = (mode == null) ? DualOcrRunner.Mode.AUTO_DUAL : mode;

        DualOcrRunner.runAsync(appContext, m, mlKitEngine, tessEngine, roiBmp, 0,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult rr) {
                        ocrInFlight.set(false);
                        if (finished.get()) return;

                        OcrResult ocr;
                        if (rr != null && rr.ocr != null) {
                            // Force our frame metrics (compatible with MRZScanActivity)
                            ocr = new OcrResult(
                                    rr.ocr.rawText,
                                    rr.ocr.elapsedMs,
                                    metrics,
                                    rr.ocr.engine
                            );
                        } else {
                            ocr = new OcrResult("", 0L, metrics, OcrResult.Engine.UNKNOWN);
                        }

                        // ❗ MRZ принимаем только если источник OCR = TESSERACT (authority),
                        // но можем показывать кандидата из ML Kit как low-confidence (UI only).
                        MrzResult mrz = (rr != null) ? rr.mrz : null;
                        if (mrz != null && ocr.engine != OcrResult.Engine.TESSERACT) {
                            mrz = null;
                        }

                        // UI feedback (always)
                        if (listener != null) {
                            listener.onOcr(ocr, mrz, stable);
                            notifyOcrState(ocr);
                        }

                        // If Tess MRZ missing, try extracting a provisional MRZ from the available OCR text (UI only).
                        MrzResult provisionalMrz = null;
                        if (mrz == null && ocr != null && ocr.rawText != null && !ocr.rawText.trim().isEmpty()) {
                            provisionalMrz = MrzTextProcessor.parse(ocr.rawText);
                        }

                        if (mrz != null) {
                            // Validate MRZ (checksum/structure). Only valid MRZ can be "locked".
                            boolean valid = true;
                            try {
                                NormalizedMrz nm = new NormalizedMrz(java.util.Arrays.asList(mrz.asMrzText().split("
")));
                                MrzParseResult parsed = MrzParserValidator.parse(nm);
                                valid = (parsed != null && parsed.valid);
                            } catch (Throwable ignore) {
                                // If validator fails unexpectedly, do not block the user; treat as low confidence.
                                valid = false;
                            }

                            if (valid) {
                                if (listener != null) listener.onScanState(ScanState.MRZ_FOUND, "MRZ detected");
                                MrzResult finalMrz = aggregator.addAndMaybeAggregate(mrz);
                                if (finalMrz != null) {
                                    finished.set(true);
                                    if (listener != null) listener.onFinalMrz(finalMrz, stable);
                                }
                            } else {
                                if (listener != null) {
                                    listener.onScanState(ScanState.MRZ_FOUND_INVALID_CHECKSUM, "MRZ checksum/validation failed");
                                    // Still show MRZ text via onOcr (already sent above).
                                }
                            }
                        } else if (provisionalMrz != null) {
                            if (listener != null) {
                                // Show candidate to the user, but do not lock.
                                listener.onOcr(ocr, provisionalMrz, stable);
                                listener.onScanState(ScanState.MRZ_FOUND_LOW_CONFIDENCE, "Provisional MRZ (confirm/retry)");
                            }
                        } else {
                            // No usable MRZ yet. Distinguish "not found" vs "ocr rejected".
                            if (listener != null) {
                                if (!roiDetected) {
                                    listener.onScanState(ScanState.MRZ_NOT_FOUND, "MRZ not found");
                                } else if (ocr == null || ocr.rawText == null || ocr.rawText.trim().isEmpty()) {
                                    listener.onScanState(ScanState.MRZ_FOUND_OCR_REJECTED, "OCR produced no text");
                                } else {
                                    listener.onScanState(ScanState.MRZ_FOUND_OCR_REJECTED, "OCR text rejected as MRZ");
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        ocrInFlight.set(false);
                        String cause = (error != null && error.getMessage() != null && !error.getMessage().isBlank())
                                ? error.getMessage()
                                : "unknown error";
                        notifyError("OCR failed: " + cause, error);
                    }
                });
    }

    private static Bitmap rotateBitmap(Bitmap src, int rotationDeg) {
        try {
            Matrix m = new Matrix();
            m.postRotate(rotationDeg);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable t) {
            // If rotation fails, fallback to original (better than crash)
            return src;
        }
    }

    /**
     * Scale ROI so that one MRZ line is about TARGET_MRZ_LINE_PX pixels.
     * This significantly reduces confusion like '<' -> 'K' in Tesseract on small ROI.
     */
    private static Bitmap scaleMrzRoi(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return src;

        // MRZ is typically 2 lines => one line height ~ h/2
        int currentLinePx = Math.max(1, h / 2);
        float scale = TARGET_MRZ_LINE_PX / (float) currentLinePx;
        scale = Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));

        // If scale ~1, keep original to avoid extra alloc
        if (scale < 1.05f) return src;

        int newW = Math.max(1, Math.round(w * scale));
        int newH = Math.max(1, Math.round(h * scale));

        return Bitmap.createScaledBitmap(src, newW, newH, false);
    }

    private void notifyError(String message, Throwable error) {
        Log.e(TAG, message, error);
        if (listener != null) {
            listener.onAnalyzerError(message, error);
            listener.onScanState(ScanState.ERROR, message);
        }
    }

    private void notifyFrameProcessed(ScanState state, String message, long timestampMs) {
        if (listener != null) {
            listener.onFrameProcessed(state, message, timestampMs);
            listener.onScanState(state, message);
        }
    }

    private void notifyOcrState(OcrResult ocr) {
        if (listener == null || ocr == null) return;
        if (ocr.rawText == null || ocr.rawText.trim().isEmpty()) return;

        if (ocr.engine == OcrResult.Engine.ML_KIT) {
            listener.onScanState(ScanState.ML_TEXT_FOUND, "ML Kit OCR text detected");
        } else if (ocr.engine == OcrResult.Engine.TESSERACT) {
            listener.onScanState(ScanState.TESS_TEXT_FOUND, "Tesseract OCR text detected");
        }
    }

    private static Rect buildFallbackRoi(int frameWidth, int frameHeight) {
        int marginX = Math.round(frameWidth * FALLBACK_ROI_SIDE_MARGIN_RATIO);
        int roiHeight = Math.round(frameHeight * FALLBACK_ROI_HEIGHT_RATIO);

        int left = Math.max(0, marginX);
        int right = Math.min(frameWidth, frameWidth - marginX);

        int bottom = frameHeight;
        int top = Math.max(0, bottom - roiHeight);

        if (right <= left) {
            left = 0;
            right = frameWidth;
        }
        if (bottom <= top) {
            top = 0;
            bottom = frameHeight;
        }
        return new Rect(left, top, right, bottom);
    }
}
