package com.example.emrtdreader.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.models.MrzFormat;
import com.example.emrtdreader.models.MrzResult;
import com.example.emrtdreader.models.OcrResult;
import com.example.emrtdreader.utils.MrzNormalizer;
import com.example.emrtdreader.utils.MrzRepair;

/**
 * Runs OCR (single or dual) and selects best MRZ by checksum score first, then image quality.
 */
public final class DualOcrRunner {

    public enum Mode { MLKIT_ONLY, TESS_ONLY, AUTO_DUAL }

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
        if (roi == null) return new RunResult(new OcrResult("", 0, OcrQuality.compute(Bitmap.createBitmap(1,1, Bitmap.Config.ARGB_8888))), null);

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
        OcrResult o1 = mlKit.recognize(ctx, input, rotationDeg);
        MrzResult m1 = normalizeAndRepair(o1.rawText);

        OcrResult o2 = tess.recognize(ctx, input, rotationDeg);
        MrzResult m2 = normalizeAndRepair(o2.rawText);

        MrzResult bestMrz = pickBest(m1, m2);
        OcrResult bestOcr = (bestMrz == m2) ? o2 : o1;

        return new RunResult(bestOcr, bestMrz);
    }

    private static MrzResult normalizeAndRepair(String raw) {
        MrzResult m = MrzNormalizer.normalizeBest(raw);
        if (m == null) return null;

        if (m.format == MrzFormat.TD3) {
            // TD3 repair mostly affects line2 numeric/check digits
            String fixedL2 = MrzRepair.repairTd3Line2(m.line2);
            m = new MrzResult(m.line1, fixedL2, null, MrzFormat.TD3, com.example.emrtdreader.utils.MrzValidation.scoreTd3(m.line1, fixedL2));
        } else if (m.format == MrzFormat.TD1 && m.line3 != null) {
            String[] fixed = MrzRepair.repairTd1(m.line1, m.line2, m.line3);
            m = new MrzResult(fixed[0], fixed[1], fixed[2], MrzFormat.TD1, com.example.emrtdreader.utils.MrzValidation.scoreTd1(fixed[0], fixed[1], fixed[2]));
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
}
