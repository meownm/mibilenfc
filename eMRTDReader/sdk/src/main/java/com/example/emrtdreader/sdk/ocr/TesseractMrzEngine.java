package com.example.emrtdreader.sdk.ocr;

import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrOutput;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.util.Locale;

/**
 * Tesseract-based OCR engine optimized strictly for MRZ.
 *
 * Key properties:
 * - LSTM only (OEM_LSTM_ONLY)
 * - Hard whitelist for MRZ charset
 * - Aggressive blacklist to eliminate quotes and punctuation
 * - Dictionaries disabled
 * - Fixed DPI for stable glyph geometry
 * - SINGLE_BLOCK page segmentation
 */
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

        ensureInitialized();

        Bitmap bitmap = input.bitmap;
        long t0 = System.currentTimeMillis();

        tess.setImage(bitmap);
        String rawText = tess.getUTF8Text();

        long elapsedMs = System.currentTimeMillis() - t0;

        String normalized = normalizeMrzText(rawText);

        return buildOutput(normalized, elapsedMs);
    }

    private void ensureInitialized() {
        if (initialized) return;

        // OEM_LSTM_ONLY = 1 (android TessBaseAPI does not expose the constant)
        tess.init(dataPath, language, 1);

        // Strict MRZ character set
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, MRZ_WHITELIST);

        // Hard blacklist: kill quotes, punctuation, spaces
        tess.setVariable(
                TessBaseAPI.VAR_CHAR_BLACKLIST,
                " \n\r\t\"'`“”‘’«»‹›()[]{}.,:;!/\\|_-"
        );

        // Disable dictionaries completely
        tess.setVariable(VAR_LOAD_SYSTEM_DAWG, "0");
        tess.setVariable(VAR_LOAD_FREQ_DAWG, "0");

        // Penalize non-MRZ guesses
        tess.setVariable("language_model_penalty_non_dict_word", "1");
        tess.setVariable("language_model_penalty_non_freq_dict_word", "1");

        // Force DPI so '<' is treated geometrically, not heuristically
        tess.setVariable("user_defined_dpi", "300");

        // MRZ is a single rectangular block
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);

        initialized = true;
    }

    /**
     * Normalize common OCR confusions before MRZ parsing.
     * This does NOT "guess" fields, only fixes glyph-level noise.
     */
    private static String normalizeMrzText(String text) {
        if (text == null) return "";

        String s = text.toUpperCase(Locale.US);

        // Typography → MRZ filler
        s = s.replace('«', '<')
                .replace('»', '<')
                .replace('‹', '<')
                .replace('›', '<')
                .replace('"', '<')
                .replace(' ', '<');

        // Strip everything outside MRZ alphabet (keep K for contextual fix later)
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') ||
                    c == '<' ||
                    c == 'K') {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static OcrOutput buildOutput(String text, long elapsedMs) {
        String raw = text == null ? "" : text;

        int totalChars = 0;
        int allowedChars = 0;
        int ltCount = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            totalChars++;
            if (MRZ_WHITELIST.indexOf(c) >= 0) {
                allowedChars++;
            }
            if (c == '<') {
                ltCount++;
            }
        }

        float whitelistRatio =
                totalChars == 0 ? 0.0f : (float) allowedChars / (float) totalChars;

        return new OcrOutput(raw, elapsedMs, whitelistRatio, ltCount);
    }
}
