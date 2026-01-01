package com.example.emrtdreader.ocr;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads traineddata to app files dir (/files/tessdata).
 * Uses GitHub tessdata_best by default; you can swap URL to your mirror if needed.
 */
public final class TesseractDataManager {
    private TesseractDataManager() {}

    public static File tessdataDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "tessdata");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File trainedDataFile(Context ctx, String lang) {
        return new File(tessdataDir(ctx), lang + ".traineddata");
    }

    public static boolean ensureTrainedData(Context ctx, String lang) {
        File f = trainedDataFile(ctx, lang);
        if (f.exists() && f.length() > 1024 * 1024) return true; // basic sanity
        return downloadTrainedData(ctx, lang, f);
    }

    private static boolean downloadTrainedData(Context ctx, String lang, File dst) {
        // Default: tessdata_best (better accuracy, bigger). For speed you can use tessdata_fast.
        String url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/" + lang + ".traineddata";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code / 100 != 2) return false;

            File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }

            // atomic-ish replace
            if (dst.exists()) dst.delete();
            return tmp.renameTo(dst);
        } catch (Throwable t) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
