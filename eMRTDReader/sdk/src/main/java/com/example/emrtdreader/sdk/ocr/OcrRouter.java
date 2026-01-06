package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class OcrRouter {
    public interface Callback {
        void onSuccess(Result result, MrzResult mrz);
        void onFailure(Throwable error);
    }

    public static final class Result {
        public final OcrResult.Engine engine;
        public final String mlText;
        public final String tessText;
        public final String finalText;
        public final OcrMetrics metrics;
        public final long elapsedMs;

        private Result(OcrResult.Engine engine,
                       String mlText,
                       String tessText,
                       String finalText,
                       OcrMetrics metrics,
                       long elapsedMs) {
            this.engine = engine;
            this.mlText = mlText;
            this.tessText = tessText;
            this.finalText = finalText;
            this.metrics = metrics;
            this.elapsedMs = elapsedMs;
        }
    }

    private static final ExecutorService PREPROCESS_EXECUTOR =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("mrz-ml-first"));

    private OcrRouter() {}

    public static void runAsync(Context ctx,
                                OcrEngine mlKit,
                                OcrEngine tess,
                                Bitmap roi,
                                int rotationDeg,
                                Callback callback) {
        if (roi == null) {
            callback.onSuccess(new Result(OcrResult.Engine.UNKNOWN, "", "", "", new OcrMetrics(0, 0, 0), 0L), null);
            return;
        }

        PREPROCESS_EXECUTOR.execute(() -> {
            try {
                Bitmap mlInput = MrzPreprocessor.preprocess(roi);
                Bitmap tessInput = AdaptiveThreshold.binarize(mlInput);
                OcrMetrics frameMetrics = OcrQuality.compute(roi);
                runMlThenMaybeTess(ctx, mlKit, tess, mlInput, tessInput, rotationDeg, frameMetrics, callback);
            } catch (Throwable e) {
                callback.onFailure(new IllegalStateException("OCR preprocessing failed", e));
            }
        });
    }

    private static void runMlThenMaybeTess(Context ctx,
                                           OcrEngine mlKit,
                                           OcrEngine tess,
                                           Bitmap mlInput,
                                           Bitmap tessInput,
                                           int rotationDeg,
                                           OcrMetrics frameMetrics,
                                           Callback callback) {
        runEngineAsync(ctx, mlKit, mlInput, rotationDeg, new EngineCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                String mlText = result != null ? result.rawText : "";
                if (MrzCandidateValidator.isValid(mlText)) {
                    publishResult(result != null ? result.engine : OcrResult.Engine.ML_KIT,
                            mlText,
                            "",
                            mlText,
                            frameMetrics,
                            result != null ? result.elapsedMs : 0L,
                            callback);
                    return;
                }
                runTessFallback(ctx, tess, tessInput, rotationDeg, frameMetrics, mlText, callback);
            }

            @Override
            public void onFailure(Throwable error) {
                runTessFallback(ctx, tess, tessInput, rotationDeg, frameMetrics, "", callback);
            }
        });
    }

    private static void runTessFallback(Context ctx,
                                        OcrEngine tess,
                                        Bitmap tessInput,
                                        int rotationDeg,
                                        OcrMetrics frameMetrics,
                                        String mlText,
                                        Callback callback) {
        runEngineAsync(ctx, tess, tessInput, rotationDeg, new EngineCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                String tessText = result != null ? result.rawText : "";
                publishResult(result != null ? result.engine : OcrResult.Engine.TESSERACT,
                        mlText,
                        tessText,
                        tessText,
                        frameMetrics,
                        result != null ? result.elapsedMs : 0L,
                        callback);
            }

            @Override
            public void onFailure(Throwable error) {
                callback.onFailure(error != null ? error : new IllegalStateException("OCR failed"));
            }
        });
    }

    private static void publishResult(OcrResult.Engine engine,
                                      String mlText,
                                      String tessText,
                                      String finalText,
                                      OcrMetrics frameMetrics,
                                      long elapsedMs,
                                      Callback callback) {
        Result result = new Result(engine, mlText, tessText, finalText, frameMetrics, elapsedMs);
        MrzResult mrz = MrzTextProcessor.normalizeAndRepair(finalText);
        callback.onSuccess(result, mrz);
    }

    private static void runEngineAsync(Context ctx,
                                       OcrEngine engine,
                                       Bitmap input,
                                       int rotationDeg,
                                       EngineCallback callback) {
        if (engine == null) {
            callback.onFailure(new IllegalStateException("OCR engine unavailable"));
            return;
        }
        try {
            engine.recognizeAsync(ctx, input, rotationDeg, new OcrEngine.Callback() {
                @Override
                public void onSuccess(OcrResult result) {
                    callback.onSuccess(result);
                }

                @Override
                public void onFailure(Throwable error) {
                    callback.onFailure(error);
                }
            });
        } catch (Throwable e) {
            callback.onFailure(e);
        }
    }

    private interface EngineCallback {
        void onSuccess(OcrResult result);
        void onFailure(Throwable error);
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, baseName + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
