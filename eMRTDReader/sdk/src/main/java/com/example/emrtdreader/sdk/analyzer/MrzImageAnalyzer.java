package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.FrameStats;
import com.example.emrtdreader.sdk.ocr.MrzAutoDetector;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.ocr.OcrRouter;
import com.example.emrtdreader.sdk.ocr.RectAverager;
import com.example.emrtdreader.sdk.utils.MrzBurstAggregator;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Final MRZ CameraX analyzer.
 *
 * Properties:
 * - analyze() is lightweight (no OCR, no heavy scaling loops)
 * - heavy work runs in single-thread background pipeline
 * - MRZ is accepted ONLY from Tesseract
 * - ML Kit is UI-only (text presence feedback)
 * - stable ROI + burst aggregation
 */
public final class MrzImageAnalyzer implements ImageAnalysis.Analyzer {

    private static final String TAG = "MRZ";

    // ---- config ----
    private static final long DEFAULT_INTERVAL_MS = 180;

    // ---- deps ----
    private final Context appContext;
    private final OcrEngine mlKitEngine;
    private final OcrEngine tessEngine;
    private final Listener listener;

    // ---- state ----
    private final MrzPipelineExecutor pipelineExecutor = new MrzPipelineExecutor();
    private final RectAverager rectAverager = new RectAverager(6, 0.35f);
    private final MrzBurstAggregator burstAggregator = new MrzBurstAggregator(3, 12);

    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean ocrInFlight = new AtomicBoolean(false);

    private long lastAnalyzeTs = 0L;
    private final long intervalMs;

    // ---- listener ----
    public interface Listener {
        void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi);
        void onFinalMrz(MrzResult finalMrz, Rect roi);
        void onAnalyzerError(String message, Throwable error);
        default void onScanState(ScanState state, String message) {}
    }

    // ---- ctor ----
    public MrzImageAnalyzer(
            Context ctx,
            OcrEngine mlKitEngine,
            OcrEngine tessEngine,
            Listener listener
    ) {
        this(ctx, mlKitEngine, tessEngine, DEFAULT_INTERVAL_MS, listener);
    }

    public MrzImageAnalyzer(
            Context ctx,
            OcrEngine mlKitEngine,
            OcrEngine tessEngine,
            long intervalMs,
            Listener listener
    ) {
        this.appContext = ctx.getApplicationContext();
        this.mlKitEngine = mlKitEngine;
        this.tessEngine = tessEngine;
        this.listener = listener;
        this.intervalMs = intervalMs;
    }

    // ---- public API ----
    public void reset() {
        finished.set(false);
        burstAggregator.reset();
        rectAverager.reset();
    }

    // ---- CameraX entry ----
    @Override
    public void analyze(@NonNull ImageProxy image) {
        long now = System.currentTimeMillis();

        try {
            if (finished.get()) return;
            if (now - lastAnalyzeTs < intervalMs) return;
            lastAnalyzeTs = now;

            // lightweight conversion ONLY
            Bitmap frame = ImageProxyUtils.toBitmap(image);
            if (frame == null) return;

            Bitmap safeBitmap = frame.copy(Bitmap.Config.ARGB_8888, false);
            if (safeBitmap == null) return;

            pipelineExecutor.submit(() -> runPipeline(safeBitmap));

        } catch (Throwable t) {
            notifyError("Analyzer error", t);
        } finally {
            image.close();
        }
    }

    // ---- background pipeline ----
    private void runPipeline(Bitmap frameBitmap) {
        if (finished.get()) return;
        if (!ocrInFlight.compareAndSet(false, true)) return;

        try {
            // --- stats (optional, debug only) ---
            FrameStats stats = FrameStats.compute(frameBitmap);
            Log.d(TAG, String.format(
                    Locale.US,
                    "FRAME mean=%.1f contrast=%.1f sharp=%.1f",
                    stats.brightness,
                    stats.contrast,
                    stats.sharpness
            ));

            // --- MRZ detect ---
            Rect detected = MrzAutoDetector.detect(frameBitmap);
            if (detected == null) {
                notifyState(ScanState.WAITING, "Searching MRZ");
                return;
            }

            // --- stabilize ROI ---
            Rect stable = rectAverager.update(
                    detected,
                    frameBitmap.getWidth(),
                    frameBitmap.getHeight()
            );

            // --- crop ROI ---
            Bitmap roi = Bitmap.createBitmap(
                    frameBitmap,
                    stable.left,
                    stable.top,
                    stable.width(),
                    stable.height()
            );

            // --- OCR routing ---
            OcrRouter.runAsync(
                    appContext,
                    mlKitEngine,
                    tessEngine,
                    roi,
                    0,
                    "camera",
                    frameBitmap.getWidth(),
                    frameBitmap.getHeight(),
                    new OcrRouter.Callback() {

                        @Override
                        public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                            ocrInFlight.set(false);

                            // UI feedback (text presence)
                            if (listener != null) {
                                listener.onOcr(
                                        new OcrResult(
                                                result.finalText,
                                                result.elapsedMs,
                                                result.metrics,
                                                result.engine
                                        ),
                                        mrz,
                                        stable
                                );
                            }

                            // ❗ MRZ принимается ТОЛЬКО если Tesseract дал валидный результат
                            if (mrz != null) {
                                notifyState(ScanState.MRZ_FOUND, "MRZ detected");

                                MrzResult finalMrz =
                                        burstAggregator.addAndMaybeAggregate(mrz);

                                if (finalMrz != null) {
                                    finished.set(true);
                                    notifyState(ScanState.CONFIRMED, "MRZ confirmed");
                                    if (listener != null) {
                                        listener.onFinalMrz(finalMrz, stable);
                                    }
                                }
                            } else {
                                notifyState(ScanState.WAITING, "Waiting for MRZ");
                            }
                        }

                        @Override
                        public void onFailure(Throwable error) {
                            ocrInFlight.set(false);
                            notifyError("OCR failed", error);
                        }
                    }
            );

        } catch (Throwable t) {
            ocrInFlight.set(false);
            notifyError("Pipeline error", t);
        }
    }

    // ---- helpers ----
    private void notifyError(String message, Throwable error) {
        Log.e(TAG, message, error);
        if (listener != null) {
            listener.onAnalyzerError(message, error);
            listener.onScanState(ScanState.ERROR, message);
        }
    }

    private void notifyState(ScanState state, String message) {
        if (listener != null) {
            listener.onScanState(state, message);
        }
    }
}
