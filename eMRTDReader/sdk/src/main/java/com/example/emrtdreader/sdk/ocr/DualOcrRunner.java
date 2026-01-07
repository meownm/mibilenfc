package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs OCR (single or dual).
 *
 * IMPORTANT POLICY:
 * - MRZ must be derived ONLY from Tesseract output.
 * - ML Kit may be used for UI text feedback but never trusted for MRZ parsing.
 *
 * Selection rules:
 * - Mode.MLKIT_ONLY: returns ML Kit OCR result, mrz=null
 * - Mode.TESS_ONLY: returns Tesseract OCR result + MRZ (if parsed)
 * - Mode.AUTO_DUAL:
 *      - run both engines in parallel
 *      - choose OCR text: prefer ML Kit if it has non-empty text, else use Tesseract
 *      - choose MRZ: from Tesseract only
 */
public final class DualOcrRunner {

    public enum Mode { MLKIT_ONLY, TESS_ONLY, AUTO_DUAL }

    public interface RunCallback {
        void onSuccess(RunResult result);
        void onFailure(Throwable error);
    }

    private static final long DEFAULT_DUAL_TIMEOUT_MS = 1200L;

    private static final ExecutorService PREPROCESS_EXECUTOR =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("mrz-preprocess"));

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("mrz-ocr-timeout"));

    public static final class RunResult {
        public final OcrResult ocr;
        public final MrzResult mrz;

        public RunResult(OcrResult ocr, MrzResult mrz) {
            this.ocr = ocr;
            this.mrz = mrz;
        }
    }

    private DualOcrRunner() {}

    public static void runAsync(Context ctx,
                                Mode mode,
                                OcrEngine mlKit,
                                OcrEngine tess,
                                Bitmap roi,
                                int rotationDeg,
                                RunCallback callback) {
        runAsyncWithTimeout(ctx, mode, mlKit, tess, roi, rotationDeg, DEFAULT_DUAL_TIMEOUT_MS, callback);
    }

    static void runAsyncWithTimeout(Context ctx,
                                    Mode mode,
                                    OcrEngine mlKit,
                                    OcrEngine tess,
                                    Bitmap roi,
                                    int rotationDeg,
                                    long dualTimeoutMs,
                                    RunCallback callback) {
        if (callback == null) return;

        if (roi == null) {
            callback.onSuccess(new RunResult(emptyOcrResult(), null));
            return;
        }

        CompletableFuture
                .supplyAsync(() -> preprocessForEngines(roi), PREPROCESS_EXECUTOR)
                .thenAccept(inputs -> runAsyncInternal(ctx, mode, mlKit, tess, inputs, rotationDeg, dualTimeoutMs, callback))
                .exceptionally(ex -> {
                    callback.onFailure(new IllegalStateException("OCR preprocessing failed", ex));
                    return null;
                });
    }

    private static void runAsyncInternal(Context ctx,
                                         Mode mode,
                                         OcrEngine mlKit,
                                         OcrEngine tess,
                                         PreprocessResult inputs,
                                         int rotationDeg,
                                         long dualTimeoutMs,
                                         RunCallback callback) {
        Bitmap mlInput = (inputs != null) ? inputs.mlInput : null;
        Bitmap tessInput = (inputs != null) ? inputs.tessInput : null;

        if (mode == Mode.MLKIT_ONLY) {
            runSingleAsync(ctx, mlKit, mlInput, rotationDeg, /*mrzFromTessOnly*/ false, callback);
            return;
        }
        if (mode == Mode.TESS_ONLY) {
            runSingleAsync(ctx, tess, tessInput, rotationDeg, /*mrzFromTessOnly*/ true, callback);
            return;
        }

        // AUTO_DUAL
        CompletableFuture<OcrOutcome> mlFuture = runEngineAsync(ctx, mlKit, mlInput, rotationDeg, /*mrzFromTessOnly*/ false);
        CompletableFuture<OcrOutcome> tessFuture = runEngineAsync(ctx, tess, tessInput, rotationDeg, /*mrzFromTessOnly*/ true);

        CompletableFuture<Void> all = CompletableFuture.allOf(mlFuture, tessFuture);
        AtomicBoolean completed = new AtomicBoolean(false);

        ScheduledFuture<?> timeoutFuture = TIMEOUT_EXECUTOR.schedule(() -> {
            if (completed.compareAndSet(false, true)) {
                finalizeAutoResult(mlFuture, tessFuture, callback,
                        new TimeoutException("Dual OCR timed out"));
            }
        }, dualTimeoutMs, TimeUnit.MILLISECONDS);

        all.whenComplete((ignored, ex) -> {
            if (completed.compareAndSet(false, true)) {
                timeoutFuture.cancel(false);
                finalizeAutoResult(mlFuture, tessFuture, callback, ex);
            }
        });
    }

    private static void runSingleAsync(Context ctx,
                                       OcrEngine engine,
                                       Bitmap input,
                                       int rotationDeg,
                                       boolean mrzFromTessOnly,
                                       RunCallback callback) {
        runEngineAsync(ctx, engine, input, rotationDeg, mrzFromTessOnly).whenComplete((outcome, ex) -> {
            if (ex != null) {
                callback.onFailure(ex);
                return;
            }
            if (outcome == null) {
                callback.onFailure(new IllegalStateException("OCR failed"));
                return;
            }
            if (outcome.error != null) {
                callback.onFailure(outcome.error);
                return;
            }
            if (outcome.ocr == null) {
                callback.onFailure(new IllegalStateException("OCR failed"));
                return;
            }
            callback.onSuccess(new RunResult(outcome.ocr, outcome.mrz));
        });
    }

    private static void finalizeAutoResult(CompletableFuture<OcrOutcome> mlFuture,
                                           CompletableFuture<OcrOutcome> tessFuture,
                                           RunCallback callback,
                                           Throwable error) {

        OcrOutcome mlOutcome = mlFuture.isDone() ? mlFuture.getNow(null) : null;
        OcrOutcome tessOutcome = tessFuture.isDone() ? tessFuture.getNow(null) : null;

        // MRZ ONLY from Tesseract
        MrzResult mrz = (tessOutcome != null) ? tessOutcome.mrz : null;

        // Choose OCR text for UI: prefer ML Kit if it has any non-empty text, else Tesseract
        OcrResult chosenOcr = null;

        if (mlOutcome != null && mlOutcome.ocr != null && !isBlank(mlOutcome.ocr.rawText)) {
            chosenOcr = mlOutcome.ocr;
        } else if (tessOutcome != null && tessOutcome.ocr != null && !isBlank(tessOutcome.ocr.rawText)) {
            chosenOcr = tessOutcome.ocr;
        } else if (mlOutcome != null && mlOutcome.ocr != null) {
            chosenOcr = mlOutcome.ocr;
        } else if (tessOutcome != null && tessOutcome.ocr != null) {
            chosenOcr = tessOutcome.ocr;
        }

        if (chosenOcr != null) {
            callback.onSuccess(new RunResult(chosenOcr, mrz));
            return;
        }

        Throwable failure = error;
        if (failure == null) {
            if (mlOutcome != null && mlOutcome.error != null) {
                failure = mlOutcome.error;
            } else if (tessOutcome != null && tessOutcome.error != null) {
                failure = tessOutcome.error;
            }
        }
        if (failure == null) {
            failure = new IllegalStateException("OCR failed");
        }
        callback.onFailure(failure);
    }

    private static CompletableFuture<OcrOutcome> runEngineAsync(Context ctx,
                                                                OcrEngine engine,
                                                                Bitmap input,
                                                                int rotationDeg,
                                                                boolean mrzFromTessOnly) {
        CompletableFuture<OcrOutcome> future = new CompletableFuture<>();

        if (engine == null) {
            future.complete(new OcrOutcome(emptyOcrResult(), null, null));
            return future;
        }

        try {
            engine.recognizeAsync(ctx, input, rotationDeg, new OcrEngine.Callback() {
                @Override
                public void onSuccess(OcrResult result) {
                    // MRZ parse policy: ONLY when caller explicitly wants MRZ (tesseract path)
                    MrzResult mrz = null;
                    if (mrzFromTessOnly) {
                        String raw = (result != null) ? result.rawText : "";
                        mrz = MrzTextProcessor.normalizeAndRepair(raw);
                    }
                    future.complete(new OcrOutcome(result, mrz, null));
                }

                @Override
                public void onFailure(Throwable error) {
                    future.complete(new OcrOutcome(null, null, error));
                }
            });
        } catch (Throwable e) {
            future.complete(new OcrOutcome(null, null, e));
        }

        return future;
    }

    private static PreprocessResult preprocessForEngines(Bitmap roi) {
        // ML Kit: non-binary
        Bitmap ml = MrzPreprocessor.preprocessForMl(roi);

        // Tesseract: binary + scaled via default candidate
        Bitmap tess = MrzPreprocessor.preprocessForTesseract(roi);

        return new PreprocessResult(ml, tess);
    }

    /**
     * Kept for compatibility with existing tests / reflection usage.
     * Not used in current AUTO_DUAL policy (since MRZ is tesseract-only).
     */
    @SuppressWarnings("unused")
    private static MrzResult pickBest(MrzResult a, MrzResult b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.confidence != b.confidence) return (a.confidence > b.confidence) ? a : b;
        // tie-breaker: prefer TD3 slightly (passport MRZ), else keep a
        if (a.format != b.format) return (a.format == MrzFormat.TD3) ? a : b;
        return a;
    }

    private static OcrResult emptyOcrResult() {
        return new OcrResult(
                "",
                0,
                OcrQuality.compute(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)),
                OcrResult.Engine.UNKNOWN
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static final class OcrOutcome {
        private final OcrResult ocr;
        private final MrzResult mrz;
        private final Throwable error;

        private OcrOutcome(OcrResult ocr, MrzResult mrz, Throwable error) {
            this.ocr = ocr;
            this.mrz = mrz;
            this.error = error;
        }
    }

    private static final class PreprocessResult {
        private final Bitmap mlInput;
        private final Bitmap tessInput;

        private PreprocessResult(Bitmap mlInput, Bitmap tessInput) {
            this.mlInput = mlInput;
            this.tessInput = tessInput;
        }
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
