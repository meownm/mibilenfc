package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class TesseractOcrEngine implements OcrEngine {
    private TessBaseAPI tess;
    private static final ExecutorService TESS_EXECUTOR =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("tess-ocr"));

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
    public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
        TESS_EXECUTOR.execute(() -> {
            try {
                callback.onSuccess(recognizeInternal(ctx, bitmap));
            } catch (Throwable e) {
                callback.onFailure(e);
            }
        });
    }

    private synchronized OcrResult recognizeInternal(Context ctx, Bitmap bitmap) {
        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);

        try {
            ensureInit(ctx);
            tess.setImage(bitmap);
            String txt = tess.getUTF8Text();
            long dt = System.currentTimeMillis() - t0;
            return new OcrResult(txt == null ? "" : txt, dt, metrics, OcrResult.Engine.TESSERACT);
        } catch (Throwable e) {
            throw new IllegalStateException("Tesseract OCR failed", e);
        }
    }

    @Override
    public void close() {
        if (tess != null) {
            tess.end();
            tess = null;
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
