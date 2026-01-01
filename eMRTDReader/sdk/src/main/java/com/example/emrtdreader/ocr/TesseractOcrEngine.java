package com.example.emrtdreader.ocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.example.emrtdreader.models.OcrMetrics;
import com.example.emrtdreader.models.OcrResult;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class TesseractOcrEngine implements OcrEngine {
    private TessBaseAPI tess;

    @Override public String getName() { return "Tesseract"; }

    @Override
    public boolean isAvailable(Context ctx) {
        return true; // will download traineddata lazily on first use
    }

    private synchronized void ensureInit(Context ctx) {
        if (tess != null) return;

        File baseDir = ctx.getFilesDir();
        File tessdata = new File(baseDir, "tessdata");
        if (!tessdata.exists()) tessdata.mkdirs();

        // Try to copy from assets on first run (if provided)
        copyIfMissing(ctx.getAssets(), "tessdata/eng.traineddata", new File(tessdata, "eng.traineddata"));
        copyIfMissing(ctx.getAssets(), "tessdata/ocrb.traineddata", new File(tessdata, "ocrb.traineddata"));

        // If not shipped, download on demand
        TesseractDataManager.ensureTrainedData(ctx, "eng");
        // Optional MRZ-specialized data; if download fails we still proceed with eng
        TesseractDataManager.ensureTrainedData(ctx, "ocrb");

        String lang = new File(tessdata, "ocrb.traineddata").exists() ? "ocrb" : "eng";

        tess = new TessBaseAPI();
        tess.init(baseDir.getAbsolutePath(), lang);
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<");
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
    }

    private void copyIfMissing(AssetManager am, String assetPath, File dst) {
        try {
            if (dst.exists()) return;
            try (InputStream in = am.open(assetPath);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
        } catch (Throwable ignore) {}
    }

    @Override
    public synchronized OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);

        try {
            ensureInit(ctx);
            tess.setImage(bitmap);
            String txt = tess.getUTF8Text();
            long dt = System.currentTimeMillis() - t0;
            return new OcrResult(txt == null ? "" : txt, dt, metrics);
        } catch (Throwable e) {
            long dt = System.currentTimeMillis() - t0;
            return new OcrResult("", dt, metrics);
        }
    }

    @Override
    public void close() {
        if (tess != null) {
            tess.end();
            tess = null;
        }
    }
}
