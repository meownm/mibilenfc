package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OCR router:
 * 1) Try ML Kit (fast). If non-empty -> return it (UI feedback), DO NOT run tesseract.
 * 2) If ML empty (or ML fails) -> run Tesseract candidate-loop with MRZ-oriented preprocessing.
 *
 * IMPORTANT POLICY:
 * - MRZ result is derived ONLY from Tesseract output (never from ML Kit).
 *
 * This implementation is aligned with:
 * - OcrEngine (async callback API)
 * - OcrMetrics / OcrResult models
 * - PreprocessParamSet candidate loop expectations (tests)
 */
public final class OcrRouter {

    public static final class Result {
        public final String finalText;
        public final String mlText;
        public final String tessText;

        public final long elapsedMs;
        public final OcrMetrics metrics;
        public final OcrResult.Engine engine;

        public Result(String finalText,
                      String mlText,
                      String tessText,
                      long elapsedMs,
                      OcrMetrics metrics,
                      OcrResult.Engine engine) {
            this.finalText = finalText;
            this.mlText = mlText;
            this.tessText = tessText;
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

    /**
     * Minimal signature used across SDK tests and simple callers.
     */
    public static void runAsync(
            @NonNull Context ctx,
            @NonNull OcrEngine mlKitEngine,
            @NonNull OcrEngine tessEngine,
            @NonNull Bitmap roiBitmap,
            int rotationDeg,
            @NonNull Callback cb
    ) {
        EXEC.execute(() -> runInternal(ctx, mlKitEngine, tessEngine, roiBitmap, rotationDeg, cb));
    }

    // ---------------- internal ----------------

    private static void runInternal(
            @NonNull Context ctx,
            @NonNull OcrEngine mlKitEngine,
            @NonNull OcrEngine tessEngine,
            @NonNull Bitmap roiBitmap,
            int rotationDeg,
            @NonNull Callback cb
    ) {
        final long t0 = SystemClock.elapsedRealtime();

        // ML preprocessing must remain "non-binarized" (tests expect this)
        final Bitmap mlInput = MrzPreprocessor.preprocessForMlMinimal(roiBitmap);

        mlKitEngine.recognizeAsync(ctx, mlInput, rotationDeg, new OcrEngine.Callback() {
            @Override
            public void onSuccess(OcrResult mlRes) {
                final String mlText = safe(mlRes != null ? mlRes.rawText : null);

                // If ML produced any text -> return immediately (NO tesseract call)
                if (!mlText.isBlank()) {
                    long elapsed = SystemClock.elapsedRealtime() - t0;
                    OcrMetrics metrics = (mlRes != null) ? mlRes.metrics : new OcrMetrics(0, 0, 0);

                    cb.onSuccess(
                            new Result(
                                    mlText,
                                    mlText,
                                    "",
                                    elapsed,
                                    metrics,
                                    OcrResult.Engine.ML_KIT
                            ),
                            null
                    );
                    return;
                }

                // ML empty -> fallback to tesseract candidate loop
                runTesseractCandidateLoop(ctx, tessEngine, roiBitmap, rotationDeg, t0, "", cb);
            }

            @Override
            public void onFailure(Throwable error) {
                // ML failures must not kill pipeline -> fallback to tesseract loop
                runTesseractCandidateLoop(ctx, tessEngine, roiBitmap, rotationDeg, t0, "", cb);
            }
        });
    }

    private static void runTesseractCandidateLoop(
            @NonNull Context ctx,
            @NonNull OcrEngine tessEngine,
            @NonNull Bitmap roiBitmap,
            int rotationDeg,
            long t0,
            @NonNull String mlText,
            @NonNull Callback cb
    ) {
        final List<PreprocessParams> candidates = PreprocessParamSet.getCandidates();

        final AtomicReference<BestPick> best = new AtomicReference<>(new BestPick());

        runCandidateAtIndex(
                ctx,
                tessEngine,
                roiBitmap,
                rotationDeg,
                candidates,
                0,
                best,
                () -> {
                    BestPick pick = best.get();
                    String tessText = safe(pick.bestText);

                    if (tessText.isBlank()) {
                        cb.onFailure(new IllegalStateException("Tesseract produced empty text for all candidates"));
                        return;
                    }

                    long elapsed = SystemClock.elapsedRealtime() - t0;
                    OcrMetrics metrics = pick.bestMetrics != null ? pick.bestMetrics : new OcrMetrics(0, 0, 0);

                    cb.onSuccess(
                            new Result(
                                    tessText,
                                    mlText,
                                    tessText,
                                    elapsed,
                                    metrics,
                                    OcrResult.Engine.TESSERACT
                            ),
                            pick.bestMrz
                    );
                },
                cb
        );
    }

    private static void runCandidateAtIndex(
            @NonNull Context ctx,
            @NonNull OcrEngine tessEngine,
            @NonNull Bitmap roiBitmap,
            int rotationDeg,
            @NonNull List<PreprocessParams> candidates,
            int index,
            @NonNull AtomicReference<BestPick> bestRef,
            @NonNull Runnable onDone,
            @NonNull Callback cb
    ) {
        if (index >= candidates.size()) {
            onDone.run();
            return;
        }

        PreprocessParams params = candidates.get(index);

        // Tesseract preprocessing: grayscale/contrast -> blur -> scale -> binarize
        Bitmap tessInput = MrzPreprocessor.preprocessForTesseract(roiBitmap, params);

        tessEngine.recognizeAsync(ctx, tessInput, rotationDeg, new OcrEngine.Callback() {
            @Override
            public void onSuccess(OcrResult tessRes) {
                String text = safe(tessRes != null ? tessRes.rawText : null);

                // Evaluate candidate -> prefer MRZ with higher confidence
                MrzResult mrz = null;
                int mrzConfidence = 0;
                if (!text.isBlank()) {
                    mrz = MrzTextProcessor.normalizeAndRepair(text);
                    if (mrz != null) {
                        mrzConfidence = mrz.confidence;
                    }
                }

                BestPick cur = bestRef.get();
                BestPick next = cur.copy();

                // Primary: higher checksum confidence wins (0..4 TD3 / 0..4 TD1)
                boolean take = false;

                if (mrz != null && cur.bestMrz == null) {
                    take = true;
                } else if (mrz != null && cur.bestMrz != null) {
                    if (mrzConfidence > cur.bestMrz.confidence) {
                        take = true;
                    } else if (mrzConfidence == cur.bestMrz.confidence) {
                        // Tie-break: better MRZ-like score, then longer text
                        int sNew = MrzCandidateValidator.score(text);
                        int sOld = MrzCandidateValidator.score(safe(cur.bestText));
                        if (sNew > sOld) take = true;
                        else if (sNew == sOld && text.length() > safe(cur.bestText).length()) take = true;
                    }
                } else if (mrz == null && cur.bestMrz == null) {
                    // No MRZ yet: choose the most MRZ-like raw by heuristic score
                    int sNew = MrzCandidateValidator.score(text);
                    int sOld = MrzCandidateValidator.score(safe(cur.bestText));
                    if (sNew > sOld) take = true;
                    else if (sNew == sOld && text.length() > safe(cur.bestText).length()) take = true;
                }

                if (take) {
                    next.bestText = text;
                    next.bestMrz = mrz; // may be null
                    next.bestMetrics = (tessRes != null) ? tessRes.metrics : null;
                }

                bestRef.set(next);

                runCandidateAtIndex(ctx, tessEngine, roiBitmap, rotationDeg, candidates, index + 1, bestRef, onDone, cb);
            }

            @Override
            public void onFailure(Throwable error) {
                // If one candidate fails, continue with next one (don’t kill the loop)
                runCandidateAtIndex(ctx, tessEngine, roiBitmap, rotationDeg, candidates, index + 1, bestRef, onDone, cb);
            }
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final class BestPick {
        String bestText = "";
        MrzResult bestMrz = null;
        OcrMetrics bestMetrics = null;

        BestPick copy() {
            BestPick b = new BestPick();
            b.bestText = this.bestText;
            b.bestMrz = this.bestMrz;
            b.bestMetrics = this.bestMetrics;
            return b;
        }
    }
}
