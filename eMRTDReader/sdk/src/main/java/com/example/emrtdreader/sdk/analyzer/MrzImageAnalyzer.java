package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.models.ScanState;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.MrzAutoDetector;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.ocr.RectAverager;
import com.example.emrtdreader.sdk.utils.MrzBurstAggregator;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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
        this.appContext = ctx.getApplicationContext();
        this.mlKitEngine = mlKit;
        this.tessEngine = tess;
        this.mode = mode;
        this.intervalMs = intervalMs;
        this.listener = listener;
        this.aggregator = new MrzBurstAggregator(3, 12);
        this.rectAverager = new RectAverager(6, 0.35f);
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
            if (listener != null) listener.onOcr(rr.ocr, rr.mrz, stable);

            if (rr.mrz != null) {
                MrzResult finalMrz = aggregator.addAndMaybeAggregate(rr.mrz);
                if (finalMrz != null) {
                    finished.set(true);
                    if (listener != null) listener.onFinalMrz(finalMrz, stable);
                }
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
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
                byte[] bytes = out.toByteArray();
                Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (decoded == null) {
                    throw new IllegalStateException("Image conversion failed");
                }
                return decoded;
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Image conversion failed", t);
        }
    }

    private void notifyError(String message, Throwable error) {
        Log.e("MRZ", message, error);
        if (listener != null) {
            listener.onAnalyzerError(message, error);
            listener.onScanState(ScanState.ERROR, message);
        }
    }
}
