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
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.FrameStats;
import com.example.emrtdreader.sdk.ocr.MrzAutoDetector;
import com.example.emrtdreader.sdk.ocr.OcrRouter;
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
 */
public class MrzImageAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "MRZ";
    private static final String MSG_NO_ROI = "No MRZ ROI detected; using fallback ROI";
    private static final String MSG_SKIP_INTERVAL = "Frame skipped: interval";
    private static final String MSG_SKIP_OCR_IN_FLIGHT = "Frame skipped: OCR in flight";
    private static final float FALLBACK_ROI_HEIGHT_RATIO = 0.38f;
    private static final float FALLBACK_ROI_SIDE_MARGIN_RATIO = 0.05f;

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
    private final YuvBitmapConverter yuvBitmapConverter;
    private final String cameraId;

    private volatile OcrEngine mlKitEngine;
    private volatile OcrEngine tessEngine;
    private volatile DualOcrRunner.Mode mode;

    private long lastTs = 0L;
    private final long intervalMs;

    public MrzImageAnalyzer(Context ctx,
                           OcrEngine mlKit,
                           OcrEngine tess,
                           DualOcrRunner.Mode mode,
                           long intervalMs,
                           Listener listener) {
        this(ctx, mlKit, tess, mode, intervalMs, "default", listener, new YuvBitmapConverter(ctx.getApplicationContext()));
    }

    public MrzImageAnalyzer(Context ctx,
                            OcrEngine mlKit,
                            OcrEngine tess,
                            DualOcrRunner.Mode mode,
                            long intervalMs,
                            String cameraId,
                            Listener listener) {
        this(ctx, mlKit, tess, mode, intervalMs, cameraId, listener, new YuvBitmapConverter(ctx.getApplicationContext()));
    }

    @VisibleForTesting
    MrzImageAnalyzer(Context ctx,
                     OcrEngine mlKit,
                     OcrEngine tess,
                     DualOcrRunner.Mode mode,
                     long intervalMs,
                     Listener listener,
                     YuvBitmapConverter yuvBitmapConverter) {
        this(ctx, mlKit, tess, mode, intervalMs, "default", listener, yuvBitmapConverter);
    }

    @VisibleForTesting
    MrzImageAnalyzer(Context ctx,
                     OcrEngine mlKit,
                     OcrEngine tess,
                     DualOcrRunner.Mode mode,
                     long intervalMs,
                     String cameraId,
                     Listener listener,
                     YuvBitmapConverter yuvBitmapConverter) {
        this.appContext = ctx.getApplicationContext();
        this.mlKitEngine = mlKit;
        this.tessEngine = tess;
        this.mode = mode;
        this.intervalMs = intervalMs;
        this.listener = listener;
        this.aggregator = new MrzBurstAggregator(3, 12);
        this.rectAverager = new RectAverager(6, 0.35f);
        this.yuvBitmapConverter = yuvBitmapConverter;
        this.cameraId = cameraId;
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
        try {
            if (finished.get()) {
                return;
            }
            log("FRAME ts=" + System.currentTimeMillis() + " w=" + image.getWidth() + " h=" + image.getHeight());
            long now = System.currentTimeMillis();
            if (now - lastTs < intervalMs) {
                notifyFrameProcessed(ScanState.WAITING, MSG_SKIP_INTERVAL, now);
                return;
            }
            lastTs = now;

            int rotationDeg = image.getImageInfo().getRotationDegrees();
            Bitmap bitmap = imageProxyToBitmap(image);
            Bitmap safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (safeBitmap == null) {
                throw new IllegalStateException("Bitmap copy failed");
            }

            if (rotationDeg != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotationDeg);
                safeBitmap = Bitmap.createBitmap(safeBitmap, 0, 0, safeBitmap.getWidth(), safeBitmap.getHeight(), m, true);
                rotationDeg = 0;
            }
            int frameWidth = safeBitmap.getWidth();
            int frameHeight = safeBitmap.getHeight();

            FrameStats stats = FrameStats.compute(safeBitmap);
            log("FRAME_STATS ts=" + System.currentTimeMillis()
                    + " mean=" + String.format(Locale.US, "%.1f", stats.brightness)
                    + " contrast=" + String.format(Locale.US, "%.1f", stats.contrast)
                    + " sharp=" + String.format(Locale.US, "%.1f", stats.sharpness)
                    + " noise=" + String.format(Locale.US, "%.2f", stats.noise));

            Rect detected = MrzAutoDetector.detect(safeBitmap);
            if (detected == null) {
                detected = buildFallbackRoi(frameWidth, frameHeight);
                notifyFrameProcessed(ScanState.WAITING, MSG_NO_ROI, System.currentTimeMillis());
            }

            Rect stable = rectAverager.update(detected, frameWidth, frameHeight);
            Bitmap roiBmp = Bitmap.createBitmap(safeBitmap, stable.left, stable.top, stable.width(), stable.height());
            log("MRZ ROI size: w=%d h=%d", stable.width(), stable.height());
            log("MRZ line height ~ %d px", stable.height() / 2);

            if (!ocrInFlight.compareAndSet(false, true)) {
                notifyFrameProcessed(ScanState.WAITING, MSG_SKIP_OCR_IN_FLIGHT, System.currentTimeMillis());
                return;
            }

            runOcrAsync(roiBmp, rotationDeg, stable, frameWidth, frameHeight);
        } catch (Throwable e) {
            String message = (e instanceof IllegalStateException) ? e.getMessage() : "Analyzer error while processing frame";
            if (message == null || message.trim().isEmpty()) {
                message = "Analyzer error while processing frame";
            }
            notifyError(message, e);
        } finally {
            image.close();
        }
    }

    private void runOcrAsync(Bitmap roiBmp, int rotationDeg, Rect stable, int frameWidth, int frameHeight) {
        if (mode == DualOcrRunner.Mode.AUTO_DUAL) {
            OcrRouter.runAsync(appContext, mlKitEngine, tessEngine, roiBmp, rotationDeg,
                    cameraId, frameWidth, frameHeight, new OcrRouter.Callback() {
                @Override
                public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                    ocrInFlight.set(false);
                    if (finished.get()) {
                        return;
                    }

                    OcrResult ocr = new OcrResult(
                            result.finalText,
                            result.elapsedMs,
                            result.metrics,
                            result.engine
                    );

                    if (listener != null) {
                        listener.onOcr(ocr, mrz, stable);
                        notifyOcrState(ocr);
                    }

                    if (mrz != null) {
                        if (listener != null) listener.onScanState(ScanState.MRZ_FOUND, "MRZ detected");
                        MrzResult finalMrz = aggregator.addAndMaybeAggregate(mrz);
                        if (finalMrz != null) {
                            finished.set(true);
                            if (listener != null) listener.onFinalMrz(finalMrz, stable);
                        }
                    } else if (listener != null) {
                        listener.onScanState(ScanState.WAITING, "Waiting for MRZ");
                    }
                }

                @Override
                public void onFailure(Throwable error) {
                    ocrInFlight.set(false);
                    String cause = error != null ? error.getMessage() : null;
                    if (cause == null || cause.trim().isEmpty()) {
                        cause = "unknown error";
                    }
                    notifyError("OCR failed: " + cause, error);
                }
            });
            return;
        }

        DualOcrRunner.runAsync(appContext, mode, mlKitEngine, tessEngine, roiBmp, rotationDeg,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult rr) {
                        ocrInFlight.set(false);
                        if (finished.get()) {
                            return;
                        }

                        if (listener != null) {
                            listener.onOcr(rr.ocr, rr.mrz, stable);
                            notifyOcrState(rr.ocr);
                        }

                        if (rr.mrz != null) {
                            if (listener != null) listener.onScanState(ScanState.MRZ_FOUND, "MRZ detected");
                            MrzResult finalMrz = aggregator.addAndMaybeAggregate(rr.mrz);
                            if (finalMrz != null) {
                                finished.set(true);
                                if (listener != null) listener.onFinalMrz(finalMrz, stable);
                            }
                        } else if (listener != null) {
                            listener.onScanState(ScanState.WAITING, "Waiting for MRZ");
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        ocrInFlight.set(false);
                        String cause = error != null ? error.getMessage() : null;
                        if (cause == null || cause.trim().isEmpty()) {
                            cause = "unknown error";
                        }
                        notifyError("OCR failed: " + cause, error);
                    }
                });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        // Converts via YuvBitmapConverter using NV21 + JPEG round-trip for YUV_420_888 frames.
        return yuvBitmapConverter.toBitmap(image);
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

    private void log(String message) {
        Log.d(TAG, message);
    }

    private void log(String format, Object... args) {
        Log.d(TAG, String.format(Locale.US, format, args));
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
