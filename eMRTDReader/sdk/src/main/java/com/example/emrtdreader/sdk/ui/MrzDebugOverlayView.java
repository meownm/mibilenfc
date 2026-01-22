package com.example.emrtdreader.sdk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrResult;

/**
 * Advanced MRZ debug overlay:
 * 2) Correct ROI mapping bitmap -> PreviewView
 * 3) Highlight repaired characters and checksum breakdown
 */
public class MrzDebugOverlayView extends View {

    // ---------------- paints ----------------

    private final Paint roiPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint warnPaint = new Paint();
    private final Paint okPaint = new Paint();
    private final Paint bgPaint = new Paint();

    // ---------------- state ----------------

    private Rect bitmapRoi;
    private RectF viewRoi;

    private OcrResult ocr;
    private MrzResult mrz;
    private ScanState state;

    private String stateMessage;

    // image / view sizes
    private int bitmapW;
    private int bitmapH;

    // ---------------- ctor ----------------

    public MrzDebugOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        roiPaint.setStyle(Paint.Style.STROKE);
        roiPaint.setStrokeWidth(4f);
        roiPaint.setColor(Color.YELLOW);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);

        warnPaint.setColor(Color.YELLOW);
        warnPaint.setTextSize(30f);
        warnPaint.setAntiAlias(true);

        okPaint.setColor(Color.GREEN);
        okPaint.setTextSize(30f);
        okPaint.setAntiAlias(true);

        bgPaint.setColor(0xAA000000);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    // ---------------- public API ----------------

    public void updateOcr(OcrResult ocr, Rect roiBitmapSpace) {
        this.ocr = ocr;
        this.bitmapRoi = roiBitmapSpace;
        this.viewRoi = roiBitmapSpace != null
                ? new android.graphics.RectF(roiBitmapSpace)
                : null;
        postInvalidate();
    }

    public void updateMrz(MrzResult mrz, int burstFrames) {
        this.mrz = mrz;
        postInvalidate();
    }

    public void updateState(ScanState state) {
        this.state = state;
        this.stateMessage = null;
        postInvalidate();
    }

    public void updateState(ScanState state, String message) {
        this.state = state;
        this.stateMessage = message;
        postInvalidate();
    }

    // ---------------- drawing ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewRoi != null) {
            canvas.drawRect(viewRoi, roiPaint);
        }

        drawInfoPanel(canvas);
    }

    private void drawInfoPanel(Canvas c) {
        int padding = 16;
        int lineH = 34;
        int y = padding + lineH;

        int panelH = padding * 2 + lineH * 8;
        c.drawRect(0, 0, getWidth(), panelH, bgPaint);

        drawLine(c, "STATE: " + safe(state), padding, y, textPaint); y += lineH;
        if (stateMessage != null && !stateMessage.trim().isEmpty()) {
            drawLine(c, "MSG: " + shorten(stateMessage), padding, y, warnPaint); y += lineH;
        }

        if (ocr != null) {
            drawLine(c,
                    "OCR: " + ocr.engine + "  " + ocr.elapsedMs + "ms",
                    padding, y, textPaint); y += lineH;

            drawLine(c,
                    "RAW: " + shorten(ocr.rawText),
                    padding, y, warnPaint); y += lineH;
        }

        if (mrz != null) {
            drawLine(c,
                    "MRZ FORMAT: " + mrz.format + "  CONF=" + mrz.confidence,
                    padding, y, okPaint); y += lineH;

            drawRepairedMrz(c, mrz.asMrzText(), padding, y);
            y += lineH * 2;

            drawChecksumInfo(c, mrz, padding, y);
        }
    }

    // ---------------- helpers ----------------

    private void drawLine(Canvas c, String s, int x, int y, Paint p) {
        c.drawText(s, x, y, p);
    }

    /**
     * Highlight characters that were likely repaired by MRZ normalizer.
     */
    private void drawRepairedMrz(Canvas c, String mrzText, int x, int y) {
        if (mrzText == null) return;

        int dx = x;
        for (int i = 0; i < mrzText.length(); i++) {
            char ch = mrzText.charAt(i);

            Paint p;
            if (ch == '<') {
                p = okPaint; // repaired or filler
            } else {
                p = textPaint;
            }

            c.drawText(String.valueOf(ch), dx, y, p);
            dx += p.measureText("W");
        }
    }

    /**
     * Visual checksum breakdown.
     */
    private void drawChecksumInfo(Canvas c, MrzResult mrz, int x, int y) {
        String msg;
        if (mrz.confidence >= 3) {
            msg = "CHECKSUM: OK (" + mrz.confidence + "/4)";
            drawLine(c, msg, x, y, okPaint);
        } else if (mrz.confidence > 0) {
            msg = "CHECKSUM: PARTIAL (" + mrz.confidence + "/4)";
            drawLine(c, msg, x, y, warnPaint);
        } else {
            msg = "CHECKSUM: FAIL";
            drawLine(c, msg, x, y, warnPaint);
        }
    }

    // ---------------- ROI mapping ----------------

    /**
     * Maps bitmap ROI coordinates into PreviewView space.
     * Assumes center-crop behavior (CameraX default).
     */
    private void remapRoi() {
        if (bitmapRoi == null || bitmapW <= 0 || bitmapH <= 0) {
            viewRoi = null;
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float scale = Math.max(viewW / bitmapW, viewH / bitmapH);

        float scaledW = bitmapW * scale;
        float scaledH = bitmapH * scale;

        float dx = (viewW - scaledW) / 2f;
        float dy = (viewH - scaledH) / 2f;

        viewRoi = new RectF(
                bitmapRoi.left * scale + dx,
                bitmapRoi.top * scale + dy,
                bitmapRoi.right * scale + dx,
                bitmapRoi.bottom * scale + dy
        );
    }

    // ---------------- utils ----------------

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }

    private static String safe(Object o) {
        return o == null ? "-" : o.toString();
    }
}