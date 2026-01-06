package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MlKitOcrEngine implements OcrEngine {

    private final com.google.mlkit.vision.text.TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private static final long OCR_TIMEOUT_MS = 1200;
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("mlkit-ocr-timeout"));

    @Override public String getName() { return "ML Kit"; }

    @Override public boolean isAvailable(Context ctx) { return true; }

    @Override
    public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);
        InputImage img;

        try {
            img = InputImage.fromBitmap(bitmap, rotationDegrees);
        } catch (Throwable e) {
            callback.onFailure(new IllegalStateException("ML Kit OCR failed", e));
            return;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        ScheduledFuture<?> timeoutFuture = TIMEOUT_EXECUTOR.schedule(() -> {
            if (finished.compareAndSet(false, true)) {
                callback.onFailure(new TimeoutException("ML Kit OCR timed out"));
            }
        }, OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        recognizer.process(img)
                .addOnSuccessListener(text -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    timeoutFuture.cancel(false);
                    long dt = System.currentTimeMillis() - t0;
                    callback.onSuccess(new OcrResult(text.getText(), dt, metrics, OcrResult.Engine.ML_KIT));
                })
                .addOnFailureListener(e -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    timeoutFuture.cancel(false);
                    callback.onFailure(new IllegalStateException("ML Kit OCR failed", e));
                })
                .addOnCanceledListener(() -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    timeoutFuture.cancel(false);
                    callback.onFailure(new CancellationException("ML Kit OCR canceled"));
                });
    }

    @Override public void close() {
        try {
            recognizer.close();
        } catch (Throwable ignored) {
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
