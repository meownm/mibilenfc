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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TesseractOcrEngine implements OcrEngine {

    private static final String MRZ_WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";
    private static final String VAR_LOAD_SYSTEM_DAWG = "load_system_dawg";
    private static final String VAR_LOAD_FREQ_DAWG = "load_freq_dawg";
    private static final String VAR_USER_DEFINED_DPI = "user_defined_dpi";

    private static final ExecutorService TESS_EXECUTOR =
            Executors.newSingleThreadExecutor(new NamedThreadFactory("tess-ocr"));

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Object lock = new Object();
    private TessBaseAPI tess;

    @Override public String getName() { return "Tesseract"; }

    @Override
    public boolean isAvailable(Context ctx) {
        return true; // availability depends on traineddata presence; handled on init
    }

    @Override
    public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
        if (callback == null) return;

        if (closed.get()) {
            callback.onFailure(new IllegalStateException("Tesseract engine is closed"));
            return;
        }
        if (bitmap == null) {
            callback.onFailure(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        TESS_EXECUTOR.execute(() -> {
            if (closed.get()) return;
            try {
                callback.onSuccess(recognizeInternal(ctx, bitmap));
            } catch (Throwable e) {
                if (!closed.get()) callback.onFailure(e);
            }
        });
    }

    private OcrResult recognizeInternal(Context ctx, Bitmap bitmap) {
        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);

        ensureInit(ctx);

        synchronized (lock) {
            if (tess == null) {
                throw new IllegalStateException("Tesseract init failed (tess is null)");
            }
            try {
                tess.setImage(bitmap);
                String txt = tess.getUTF8Text();
                long dt = System.currentTimeMillis() - t0;
                return new OcrResult(txt == null ? "" : txt, dt, metrics, OcrResult.Engine.TESSERACT);
            } catch (Throwable e) {
                throw new IllegalStateException("Tesseract OCR failed", e);
            }
        }
    }

    private void ensureInit(Context ctx) {
        synchronized (lock) {
            if (tess != null) return;

            File baseDir = ctx.getFilesDir();
            File tessdataDir = new File(baseDir, "tessdata");
            if (!tessdataDir.exists()) tessdataDir.mkdirs();

            // 1) Try copy from assets if provided (optional)
            copyIfMissing(ctx.getAssets(), "tessdata/eng.traineddata", new File(tessdataDir, "eng.traineddata"));
            copyIfMissing(ctx.getAssets(), "tessdata/ocrb.traineddata", new File(tessdataDir, "ocrb.traineddata"));

            // 2) Try download (may fail on some networks)
            TesseractDataManager.ensureTrainedData(ctx, "eng");
            TesseractDataManager.ensureTrainedData(ctx, "ocrb");

            File ocrb = new File(tessdataDir, "ocrb.traineddata");
            File eng = new File(tessdataDir, "eng.traineddata");

            String lang;
            if (ocrb.exists() && ocrb.length() > 1024 * 1024) {
                lang = "ocrb";
            } else if (eng.exists() && eng.length() > 1024 * 1024) {
                lang = "eng";
            } else {
                throw new IllegalStateException(
                        "No traineddata found. Put eng.traineddata (or ocrb.traineddata) into " +
                                tessdataDir.getAbsolutePath()
                );
            }

            TessBaseAPI api = new TessBaseAPI();
            boolean ok;
            try {
                ok = api.init(baseDir.getAbsolutePath(), lang, 1);
            } catch (Throwable e) {
                throw new IllegalStateException("Tesseract init failed", e);
            }

            if (!ok) {
                throw new IllegalStateException("Tesseract init returned false");
            }

            // MRZ tuning
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, MRZ_WHITELIST);
            api.setVariable(VAR_LOAD_SYSTEM_DAWG, "0");
            api.setVariable(VAR_LOAD_FREQ_DAWG, "0");
            api.setVariable(VAR_USER_DEFINED_DPI, "300");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);

            tess = api;
        }
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
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        synchronized (lock) {
            if (tess != null) {
                try {
                    tess.end();
                } catch (Throwable ignored) {}
                tess = null;
            }
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
