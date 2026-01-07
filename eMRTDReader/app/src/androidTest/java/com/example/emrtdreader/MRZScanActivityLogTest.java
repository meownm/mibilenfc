package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.analysis.ScanState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class MRZScanActivityLogTest {

    @Test
    public void frameLogLineIncludesMrzValidityAndLengths() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                OcrResult ocr = new OcrResult("P<UTO", 120L, new OcrMetrics(100.2, 42.5, 9.1), OcrResult.Engine.ML_KIT);
                MrzResult mrz = new MrzResult("P<UTOERIKSSON<<ANNA<MARIA", "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
                        null, MrzFormat.TD3, 3);
                activity.onOcr(ocr, mrz, new Rect(0, 0, 10, 10));

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                Pattern pattern = Pattern.compile(
                        "\\[frame\\] ts=\\d+ mean=\\d+\\.\\d contrast=\\d+\\.\\d sharp=\\d+\\.\\d engine=ML_KIT mrzValid=true mlLen=\\d+ tessLen=0");
                Matcher matcher = pattern.matcher(logText);
                assertTrue("Frame log line missing or malformed: " + logText, matcher.find());
            });
        }
    }

    @Test
    public void frameLogsAccumulateForMultipleFrames() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                OcrResult first = new OcrResult("", 80L, new OcrMetrics(80.0, 20.0, 5.0), OcrResult.Engine.TESSERACT);
                activity.onOcr(first, null, new Rect(0, 0, 5, 5));
                OcrResult second = new OcrResult("ABC", 90L, new OcrMetrics(70.0, 18.0, 4.0), OcrResult.Engine.ML_KIT);
                activity.onOcr(second, null, new Rect(0, 0, 5, 5));

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                int occurrences = countOccurrences(logText, "[frame]");
                assertEquals("Expected two frame log entries", 2, occurrences);
                assertTrue("Expected mrzValid=false in log output", logText.contains("mrzValid=false"));
            });
        }
    }

    @Test
    public void ocrLinesAppendWithPrefixesInOrder() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                OcrResult mlOcr = new OcrResult("LINE1\nLINE2", 75L, new OcrMetrics(60.0, 14.0, 3.0), OcrResult.Engine.ML_KIT);
                activity.onOcr(mlOcr, null, new Rect(0, 0, 5, 5));
                OcrResult tessOcr = new OcrResult("TESSLINE", 90L, new OcrMetrics(50.0, 10.0, 2.0), OcrResult.Engine.TESSERACT);
                activity.onOcr(tessOcr, null, new Rect(0, 0, 5, 5));

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                int mlLine1 = logText.indexOf("ML: LINE1");
                int mlLine2 = logText.indexOf("ML: LINE2");
                int tessLine = logText.indexOf("TESS: TESSLINE");
                assertTrue("Expected ML prefix for first line", mlLine1 >= 0);
                assertTrue("Expected ML prefix for second line", mlLine2 >= 0);
                assertTrue("Expected TESS prefix for line", tessLine >= 0);
                assertTrue("Expected ML lines before TESS line", mlLine1 < mlLine2 && mlLine2 < tessLine);
            });
        }
    }

    @Test
    public void errorLogLineUsesErrorPrefix() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                activity.onAnalyzerError("camera failure", null);

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                assertTrue("Expected ERROR prefix in log output", logText.contains("ERROR: Analyzer error: camera failure"));
            });
        }
    }

    @Test
    public void scanStateLogLinesAppendWithTimestamps() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                activity.onScanState(ScanState.ML_TEXT_FOUND, null);
                activity.onScanState(ScanState.TESS_TEXT_FOUND, null);
                activity.onScanState(ScanState.WAITING, null);
                activity.onScanState(ScanState.ERROR, "Lens blocked");

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                assertTrue("Expected timestamped scan state entry", logText.contains("[state] ts="));
                assertTrue("Missing ML scan state entry", logText.contains("ML text detected"));
                assertTrue("Missing TESS scan state entry", logText.contains("Tess text detected"));
                assertTrue("Missing WAITING scan state entry", logText.contains("Waiting for MRZ"));
                assertTrue("Missing ERROR scan state entry", logText.contains("Error: Lens blocked"));
                assertTrue(logText.indexOf("ML text detected") < logText.indexOf("Tess text detected"));
                assertTrue(logText.indexOf("Tess text detected") < logText.indexOf("Waiting for MRZ"));
                assertTrue(logText.indexOf("Waiting for MRZ") < logText.indexOf("Error: Lens blocked"));
            });
        }
    }

    @Test
    public void scanStateLogSkipsMrzFoundAndFallsBackToUnknownError() {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                activity.onScanState(ScanState.MRZ_FOUND, null);
                activity.onScanState(ScanState.ERROR, " ");

                TextView logView = activity.findViewById(R.id.logTextView);
                assertNotNull(logView);
                String logText = logView.getText().toString();
                assertEquals("Expected only one scan-state entry", 1, countOccurrences(logText, "[state]"));
                assertTrue("Expected unknown error fallback", logText.contains("Error: Unknown error"));
            });
        }
    }

    private Intent createIntent() {
        Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, MRZScanActivity.class);
        intent.putExtra(MRZScanActivity.EXTRA_DISABLE_CAMERA, true);
        return intent;
    }

    private int countOccurrences(String text, String token) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }
}
