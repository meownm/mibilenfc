package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrOutput;
import com.googlecode.tesseract.android.TessBaseAPI;

public final class TesseractMrzEngine implements MrzOcrEngine {
    static final String MRZ_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";
    private static final String VAR_LOAD_SYSTEM_DAWG = "load_system_dawg";
    private static final String VAR_LOAD_FREQ_DAWG = "load_freq_dawg";

    private final TessBaseAPI tess;
    private final String dataPath;
    private final String language;
    private boolean initialized;

    public TesseractMrzEngine(String dataPath, String language) {
        this(new TessBaseAPI(), dataPath, language);
    }

    public TesseractMrzEngine(TessBaseAPI tess, String dataPath, String language) {
        if (tess == null) {
            throw new IllegalArgumentException("TessBaseAPI is required");
        }
        if (dataPath == null || dataPath.isBlank()) {
            throw new IllegalArgumentException("dataPath is required");
        }
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language is required");
        }
        this.tess = tess;
        this.dataPath = dataPath;
        this.language = language;
    }

    @Override
    public synchronized OcrOutput recognize(PreprocessedMrz input) {
        if (input == null || input.bitmap == null) {
            throw new IllegalArgumentException("Preprocessed MRZ bitmap is required");
        }
        Bitmap bitmap = input.bitmap;
        long t0 = System.currentTimeMillis();
        ensureInitialized();
        tess.setImage(bitmap);
        String text = tess.getUTF8Text();
        long elapsedMs = System.currentTimeMillis() - t0;
        return buildOutput(text, elapsedMs);
    }

    private void ensureInitialized() {
        if (initialized) return;
        tess.init(dataPath, language, TessBaseAPI.OEM_TESSERACT_ONLY);
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, MRZ_WHITELIST);
        tess.setVariable(VAR_LOAD_SYSTEM_DAWG, "0");
        tess.setVariable(VAR_LOAD_FREQ_DAWG, "0");
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        initialized = true;
    }

    private static OcrOutput buildOutput(String text, long elapsedMs) {
        String raw = text == null ? "" : text;
        int totalChars = 0;
        int allowedChars = 0;
        int ltCount = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            totalChars++;
            if (MRZ_WHITELIST.indexOf(c) >= 0) {
                allowedChars++;
            }
            if (c == '<') {
                ltCount++;
            }
        }
        float whitelistRatio = totalChars == 0 ? 0.0f : (float) allowedChars / (float) totalChars;
        return new OcrOutput(raw, elapsedMs, whitelistRatio, ltCount);
    }
}
