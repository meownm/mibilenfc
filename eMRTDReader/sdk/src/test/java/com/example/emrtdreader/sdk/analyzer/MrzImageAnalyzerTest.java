package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.models.ScanState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.nio.ByteBuffer;
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
    public void analyzeUsesSafeBitmapAfterClose() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);
        OcrEngine mlKit = new FlagOcrEngine(closed, recognizeCalled);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        ImageProxy imageProxy = createImageProxy(closed, 8, 8);
        analyzer.analyze(imageProxy);

        assertTrue(recognizeCalled.get());
        verify(listener).onOcr(any(), any(), any());
        assertTrue(closed.get());
    }

    @Test
    public void analyzeRecoversAfterConversionFailure() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new StubOcrEngine(recognizeCalled),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        AtomicBoolean closed = new AtomicBoolean(false);
        ImageProxy failingProxy = mock(ImageProxy.class);
        ImageInfo info = mock(ImageInfo.class);
        when(failingProxy.getImageInfo()).thenReturn(info);
        when(info.getRotationDegrees()).thenReturn(0);
        when(failingProxy.getPlanes()).thenThrow(new RuntimeException("planes"));
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(failingProxy).close();

        analyzer.analyze(failingProxy);

        verify(listener).onAnalyzerError(eq("Image conversion failed"), any(IllegalStateException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("Image conversion failed"));
        assertTrue(closed.get());

        ImageProxy okProxy = createImageProxy(new AtomicBoolean(false), 8, 8);
        analyzer.analyze(okProxy);

        verify(listener).onOcr(any(), any(), any());
        assertTrue(recognizeCalled.get());
    }

    @Test
    public void analyzeReportsErrorWhenConversionFails() {
        Context context = ApplicationProvider.getApplicationContext();
        OcrEngine mlKit = mock(OcrEngine.class);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getPlanes()).thenThrow(new RuntimeException("conversion"));

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

        verify(listener).onAnalyzerError(eq("Image conversion failed"), any(IllegalStateException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("Image conversion failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeReportsErrorWhenOcrFails() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new ThrowingOcrEngine(),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        AtomicBoolean closed = new AtomicBoolean(false);
        ImageProxy imageProxy = createImageProxy(closed, 8, 8);

        analyzer.analyze(imageProxy);

        verify(listener).onAnalyzerError(eq("OCR failed: ocr failed"), any(IllegalStateException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("OCR failed: ocr failed"));
        assertTrue(closed.get());
    }

    private static ImageProxy createImageProxy(AtomicBoolean closedFlag, int width, int height) {
        int ySize = width * height;
        int uvSize = (width * height) / 4;
        byte[] y = new byte[ySize];
        byte[] u = new byte[uvSize];
        byte[] v = new byte[uvSize];
        for (int i = 0; i < y.length; i++) {
            y[i] = (byte) 120;
        }
        for (int i = 0; i < uvSize; i++) {
            u[i] = (byte) 128;
            v[i] = (byte) 128;
        }

        ImageProxy.PlaneProxy yPlane = mock(ImageProxy.PlaneProxy.class);
        ImageProxy.PlaneProxy uPlane = mock(ImageProxy.PlaneProxy.class);
        ImageProxy.PlaneProxy vPlane = mock(ImageProxy.PlaneProxy.class);

        when(yPlane.getBuffer()).thenReturn(ByteBuffer.wrap(y));
        when(uPlane.getBuffer()).thenReturn(ByteBuffer.wrap(u));
        when(vPlane.getBuffer()).thenReturn(ByteBuffer.wrap(v));

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getWidth()).thenReturn(width);
        when(imageProxy.getHeight()).thenReturn(height);
        when(imageProxy.getPlanes()).thenReturn(new ImageProxy.PlaneProxy[]{yPlane, uPlane, vPlane});
        doAnswer(invocation -> {
            closedFlag.set(true);
            return null;
        }).when(imageProxy).close();

        return imageProxy;
    }

    private static class FlagOcrEngine implements OcrEngine {
        private final AtomicBoolean closedFlag;
        private final AtomicBoolean called;

        private FlagOcrEngine(AtomicBoolean closedFlag, AtomicBoolean called) {
            this.closedFlag = closedFlag;
            this.called = called;
        }

        @Override
        public String getName() {
            return "flag";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            assertTrue(closedFlag.get());
            called.set(true);
            return new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT);
        }

        @Override
        public void close() {
        }
    }

    private static class StubOcrEngine implements OcrEngine {
        private final AtomicBoolean called;

        private StubOcrEngine(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            called.set(true);
            return new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT);
        }

        @Override
        public void close() {
        }
    }

    private static class ThrowingOcrEngine implements OcrEngine {
        @Override
        public String getName() {
            return "throwing";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            throw new RuntimeException("ocr failed");
        }

        @Override
        public void close() {
        }
    }
}
