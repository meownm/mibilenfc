package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.utils.MrzNormalizer;
import com.example.emrtdreader.sdk.utils.MrzRepair;

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
 * Runs OCR (single or dual) and selects best MRZ by checksum score first, then image quality.
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
        Bitmap mlInput = inputs != null ? inputs.mlInput : null;
        Bitmap tessInput = inputs != null ? inputs.tessInput : null;
        if (mode == Mode.MLKIT_ONLY) {
            runSingleAsync(ctx, mlKit, mlInput, rotationDeg, callback);
            return;
        }
        if (mode == Mode.TESS_ONLY) {
            runSingleAsync(ctx, tess, tessInput, rotationDeg, callback);
            return;
        }

        CompletableFuture<OcrOutcome> mlFuture = runEngineAsync(ctx, mlKit, mlInput, rotationDeg);
        CompletableFuture<OcrOutcome> tessFuture = runEngineAsync(ctx, tess, tessInput, rotationDeg);
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
                                       RunCallback callback) {
        runEngineAsync(ctx, engine, input, rotationDeg).whenComplete((outcome, ex) -> {
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

        MrzResult bestMrz = pickBest(mlOutcome != null ? mlOutcome.mrz : null,
                tessOutcome != null ? tessOutcome.mrz : null);
        OcrOutcome bestOutcome = null;
        if (bestMrz != null) {
            if (mlOutcome != null && bestMrz == mlOutcome.mrz) {
                bestOutcome = mlOutcome;
            } else if (tessOutcome != null && bestMrz == tessOutcome.mrz) {
                bestOutcome = tessOutcome;
            }
        }
        if (bestOutcome == null) {
            if (mlOutcome != null && mlOutcome.ocr != null) {
                bestOutcome = mlOutcome;
            } else if (tessOutcome != null && tessOutcome.ocr != null) {
                bestOutcome = tessOutcome;
            }
        }

        if (bestOutcome != null && bestOutcome.ocr != null) {
            callback.onSuccess(new RunResult(bestOutcome.ocr, bestMrz));
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

    private static CompletableFuture<OcrOutcome> runEngineAsync(Context ctx, OcrEngine engine, Bitmap input, int rotationDeg) {
        CompletableFuture<OcrOutcome> future = new CompletableFuture<>();
        if (engine == null) {
            future.complete(new OcrOutcome(emptyOcrResult(), null, null));
            return future;
        }

        try {
            engine.recognizeAsync(ctx, input, rotationDeg, new OcrEngine.Callback() {
                @Override
                public void onSuccess(OcrResult result) {
                    MrzResult mrz = normalizeAndRepair(result != null ? result.rawText : "");
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
        return new PreprocessResult(preprocessForMl(roi), preprocessForTess(roi));
    }

    private static Bitmap preprocessForMl(Bitmap roi) {
        return MrzPreprocessor.preprocess(roi);
    }

    private static Bitmap preprocessForTess(Bitmap roi) {
        Bitmap pre = MrzPreprocessor.preprocess(roi);
        return AdaptiveThreshold.binarize(pre);
    }

    private static MrzResult normalizeAndRepair(String raw) {
        MrzResult m = MrzNormalizer.normalizeBest(raw);
        if (m == null) return null;

        if (m.format == MrzFormat.TD3) {
            // TD3 repair mostly affects line2 numeric/check digits
            String fixedL2 = MrzRepair.repairTd3Line2(m.line2);
            m = new MrzResult(m.line1, fixedL2, null, MrzFormat.TD3, com.example.emrtdreader.sdk.utils.MrzValidation.scoreTd3(m.line1, fixedL2));
        } else if (m.format == MrzFormat.TD1 && m.line3 != null) {
            String[] fixed = MrzRepair.repairTd1(m.line1, m.line2, m.line3);
            m = new MrzResult(fixed[0], fixed[1], fixed[2], MrzFormat.TD1, com.example.emrtdreader.sdk.utils.MrzValidation.scoreTd1(fixed[0], fixed[1], fixed[2]));
        }
        return m;
    }

    private static MrzResult pickBest(MrzResult a, MrzResult b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.confidence != b.confidence) return (a.confidence > b.confidence) ? a : b;
        // tie-breaker: prefer TD3 slightly (passport MRZ), else keep a
        if (a.format != b.format) return (a.format == MrzFormat.TD3) ? a : b;
        return a;
    }

    private static OcrResult emptyOcrResult() {
        return new OcrResult("", 0, OcrQuality.compute(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), OcrResult.Engine.UNKNOWN);
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
