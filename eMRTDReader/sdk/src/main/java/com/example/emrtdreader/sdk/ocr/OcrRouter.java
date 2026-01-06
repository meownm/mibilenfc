package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class OcrRouter {
    private static final String TAG = "OcrRouter";

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
        int width = roi != null ? roi.getWidth() : 0;
        int height = roi != null ? roi.getHeight() : 0;
        runAsync(ctx, mlKit, tess, roi, rotationDeg, "default", width, height, callback);
    }

    public static void runAsync(Context ctx,
                                OcrEngine mlKit,
                                OcrEngine tess,
                                Bitmap roi,
                                int rotationDeg,
                                String cameraId,
                                int frameWidth,
                                int frameHeight,
                                Callback callback) {
        if (roi == null) {
            callback.onSuccess(new Result(OcrResult.Engine.UNKNOWN, "", "", "", new OcrMetrics(0, 0, 0), 0L), null);
            return;
        }

        PREPROCESS_EXECUTOR.execute(() -> {
            try {
                Bitmap mlInput = MrzPreprocessor.preprocessForMlMinimal(roi);
                OcrMetrics frameMetrics = OcrQuality.compute(roi);
                runMlThenMaybeTess(ctx, mlKit, tess, roi, mlInput, rotationDeg, frameMetrics,
                        cameraId, frameWidth, frameHeight, callback);
            } catch (Throwable e) {
                callback.onFailure(new IllegalStateException("OCR preprocessing failed", e));
            }
        });
    }

    private static void runMlThenMaybeTess(Context ctx,
                                           OcrEngine mlKit,
                                           OcrEngine tess,
                                           Bitmap roi,
                                           Bitmap mlInput,
                                           int rotationDeg,
                                           OcrMetrics frameMetrics,
                                           String cameraId,
                                           int frameWidth,
                                           int frameHeight,
                                           Callback callback) {
        runEngineAsync(ctx, mlKit, mlInput, rotationDeg, new EngineCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                String mlText = result != null ? result.rawText : "";
                if (hasText(mlText)) {
                    publishResult(result != null ? result.engine : OcrResult.Engine.ML_KIT,
                            mlText,
                            "",
                            mlText,
                            frameMetrics,
                            result != null ? result.elapsedMs : 0L,
                            callback);
                } else {
                    runTessFallback(ctx, tess, roi, rotationDeg, frameMetrics, result, mlText,
                            cameraId, frameWidth, frameHeight, callback);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                runTessFallback(ctx, tess, roi, rotationDeg, frameMetrics, null, "",
                        cameraId, frameWidth, frameHeight, callback);
            }
        });
    }

    private static void runTessFallback(Context ctx,
                                        OcrEngine tess,
                                        Bitmap roi,
                                        int rotationDeg,
                                        OcrMetrics frameMetrics,
                                        OcrResult mlResult,
                                        String mlText,
                                        String cameraId,
                                        int frameWidth,
                                        int frameHeight,
                                        Callback callback) {
        runTessCandidateLoop(ctx, tess, roi, rotationDeg, frameMetrics, mlResult, mlText,
                cameraId, frameWidth, frameHeight, callback);
    }

    private static void runTessCandidateLoop(Context ctx,
                                             OcrEngine tess,
                                             Bitmap roi,
                                             int rotationDeg,
                                             OcrMetrics frameMetrics,
                                             OcrResult mlResult,
                                             String mlText,
                                             String cameraId,
                                             int frameWidth,
                                             int frameHeight,
                                             Callback callback) {
        PreprocessParamStore store = new PreprocessParamStore(ctx);
        PreprocessParams storedParams = store.load(cameraId, frameWidth, frameHeight);
        final java.util.List<PreprocessParams> candidates = buildCandidates(storedParams);
        final java.util.List<OcrResult> results = new java.util.ArrayList<>(candidates.size());
        final java.util.List<String> texts = new java.util.ArrayList<>(candidates.size());
        final java.util.concurrent.atomic.AtomicReference<Throwable> lastError =
                new java.util.concurrent.atomic.AtomicReference<>();

        class Runner {
            void runAt(int index) {
                if (index >= candidates.size()) {
                    int bestIndex = PreprocessParamSelection.pickBestIndex(texts);
                    if (bestIndex < 0) {
                        Throwable error = lastError.get();
                        callback.onFailure(error != null ? error : new IllegalStateException("OCR failed"));
                        return;
                    }
                    PreprocessParams bestParams = candidates.get(bestIndex);
                    store.save(cameraId, frameWidth, frameHeight, bestParams);
                    OcrResult bestResult = results.get(bestIndex);
                    String tessText = texts.get(bestIndex);
                    publishResult(OcrResult.Engine.TESSERACT,
                            mlText,
                            tessText,
                            tessText,
                            frameMetrics,
                            elapsedOrZero(bestResult),
                            callback);
                    return;
                }

                PreprocessParams params = candidates.get(index);
                Bitmap tessInput = MrzPreprocessor.preprocessForTesseract(roi, params);
                runEngineAsync(ctx, tess, tessInput, rotationDeg, new EngineCallback() {
                    @Override
                    public void onSuccess(OcrResult result) {
                        String tessText = result != null ? result.rawText : "";
                        results.add(result);
                        texts.add(tessText);
                        runAt(index + 1);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        lastError.set(error);
                        results.add(null);
                        texts.add("");
                        runAt(index + 1);
                    }
                });
            }
        }

        new Runner().runAt(0);
    }

    private static java.util.List<PreprocessParams> buildCandidates(PreprocessParams stored) {
        java.util.List<PreprocessParams> candidates = new java.util.ArrayList<>();
        if (stored != null) {
            candidates.add(stored);
        }
        for (PreprocessParams params : PreprocessParamSet.getCandidates()) {
            if (!params.equals(stored)) {
                candidates.add(params);
            }
        }
        return candidates;
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
        if (engine == OcrResult.Engine.TESSERACT) {
            mrz = boostConfidenceIfValid(mrz);
        }
        Log.d(TAG, "OCR routed source=" + engine.name()
                + " mlLen=" + safeLength(mlText)
                + " tessLen=" + safeLength(tessText));
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

    private static long elapsedOrZero(OcrResult result) {
        return result != null ? result.elapsedMs : 0L;
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private static MrzResult boostConfidenceIfValid(MrzResult mrz) {
        if (mrz == null) {
            return null;
        }
        if (mrz.confidence < 3) {
            return mrz;
        }
        int boosted = Math.min(4, mrz.confidence + 1);
        if (boosted == mrz.confidence) {
            return mrz;
        }
        return new MrzResult(mrz.line1, mrz.line2, mrz.line3, mrz.format, boosted);
    }

    private static int safeLength(String text) {
        return text != null ? text.length() : 0;
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
