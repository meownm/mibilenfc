package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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
                new OcrMetrics(10.0, 20.0, 30.0)
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
                new OcrMetrics(10.0, 20.0, 30.0)
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
}
