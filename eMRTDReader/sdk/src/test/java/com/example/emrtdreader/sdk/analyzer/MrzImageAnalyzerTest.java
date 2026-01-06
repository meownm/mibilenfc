package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.ScanState;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.nio.ByteBuffer;
import java.util.Arrays;
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
        verify(listener).onScanState(eq(ScanState.ERROR), eq("Analyzer error while processing frame"));
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

    @Test
    public void analyzeNotifiesOnFrameConversionFailure() {
        Context context = ApplicationProvider.getApplicationContext();
        OcrEngine mlKit = mock(OcrEngine.class);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getPlanes()).thenThrow(new RuntimeException("planes"));

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

        verify(listener).onAnalyzerError(eq("Frame conversion failed"), any(RuntimeException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("Frame conversion failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeNotifiesOnOcrFailure() {
        Context context = ApplicationProvider.getApplicationContext();
        OcrEngine mlKit = mock(OcrEngine.class);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        when(mlKit.recognize(any(Context.class), any(Bitmap.class), eq(0)))
                .thenThrow(new RuntimeException("ocr"));

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getWidth()).thenReturn(4);
        when(imageProxy.getHeight()).thenReturn(4);
        when(imageProxy.getPlanes()).thenReturn(createTestPlanes());

        AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(imageProxy).close();

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        analyzer.analyze(imageProxy);

        verify(listener).onAnalyzerError(eq("OCR failed"), any(RuntimeException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("OCR failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeCallsOnOcrWhenFrameValid() {
        Context context = ApplicationProvider.getApplicationContext();
        OcrEngine mlKit = mock(OcrEngine.class);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        OcrResult ocrResult = new OcrResult(
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<10",
                12,
                new OcrMetrics(1, 2, 3),
                OcrResult.Engine.ML_KIT
        );
        when(mlKit.recognize(any(Context.class), any(Bitmap.class), eq(0))).thenReturn(ocrResult);

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getWidth()).thenReturn(4);
        when(imageProxy.getHeight()).thenReturn(4);
        when(imageProxy.getPlanes()).thenReturn(createTestPlanes());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        analyzer.analyze(imageProxy);

        verify(listener).onOcr(eq(ocrResult), any(), any(android.graphics.Rect.class));
        verify(listener, never()).onAnalyzerError(any(String.class), any(Throwable.class));
    }

    private ImageProxy.PlaneProxy[] createTestPlanes() {
        ImageProxy.PlaneProxy yPlane = mock(ImageProxy.PlaneProxy.class);
        ImageProxy.PlaneProxy uPlane = mock(ImageProxy.PlaneProxy.class);
        ImageProxy.PlaneProxy vPlane = mock(ImageProxy.PlaneProxy.class);

        byte[] y = new byte[16];
        byte[] u = new byte[4];
        byte[] v = new byte[4];
        Arrays.fill(u, (byte) 128);
        Arrays.fill(v, (byte) 128);

        when(yPlane.getBuffer()).thenReturn(ByteBuffer.wrap(y));
        when(uPlane.getBuffer()).thenReturn(ByteBuffer.wrap(u));
        when(vPlane.getBuffer()).thenReturn(ByteBuffer.wrap(v));

        return new ImageProxy.PlaneProxy[] { yPlane, uPlane, vPlane };
    }
}
