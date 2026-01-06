package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MrzImageAnalyzerTest {

    @Test
    public void analyzeLogsAndNotifiesOnError() {
        Context context = ApplicationProvider.getApplicationContext();
        OcrEngine mlKit = mock(OcrEngine.class);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenThrow(new RuntimeException("rotation"));

        AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(imageProxy).close();

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.AUTO_DUAL,
                0,
                listener
        );

        analyzer.analyze(imageProxy);

        verify(listener).onAnalyzerError(eq("Analyzer error while processing frame"), any(RuntimeException.class));
        assertTrue(closed.get());

        List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag("MRZ");
        boolean logged = false;
        for (ShadowLog.LogItem item : logs) {
            if (item.msg != null && item.msg.contains("Analyzer error while processing frame")) {
                logged = true;
                break;
            }
        }
        assertTrue(logged);
    }
}
