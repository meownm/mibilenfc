package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import com.example.emrtdreader.sdk.models.OcrElement;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MlKitOcrEngine implements OcrEngine {

    private final com.google.mlkit.vision.text.TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private static final long OCR_TIMEOUT_MS = 1200;

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("mlkit-ocr-timeout"));

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override public String getName() { return "ML Kit"; }

    @Override public boolean isAvailable(Context ctx) { return true; }

    @Override
    public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
        if (callback == null) return;

        if (closed.get()) {
            callback.onFailure(new IllegalStateException("ML Kit engine is closed"));
            return;
        }
        if (bitmap == null) {
            callback.onFailure(new IllegalArgumentException("Bitmap is null"));
            return;
        }

        long t0 = System.currentTimeMillis();
        OcrMetrics metrics = OcrQuality.compute(bitmap);

        final InputImage img;
        try {
            img = InputImage.fromBitmap(bitmap, rotationDegrees);
        } catch (Throwable e) {
            callback.onFailure(new IllegalStateException("ML Kit OCR failed: InputImage", e));
            return;
        }

        AtomicBoolean finished = new AtomicBoolean(false);

        ScheduledFuture<?> timeoutFuture = TIMEOUT_EXECUTOR.schedule(() -> {
            if (closed.get()) return;
            if (finished.compareAndSet(false, true)) {
                callback.onFailure(new TimeoutException("ML Kit OCR timed out"));
            }
        }, OCR_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        recognizer.process(img)
                .addOnSuccessListener(text -> {
                    if (closed.get()) return;
                    if (!finished.compareAndSet(false, true)) return;
                    timeoutFuture.cancel(false);

                    long dt = System.currentTimeMillis() - t0;
                    List<OcrElement> elements = new ArrayList<>();
                    if (text != null) {
                        try {
                            for (com.google.mlkit.vision.text.Text.TextBlock b : text.getTextBlocks()) {
                                Rect bb = b.getBoundingBox();
                                elements.add(new OcrElement(
                                        b.getText(),
                                        bb,
                                        OcrElement.Level.BLOCK,
                                        OcrResult.Engine.ML_KIT
                                ));
                                for (com.google.mlkit.vision.text.Text.Line ln : b.getLines()) {
                                    Rect lb = ln.getBoundingBox();
                                    elements.add(new OcrElement(
                                            ln.getText(),
                                            lb,
                                            OcrElement.Level.LINE,
                                            OcrResult.Engine.ML_KIT
                                    ));
                                    for (com.google.mlkit.vision.text.Text.Element el : ln.getElements()) {
                                        Rect eb = el.getBoundingBox();
                                        elements.add(new OcrElement(
                                                el.getText(),
                                                eb,
                                                OcrElement.Level.ELEMENT,
                                                OcrResult.Engine.ML_KIT
                                        ));
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            // Diagnostics only; never fail OCR due to element extraction
                        }
                    }

                    callback.onSuccess(new OcrResult(
                            text != null ? text.getText() : "",
                            dt,
                            metrics,
                            OcrResult.Engine.ML_KIT,
                            elements
                    ));
                })
                .addOnFailureListener(e -> {
                    if (closed.get()) return;
                    if (!finished.compareAndSet(false, true)) return;
                    timeoutFuture.cancel(false);
                    callback.onFailure(new IllegalStateException("ML Kit OCR failed", e));
                })
                .addOnCanceledListener(() -> {
                    if (closed.get()) return;
                    if (!finished.compareAndSet(false, true)) return;
                    timeoutFuture.cancel(false);
                    callback.onFailure(new CancellationException("ML Kit OCR canceled"));
                });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            recognizer.close();
        } catch (Throwable ignored) {}
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
