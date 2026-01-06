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
import com.example.emrtdreader.sdk.ocr.MrzAutoDetector;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.ocr.RectAverager;
import com.example.emrtdreader.sdk.utils.MrzBurstAggregator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraX analyzer that:
 * - finds MRZ band automatically (deterministic)
 * - stabilizes ROI across frames
 * - runs OCR (MLKit/Tesseract or dual)
 * - aggregates MRZ across bursts
 */
public class MrzImageAnalyzer implements ImageAnalysis.Analyzer {

    public interface Listener {
        void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi);
        void onFinalMrz(MrzResult finalMrz, Rect roi);
        void onAnalyzerError(String message, Throwable error);
        default void onScanState(ScanState state, String message) {}
    }

    private final Context appContext;
    private final Listener listener;
    private final MrzBurstAggregator aggregator;
    private final RectAverager rectAverager;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final YuvBitmapConverter yuvBitmapConverter;

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
        this(ctx, mlKit, tess, mode, intervalMs, listener, new YuvBitmapConverter(ctx.getApplicationContext()));
    }

    @VisibleForTesting
    MrzImageAnalyzer(Context ctx,
                     OcrEngine mlKit,
                     OcrEngine tess,
                     DualOcrRunner.Mode mode,
                     long intervalMs,
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
        if (finished.get()) {
            image.close();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTs < intervalMs) {
            image.close();
            return;
        }
        lastTs = now;

        boolean closed = false;
        try {
            int rotationDeg = image.getImageInfo().getRotationDegrees();
            Bitmap bitmap = imageProxyToBitmap(image);
            Bitmap safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            image.close();
            closed = true;
            if (safeBitmap == null) {
                throw new IllegalStateException("Bitmap copy failed");
            }

            if (rotationDeg != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotationDeg);
                safeBitmap = Bitmap.createBitmap(safeBitmap, 0, 0, safeBitmap.getWidth(), safeBitmap.getHeight(), m, true);
                rotationDeg = 0;
            }

            Rect detected = MrzAutoDetector.detect(safeBitmap);
            if (detected == null) return;

            Rect stable = rectAverager.update(detected, safeBitmap.getWidth(), safeBitmap.getHeight());
            Bitmap roiBmp = Bitmap.createBitmap(safeBitmap, stable.left, stable.top, stable.width(), stable.height());

            DualOcrRunner.RunResult rr = runOcr(roiBmp, rotationDeg);
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
        } catch (Throwable e) {
            if (!closed) {
                image.close();
            }
            String message = (e instanceof IllegalStateException) ? e.getMessage() : "Analyzer error while processing frame";
            if (message == null || message.trim().isEmpty()) {
                message = "Analyzer error while processing frame";
            }
            notifyError(message, e);
        }
    }

    private DualOcrRunner.RunResult runOcr(Bitmap roiBmp, int rotationDeg) {
        try {
            return DualOcrRunner.run(appContext, mode, mlKitEngine, tessEngine, roiBmp, rotationDeg);
        } catch (Throwable e) {
            String cause = e.getMessage();
            if (cause == null || cause.trim().isEmpty()) {
                cause = "unknown error";
            }
            throw new IllegalStateException("OCR failed: " + cause, e);
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        return yuvBitmapConverter.toBitmap(image);
    }

    private void notifyError(String message, Throwable error) {
        Log.e("MRZ", message, error);
        if (listener != null) {
            listener.onAnalyzerError(message, error);
            listener.onScanState(ScanState.ERROR, message);
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
}
