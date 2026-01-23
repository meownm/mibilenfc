package com.example.emrtdreader.sdk.analyzer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.recognition.MrzRecognitionPipeline;

/**
 * CameraX analyzer wrapper.
 *
 * Responsibilities here are intentionally minimal:
 * - throttle by interval
 * - early-drop frames when OCR is in-flight (before expensive Bitmap conversions)
 * - convert ImageProxy -> Bitmap and rotate to upright
 * - delegate heavy work to MrzRecognitionPipeline
 *
 * All MRZ recognition logic lives in MrzRecognitionPipeline.
 */
public final class MrzImageAnalyzer implements ImageAnalysis.Analyzer {

    private static final String MSG_SKIP_INTERVAL = "Frame skipped: interval";

    public interface Listener {
        void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi);
        void onFinalMrz(MrzResult finalMrz, Rect roi);
        void onAnalyzerError(String message, Throwable error);
        default void onScanState(ScanState state, String message) {}
        default void onFrameProcessed(ScanState state, String message, long timestampMs) {}
    }

    private final Listener listener;
    private final MrzRecognitionPipeline pipeline;

    private long lastTs = 0L;
    private final long intervalMs;

    public MrzImageAnalyzer(Context ctx,
                            OcrEngine mlKit,
                            OcrEngine tess,
                            DualOcrRunner.Mode mode,
                            long intervalMs,
                            Listener listener) {
        this(ctx, mlKit, tess, mode, intervalMs, "default", listener);
    }

    public MrzImageAnalyzer(Context ctx,
                            OcrEngine mlKit,
                            OcrEngine tess,
                            DualOcrRunner.Mode mode,
                            long intervalMs,
                            String cameraId,
                            Listener listener) {
        this.listener = listener;
        this.intervalMs = intervalMs;

        // Adapter keeps the public Listener contract intact
        this.pipeline = new MrzRecognitionPipeline(
                ctx,
                mlKit,
                tess,
                mode,
                new MrzRecognitionPipeline.Listener() {
                    @Override
                    public void onOcr(OcrResult ocr, MrzResult bestSingle, Rect roi) {
                        if (MrzImageAnalyzer.this.listener != null) {
                            MrzImageAnalyzer.this.listener.onOcr(ocr, bestSingle, roi);
                        }
                    }

                    @Override
                    public void onFinalMrz(MrzResult finalMrz, Rect roi) {
                        if (MrzImageAnalyzer.this.listener != null) {
                            MrzImageAnalyzer.this.listener.onFinalMrz(finalMrz, roi);
                        }
                    }

                    @Override
                    public void onAnalyzerError(String message, Throwable error) {
                        if (MrzImageAnalyzer.this.listener != null) {
                            MrzImageAnalyzer.this.listener.onAnalyzerError(message, error);
                        }
                    }

                    @Override
                    public void onScanState(ScanState state, String message) {
                        if (MrzImageAnalyzer.this.listener != null) {
                            MrzImageAnalyzer.this.listener.onScanState(state, message);
                        }
                    }

                    @Override
                    public void onFrameProcessed(ScanState state, String message, long timestampMs) {
                        if (MrzImageAnalyzer.this.listener != null) {
                            MrzImageAnalyzer.this.listener.onFrameProcessed(state, message, timestampMs);
                        }
                    }
                }
        );
    }

    @VisibleForTesting
    void setModeForTests(DualOcrRunner.Mode mode) {
        pipeline.setMode(mode);
    }

    public void setMode(DualOcrRunner.Mode mode) {
        pipeline.setMode(mode);
    }

    public void resetBurst() {
        pipeline.resetBurst();
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        long now = System.currentTimeMillis();
        try {
            if (pipeline.isFinished()) return;

            if (now - lastTs < intervalMs) {
                if (listener != null) {
                    listener.onFrameProcessed(ScanState.WAITING, MSG_SKIP_INTERVAL, now);
                    listener.onScanState(ScanState.WAITING, MSG_SKIP_INTERVAL);
                }
                return;
            }
            lastTs = now;

            // Early drop BEFORE any expensive conversion.
            if (pipeline.shouldSkipBeforeBitmap(now)) {
                return;
            }

            final int rotationDeg = image.getImageInfo().getRotationDegrees();

            Bitmap frame = ImageProxyUtils.toBitmap(image);
            if (frame == null) {
                if (listener != null) {
                    listener.onFrameProcessed(ScanState.WAITING, "Frame->Bitmap failed", now);
                    listener.onScanState(ScanState.WAITING, "Frame->Bitmap failed");
                }
                return;
            }

            Bitmap safe = frame.copy(Bitmap.Config.ARGB_8888, false);
            if (safe == null) {
                if (listener != null) {
                    listener.onFrameProcessed(ScanState.WAITING, "Bitmap copy failed", now);
                    listener.onScanState(ScanState.WAITING, "Bitmap copy failed");
                }
                return;
            }

            final Bitmap upright = (rotationDeg != 0) ? rotateBitmap(safe, rotationDeg) : safe;

            pipeline.submitUprightFrame(upright);

        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg == null || msg.trim().isEmpty()) msg = "Analyzer error while processing frame";
            if (listener != null) {
                listener.onAnalyzerError(msg, e);
                listener.onScanState(ScanState.ERROR, msg);
            }
        } finally {
            image.close();
        }
    }

    private static Bitmap rotateBitmap(Bitmap src, int rotationDeg) {
        try {
            Matrix m = new Matrix();
            m.postRotate(rotationDeg);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable t) {
            return src;
        }
    }
}
