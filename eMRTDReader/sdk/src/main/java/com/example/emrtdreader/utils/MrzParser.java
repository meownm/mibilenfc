package com.example.emrtdreader.utils;

import com.example.emrtdreader.domain.AccessKey;
import com.example.emrtdreader.models.MrzFormat;
import com.example.emrtdreader.models.MrzResult;

public final class MrzParser {
    private MrzParser() {}

    public static AccessKey.Mrz toAccessKey(MrzResult mrz) {
        if (mrz == null) return null;
        if (mrz.format == MrzFormat.TD3) {
            String l2 = mrz.line2;
            String doc = l2.substring(0, 9).replace("<", "");
            String dob = l2.substring(13, 19);
            String doe = l2.substring(21, 27);
            return new AccessKey.Mrz(doc, dob, doe);
        } else {
            String l1 = mrz.line1;
            String l2 = mrz.line2;
            String doc = l1.substring(5, 14).replace("<", "");
            String dob = l2.substring(0, 6);
            String doe = l2.substring(8, 14);
            return new AccessKey.Mrz(doc, dob, doe);
        }
    }
}
