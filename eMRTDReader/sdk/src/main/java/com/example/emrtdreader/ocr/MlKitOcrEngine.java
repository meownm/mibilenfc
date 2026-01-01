package com.example.emrtdreader.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.models.OcrMetrics;
import com.example.emrtdreader.models.OcrResult;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.TimeUnit;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class MlKitOcrEngine implements OcrEngine {

    private final com.google.mlkit.vision.text.TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private static final long OCR_TIMEOUT_MS = 1200;

    @Override public String getName() { return "ML Kit"; }

    @Override public boolean isAvailable(Context ctx) { return true; }

    @Override
    public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);

        try {
            InputImage img = InputImage.fromBitmap(bitmap, rotationDegrees);
            Text text = Tasks.await(recognizer.process(img), OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long dt = System.currentTimeMillis() - t0;
            return new OcrResult(text.getText(), dt, metrics);
        } catch (Throwable e) {
            long dt = System.currentTimeMillis() - t0;
            return new OcrResult("", dt, metrics);
        }
    }

    @Override public void close() {
        try {
            recognizer.close();
        } catch (Throwable ignored) {
        }
    }
}
