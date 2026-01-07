package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.utils.MrzNormalizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Routes OCR calls to ML Kit / Tesseract or both.
 *
 * IMPORTANT POLICY:
 * - ML Kit is allowed to provide "text detected" for UI feedback,
 * - but MRZ result must be derived ONLY from Tesseract output.
 */
public final class OcrRouter {

    public static final class Result {
        public final String finalText;
        public final long elapsedMs;
        public final FrameOcrMetrics metrics;
        public final OcrResult.Engine engine;

        public Result(String finalText, long elapsedMs, FrameOcrMetrics metrics, OcrResult.Engine engine) {
            this.finalText = finalText;
            this.elapsedMs = elapsedMs;
            this.metrics = metrics;
            this.engine = engine;
        }
    }

    public interface Callback {
        void onSuccess(Result result, MrzResult mrz);
        void onFailure(Throwable error);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ocr-router");
        t.setDaemon(true);
        return t;
    });

    private OcrRouter() {}

    public static void runAsync(
            @NonNull Context ctx,
            @NonNull OcrEngine mlKitEngine,
            @NonNull OcrEngine tessEngine,
            @NonNull Bitmap roiBitmap,
            int rotationDeg,
            @NonNull String cameraId,
            int frameWidth,
            int frameHeight,
            @NonNull Callback cb
    ) {
        EXEC.execute(() -> {
            long t0 = SystemClock.elapsedRealtime();

            try {
                // 1) ML Kit pass (fast feedback)
                OcrOutput ml = null;
                try {
                    ml = mlKitEngine.recognize(new PreprocessedMrz(roiBitmap, rotationDeg));
                } catch (Throwable ignore) {
                    // ML Kit failures must not kill pipeline
                }

                // 2) Tesseract pass (authoritative for MRZ)
                OcrOutput tess = tessEngine.recognize(new PreprocessedMrz(roiBitmap, rotationDeg));

                // Choose displayed text (UI): prefer ML Kit if non-empty, else Tesseract
                String mlText = ml != null ? safe(ml.text) : "";
                String tessText = tess != null ? safe(tess.text) : "";

                boolean mlHas = !mlText.isBlank();
                boolean tessHas = !tessText.isBlank();

                String finalText;
                OcrResult.Engine finalEngine;
                if (mlHas) {
                    finalText = mlText;
                    finalEngine = OcrResult.Engine.ML_KIT;
                } else {
                    finalText = tessText;
                    finalEngine = OcrResult.Engine.TESSERACT;
                }

                long elapsed = SystemClock.elapsedRealtime() - t0;

                FrameOcrMetrics metrics = new FrameOcrMetrics(
                        cameraId,
                        frameWidth,
                        frameHeight,
                        roiBitmap.getWidth(),
                        roiBitmap.getHeight(),
                        mlHas,
                        tessHas
                );

                Result result = new Result(finalText, elapsed, metrics, finalEngine);

                // ✅ MRZ ONLY from Tesseract text
                MrzResult mrz = null;
                if (tessHas) {
                    mrz = MrzNormalizer.normalizeBest(tessText);
                }

                cb.onSuccess(result, mrz);

            } catch (Throwable e) {
                cb.onFailure(e);
            }
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Minimal metrics container. Keep it aligned with your existing FrameOcrMetrics
     * (if you already have one, replace this class with your existing implementation).
     */
    public static final class FrameOcrMetrics {
        public final String cameraId;
        public final int frameWidth;
        public final int frameHeight;
        public final int roiWidth;
        public final int roiHeight;
        public final boolean mlHasText;
        public final boolean tessHasText;

        public FrameOcrMetrics(
                String cameraId,
                int frameWidth,
                int frameHeight,
                int roiWidth,
                int roiHeight,
                boolean mlHasText,
                boolean tessHasText
        ) {
            this.cameraId = cameraId;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.roiWidth = roiWidth;
            this.roiHeight = roiHeight;
            this.mlHasText = mlHasText;
            this.tessHasText = tessHasText;
        }
    }
}
