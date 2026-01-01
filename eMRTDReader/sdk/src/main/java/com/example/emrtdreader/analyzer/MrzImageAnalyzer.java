package com.example.emrtdreader.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.emrtdreader.models.MrzResult;
import com.example.emrtdreader.models.OcrResult;
import com.example.emrtdreader.ocr.DualOcrRunner;
import com.example.emrtdreader.ocr.MrzAutoDetector;
import com.example.emrtdreader.ocr.OcrEngine;
import com.example.emrtdreader.ocr.RectAverager;
import com.example.emrtdreader.utils.MrzBurstAggregator;

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
        int rotationDeg = image.getImageInfo().getRotationDegrees();
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

        try {
            Bitmap frame = imageProxyToBitmap(image);
            if (frame == null) return;

            if (rotationDeg != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotationDeg);
                frame = Bitmap.createBitmap(frame, 0, 0, frame.getWidth(), frame.getHeight(), m, true);
                rotationDeg = 0;
            }

            Rect detected = MrzAutoDetector.detect(frame);
            if (detected == null) return;

            Rect stable = rectAverager.update(detected, frame.getWidth(), frame.getHeight());
            Bitmap roiBmp = Bitmap.createBitmap(frame, stable.left, stable.top, stable.width(), stable.height());

            DualOcrRunner.RunResult rr = DualOcrRunner.run(appContext, mode, mlKitEngine, tessEngine, roiBmp, rotationDeg);
            if (listener != null) listener.onOcr(rr.ocr, rr.mrz, stable);

            if (rr.mrz != null) {
                MrzResult finalMrz = aggregator.addAndMaybeAggregate(rr.mrz);
                if (finalMrz != null) {
                    finished.set(true);
                    if (listener != null) listener.onFinalMrz(finalMrz, stable);
                }
            }
        } catch (Throwable ignore) {
        } finally {
            image.close();
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
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
