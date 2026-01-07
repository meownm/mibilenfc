package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MRZScanActivityTest {

    @Test
    public void manualInputShowsErrorOnInvalidFields() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        Button manualButton = activity.findViewById(R.id.manualButton);
        manualButton.performClick();

        EditText docEdit = activity.findViewById(R.id.docNumberEdit);
        EditText dobEdit = activity.findViewById(R.id.dobEdit);
        EditText doeEdit = activity.findViewById(R.id.doeEdit);

        docEdit.setText("");
        dobEdit.setText("123");
        doeEdit.setText("123");

        Button confirmManualButton = activity.findViewById(R.id.confirmManualButton);
        confirmManualButton.performClick();

        assertEquals("Fill doc number + YYMMDD dates", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void manualInputStartsNfcFlowOnValidFields() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        Button manualButton = activity.findViewById(R.id.manualButton);
        manualButton.performClick();

        EditText docEdit = activity.findViewById(R.id.docNumberEdit);
        EditText dobEdit = activity.findViewById(R.id.dobEdit);
        EditText doeEdit = activity.findViewById(R.id.doeEdit);

        docEdit.setText("AB1234567");
        dobEdit.setText("900101");
        doeEdit.setText("300101");

        Button confirmManualButton = activity.findViewById(R.id.confirmManualButton);
        confirmManualButton.performClick();

        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(NFCReadActivity.class.getName(), next.getComponent().getClassName());

        AccessKey.Mrz key = (AccessKey.Mrz) next.getSerializableExtra("accessKey");
        assertNotNull(key);
        assertEquals("AB1234567", key.documentNumber);
        assertEquals("900101", key.dateOfBirthYYMMDD);
        assertEquals("300101", key.dateOfExpiryYYMMDD);
    }

    @Test
    public void continueButtonUsesMrzToStartNfcFlow() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        MrzResult mrz = new MrzResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                null,
                MrzFormat.TD3,
                4
        );

        activity.onFinalMrz(mrz, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        Button continueButton = activity.findViewById(R.id.continueButton);
        continueButton.performClick();

        Intent next = shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(NFCReadActivity.class.getName(), next.getComponent().getClassName());

        AccessKey.Mrz key = (AccessKey.Mrz) next.getSerializableExtra("accessKey");
        assertNotNull(key);
        assertEquals("L898902C3", key.documentNumber);
        assertEquals("740812", key.dateOfBirthYYMMDD);
        assertEquals("120415", key.dateOfExpiryYYMMDD);
        assertTrue(next.hasExtra("mrz"));
    }

    @Test
    public void onOcrShowsStatusWhenMrzMissing() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        OcrResult ocr = new OcrResult(
                "LINE1\nLINE2\nLINE3",
                120,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.ML_KIT
        );

        activity.onOcr(ocr, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView mrzTextView = activity.findViewById(R.id.mrzTextView);
        String text = mrzTextView.getText().toString();
        assertTrue(text.contains("MRZ not detected yet"));
        assertTrue(text.contains("OCR running..."));
        assertTrue(text.contains("LINE1"));
        assertTrue(text.contains("LINE2"));
        assertTrue(!text.contains("LINE3"));
    }

    @Test
    public void onOcrShowsMrzWhenAvailable() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        MrzResult mrz = new MrzResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                null,
                MrzFormat.TD3,
                4
        );
        OcrResult ocr = new OcrResult(
                "RAW\nTEXT",
                80,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.TESSERACT
        );

        activity.onOcr(ocr, mrz, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView mrzTextView = activity.findViewById(R.id.mrzTextView);
        assertEquals(mrz.asMrzText(), mrzTextView.getText().toString());
    }

    @Test
    public void onOcrShowsCameraStatusWhenOcrMissing() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onOcr(null, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView mrzTextView = activity.findViewById(R.id.mrzTextView);
        assertEquals("Camera not delivering frames", mrzTextView.getText().toString());
    }

    @Test
    public void logAppendsOcrAndMrzEntries() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        OcrResult ocr = new OcrResult(
                "LINE1\nLINE2",
                120,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.ML_KIT
        );

        MrzResult mrz = new MrzResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                null,
                MrzFormat.TD3,
                4
        );

        activity.onOcr(ocr, mrz, new Rect(0, 0, 10, 10));
        activity.onFinalMrz(mrz, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("OCR (ML_KIT) 120ms"));
        assertTrue(logText.contains("RAW OCR (ML_KIT):"));
        assertTrue(logText.contains("LINE1"));
        assertTrue(logText.contains("MRZ locked (burst):"));
        assertTrue(logText.contains(mrz.asMrzText()));
    }

    @Test
    public void logAppendsRawOcrTextWhenMrzMissing() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        OcrResult ocr = new OcrResult(
                "LINE1\nLINE2",
                120,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.ML_KIT
        );

        activity.onOcr(ocr, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("RAW OCR (ML_KIT):"));
        assertTrue(logText.contains("LINE1"));
        assertTrue(logText.contains("LINE2"));
    }

    @Test
    public void logAccumulatesAcrossOcrEvents() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        OcrResult first = new OcrResult(
                "FIRST LINE",
                50,
                new OcrMetrics(11.0, 21.0, 31.0),
                OcrResult.Engine.ML_KIT
        );
        OcrResult second = new OcrResult(
                "SECOND LINE",
                70,
                new OcrMetrics(12.0, 22.0, 32.0),
                OcrResult.Engine.TESSERACT
        );

        activity.onOcr(first, null, new Rect(0, 0, 10, 10));
        activity.onOcr(second, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("FIRST LINE"));
        assertTrue(logText.contains("SECOND LINE"));
        assertTrue(logText.indexOf("FIRST LINE") < logText.indexOf("SECOND LINE"));
    }

    @Test
    public void logDoesNotAppendWhenOcrMissing() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onOcr(null, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        assertEquals("", logTextView.getText().toString());
    }

    @Test
    public void logAppendsHeartbeatWhenFrameSkipped() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onFrameProcessed(ScanState.WAITING, "Frame skipped: interval", 12345L);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("[heartbeat]"));
        assertTrue(logText.contains("ts=12345"));
        assertTrue(logText.contains("Frame skipped: interval"));
    }

    @Test
    public void logAppendsHeartbeatWhenNoRoiFound() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onFrameProcessed(ScanState.WAITING, "No MRZ ROI detected", 54321L);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("[heartbeat]"));
        assertTrue(logText.contains("ts=54321"));
        assertTrue(logText.contains("No MRZ ROI detected"));
    }

    @Test
    public void logAppendsScanStateTransitions() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onScanState(ScanState.ML_TEXT_FOUND, null);
        activity.onScanState(ScanState.TESS_TEXT_FOUND, null);
        activity.onScanState(ScanState.WAITING, null);
        activity.onScanState(ScanState.ERROR, "Lens blocked");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        String logText = logTextView.getText().toString();
        assertTrue(logText.contains("ts="));
        assertTrue(logText.contains("ML text detected"));
        assertTrue(logText.contains("Tess text detected"));
        assertTrue(logText.contains("Waiting for MRZ"));
        assertTrue(logText.contains("Error: Lens blocked"));
        assertTrue(logText.indexOf("ML text detected") < logText.indexOf("Tess text detected"));
        assertTrue(logText.indexOf("Tess text detected") < logText.indexOf("Waiting for MRZ"));
        assertTrue(logText.indexOf("Waiting for MRZ") < logText.indexOf("Error: Lens blocked"));
    }

    @Test
    public void logDoesNotAppendOnMrzFoundScanState() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onScanState(ScanState.MRZ_FOUND, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        assertEquals("", logTextView.getText().toString());
    }

    @Test
    public void onAnalyzerErrorShowsToastAndStatus() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        ActivityController<MRZScanActivity> controller = Robolectric.buildActivity(MRZScanActivity.class).setup();
        MRZScanActivity activity = controller.get();

        activity.onAnalyzerError("Frame decode failed", new RuntimeException("boom"));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView mrzTextView = activity.findViewById(R.id.mrzTextView);
        assertEquals("Analyzer error: Frame decode failed", mrzTextView.getText().toString());
        assertEquals("Analyzer error: Frame decode failed", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void overlayTurnsPurpleOnMlKitOcr() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        OcrResult ocr = new OcrResult(
                "LINE1",
                120,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.ML_KIT
        );

        activity.onOcr(ocr, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        int actual = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_mlkit_purple);
        assertEquals(expected, actual);
    }

    @Test
    public void overlayTurnsBlueOnTesseractOcr() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        OcrResult ocr = new OcrResult(
                "LINE1",
                120,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.TESSERACT
        );

        activity.onOcr(ocr, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        int actual = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_tess_blue);
        assertEquals(expected, actual);
    }

    @Test
    public void overlayTurnsGreenOnMrzHit() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        MrzResult mrz = new MrzResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                null,
                MrzFormat.TD3,
                4
        );
        OcrResult ocr = new OcrResult(
                "RAW\nTEXT",
                80,
                new OcrMetrics(10.0, 20.0, 30.0),
                OcrResult.Engine.ML_KIT
        );

        activity.onOcr(ocr, mrz, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        int actual = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_mrz_green);
        assertEquals(expected, actual);
    }

    @Test
    public void overlayTurnsRedOnFrameLoss() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        activity.onOcr(null, null, new Rect(0, 0, 10, 10));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        int actual = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_error_red);
        assertEquals(expected, actual);
    }

    @Test
    public void overlayTurnsRedOnAnalyzerError() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        activity.onAnalyzerError("Frame decode failed", new RuntimeException("boom"));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        TextView logTextView = activity.findViewById(R.id.logTextView);
        assertTrue(logTextView.getText().toString().contains("Analyzer error: Frame decode failed"));

        int actual = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_error_red);
        assertEquals(expected, actual);
    }

    @Test
    public void overlayUsesStrokeAndUpdatesOnScanStateTransitions() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        activity.setOverlayAnimationDisabledForTesting(true);
        activity.onScanState(ScanState.WAITING, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        int waitingStroke = getOverlayStrokeColor(activity);
        int waitingExpected = ContextCompat.getColor(activity, R.color.overlay_waiting_gray);
        assertEquals(waitingExpected, waitingStroke);

        activity.onScanState(ScanState.ML_TEXT_FOUND, null);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        int mlStroke = getOverlayStrokeColor(activity);
        int mlExpected = ContextCompat.getColor(activity, R.color.overlay_mlkit_purple);
        assertEquals(mlExpected, mlStroke);

        activity.onScanState(ScanState.ERROR, "Lens blocked");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        GradientDrawable drawable = (GradientDrawable) activity.findViewById(R.id.analysisOverlayView).getBackground();
        ColorStateList fill = drawable.getColor();
        if (fill != null) {
            assertEquals(android.graphics.Color.TRANSPARENT, fill.getDefaultColor());
        }
        int strokeWidth = getOverlayStrokeWidth(drawable);
        int expectedStrokeWidth = activity.getResources().getDimensionPixelSize(R.dimen.overlay_stroke_width);
        assertEquals(expectedStrokeWidth, strokeWidth);

        int errorStroke = getOverlayStrokeColor(activity);
        int expected = ContextCompat.getColor(activity, R.color.overlay_error_red);
        assertEquals(expected, errorStroke);
    }

    private int getOverlayStrokeColor(MRZScanActivity activity) {
        GradientDrawable drawable = (GradientDrawable) activity.findViewById(R.id.analysisOverlayView).getBackground();
        Object state = ReflectionHelpers.getField(drawable, "mGradientState");
        ColorStateList stroke = ReflectionHelpers.getField(state, "mStrokeColor");
        if (stroke == null) {
            return 0;
        }
        return stroke.getDefaultColor();
    }

    private int getOverlayStrokeWidth(GradientDrawable drawable) {
        Object state = ReflectionHelpers.getField(drawable, "mGradientState");
        Integer strokeWidth = ReflectionHelpers.getField(state, "mStrokeWidth");
        return strokeWidth == null ? 0 : strokeWidth;
    }
}
