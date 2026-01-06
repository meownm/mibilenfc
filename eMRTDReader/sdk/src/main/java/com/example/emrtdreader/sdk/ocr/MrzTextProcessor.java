package com.example.emrtdreader.sdk.ocr;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.utils.MrzNormalizer;
import com.example.emrtdreader.sdk.utils.MrzRepair;
import com.example.emrtdreader.sdk.utils.MrzValidation;

public final class MrzTextProcessor {
    private MrzTextProcessor() {}

    public static MrzResult normalizeAndRepair(String raw) {
        MrzResult m = MrzNormalizer.normalizeBest(raw);
        if (m == null) return null;

        if (m.format == MrzFormat.TD3) {
            // TD3 repair mostly affects line2 numeric/check digits
            String fixedL2 = MrzRepair.repairTd3Line2(m.line2);
            return new MrzResult(m.line1, fixedL2, null, MrzFormat.TD3, MrzValidation.scoreTd3(m.line1, fixedL2));
        }
        if (m.format == MrzFormat.TD1 && m.line3 != null) {
            String[] fixed = MrzRepair.repairTd1(m.line1, m.line2, m.line3);
            return new MrzResult(fixed[0], fixed[1], fixed[2], MrzFormat.TD1, MrzValidation.scoreTd1(fixed[0], fixed[1], fixed[2]));
        }
        return m;
    }
}
