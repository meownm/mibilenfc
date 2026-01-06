package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.utils.MrzNormalizer;
import com.example.emrtdreader.sdk.utils.MrzRepair;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs OCR (single or dual) and selects best MRZ by checksum score first, then image quality.
 */
public final class DualOcrRunner {

    public enum Mode { MLKIT_ONLY, TESS_ONLY, AUTO_DUAL }

    private static final long DEFAULT_DUAL_TIMEOUT_MS = 1200L;
    private static final ExecutorService MLKIT_EXECUTOR = Executors.newSingleThreadExecutor(new NamedThreadFactory("mrz-mlkit-ocr"));
    private static final ExecutorService TESS_EXECUTOR = Executors.newSingleThreadExecutor(new NamedThreadFactory("mrz-tess-ocr"));

    public static final class RunResult {
        public final OcrResult ocr;
        public final MrzResult mrz;

        public RunResult(OcrResult ocr, MrzResult mrz) {
            this.ocr = ocr;
            this.mrz = mrz;
        }
    }

    private DualOcrRunner() {}

    public static RunResult run(Context ctx, Mode mode, OcrEngine mlKit, OcrEngine tess, Bitmap roi, int rotationDeg) {
        return runWithTimeout(ctx, mode, mlKit, tess, roi, rotationDeg, DEFAULT_DUAL_TIMEOUT_MS);
    }

    static RunResult runWithTimeout(Context ctx,
                                    Mode mode,
                                    OcrEngine mlKit,
                                    OcrEngine tess,
                                    Bitmap roi,
                                    int rotationDeg,
                                    long dualTimeoutMs) {
        if (roi == null) return new RunResult(emptyOcrResult(), null);

        Bitmap pre = MrzPreprocessor.preprocess(roi);
        Bitmap bin = AdaptiveThreshold.binarize(pre);
        Bitmap input = ThresholdSelector.choose(pre, bin);

        if (mode == Mode.MLKIT_ONLY) {
            OcrResult o = mlKit.recognize(ctx, input, rotationDeg);
            MrzResult m = normalizeAndRepair(o.rawText);
            return new RunResult(o, m);
        }
        if (mode == Mode.TESS_ONLY) {
            OcrResult o = tess.recognize(ctx, input, rotationDeg);
            MrzResult m = normalizeAndRepair(o.rawText);
            return new RunResult(o, m);
        }

        // AUTO: run both and pick best MRZ
        CompletableFuture<OcrOutcome> mlFuture = CompletableFuture
                .supplyAsync(() -> runEngine(ctx, mlKit, input, rotationDeg), MLKIT_EXECUTOR)
                .exceptionally(ex -> new OcrOutcome(null, null, ex));
        CompletableFuture<OcrOutcome> tessFuture = CompletableFuture
                .supplyAsync(() -> runEngine(ctx, tess, input, rotationDeg), TESS_EXECUTOR)
                .exceptionally(ex -> new OcrOutcome(null, null, ex));

        CompletableFuture<Void> all = CompletableFuture.allOf(mlFuture, tessFuture);
        try {
            all.get(dualTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ignored) {
            if (!mlFuture.isDone()) mlFuture.cancel(true);
            if (!tessFuture.isDone()) tessFuture.cancel(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // Individual results are handled per-future.
        }

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

        return new RunResult(bestOutcome != null ? bestOutcome.ocr : emptyOcrResult(), bestMrz);
    }

    private static OcrOutcome runEngine(Context ctx, OcrEngine engine, Bitmap input, int rotationDeg) {
        if (engine == null) return new OcrOutcome(emptyOcrResult(), null, null);
        OcrResult ocr = engine.recognize(ctx, input, rotationDeg);
        MrzResult mrz = normalizeAndRepair(ocr.rawText);
        return new OcrOutcome(ocr, mrz, null);
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
        return new OcrResult("", 0, OcrQuality.compute(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)));
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
