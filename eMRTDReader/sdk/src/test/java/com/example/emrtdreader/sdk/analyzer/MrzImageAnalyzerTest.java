package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.ImageFormat;
import android.media.Image;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MrzImageAnalyzerTest {
    private static final String TD3_MRZ =
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n" +
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

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
                listener,
                createTestConverter(createSolidBitmap(8, 8, Color.GRAY))
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
    public void analyzeUsesSafeBitmapAfterClose() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);
        AtomicBoolean immutableSeen = new AtomicBoolean(false);
        OcrEngine mlKit = new FlagOcrEngine(closed, recognizeCalled, immutableSeen);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createSolidBitmap(8, 8, Color.GRAY))
        );

        ImageProxy imageProxy = createImageProxy(closed, 8, 8);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        assertTrue(recognizeCalled.get());
        assertTrue(immutableSeen.get());
        verify(listener).onOcr(any(), any(), any());
        assertTrue(closed.get());
    }

    @Test
    public void analyzeLogsFrameOnEveryCallEvenWhenIntervalSkips() throws InterruptedException {
        ShadowLog.clear();
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new FixedOcrEngine(new OcrResult(
                        TD3_MRZ,
                        1,
                        new OcrMetrics(0, 0, 0),
                        OcrResult.Engine.ML_KIT
                )),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                1000,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));
        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        verify(listener, times(1)).onOcr(any(), any(), any());

        List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag("MRZ");
        int frameLogs = 0;
        for (ShadowLog.LogItem item : logs) {
            if (item.msg != null && item.msg.startsWith("FRAME ts=")
                    && item.msg.contains("w=320")
                    && item.msg.contains("h=240")) {
                frameLogs++;
            }
        }
        assertTrue(frameLogs >= 2);
    }

    @Test
    public void analyzeLogsFrameStatsForSyntheticBitmap() throws InterruptedException {
        ShadowLog.clear();
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new FixedOcrEngine(new OcrResult(
                        TD3_MRZ,
                        1,
                        new OcrMetrics(0, 0, 0),
                        OcrResult.Engine.ML_KIT
                )),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createCheckerboardBitmap(32, 32))
        );

        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 32, 32));

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));

        List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag("MRZ");
        boolean foundStats = false;
        for (ShadowLog.LogItem item : logs) {
            if (item.msg != null && item.msg.startsWith("FRAME_STATS")) {
                foundStats = true;
                assertTrue(!item.msg.contains("NaN"));
                assertTrue(item.msg.contains("mean="));
                assertTrue(item.msg.contains("contrast="));
                assertTrue(item.msg.contains("sharp="));
                assertTrue(item.msg.contains("noise="));
                break;
            }
        }
        assertTrue(foundStats);
    }

    @Test
    public void analyzeKeepsOcrWorkingAfterCloseWithRotation() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);
        AtomicBoolean immutableSeen = new AtomicBoolean(false);
        OcrEngine mlKit = new FlagOcrEngine(closed, recognizeCalled, immutableSeen);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createSolidBitmap(8, 8, Color.GRAY))
        );

        ImageProxy imageProxy = createImageProxy(closed, 8, 8, 90);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        assertTrue(recognizeCalled.get());
        assertTrue(immutableSeen.get());
        verify(listener).onOcr(any(), any(), any());
        assertTrue(closed.get());
    }

    @Test
    public void analyzePassesImmutableBitmapToOcr() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean immutableSeen = new AtomicBoolean(false);
        OcrEngine mlKit = new ImmutableOcrEngine(immutableSeen);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        ImageProxy imageProxy = createImageProxy(new AtomicBoolean(false), 320, 240);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        assertTrue(immutableSeen.get());
        verify(listener).onOcr(any(), any(), any());
    }

    @Test
    public void analyzeRecoversAfterConversionFailure() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        Bitmap okFrame = createSolidBitmap(8, 8, Color.GRAY);
        YuvBitmapConverter flakyConverter = new YuvBitmapConverter((image, bitmap) -> {
            if (failOnce.getAndSet(false)) {
                throw new RuntimeException("conversion");
            }
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(okFrame, 0, 0, null);
        });
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new StubOcrEngine(recognizeCalled),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                flakyConverter
        );

        AtomicBoolean closed = new AtomicBoolean(false);
        ImageProxy failingProxy = mock(ImageProxy.class);
        ImageInfo info = mock(ImageInfo.class);
        when(failingProxy.getImageInfo()).thenReturn(info);
        when(info.getRotationDegrees()).thenReturn(0);
        when(failingProxy.getWidth()).thenReturn(8);
        when(failingProxy.getHeight()).thenReturn(8);
        when(failingProxy.getImage()).thenReturn(mock(Image.class));
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

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
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
        when(imageProxy.getWidth()).thenReturn(8);
        when(imageProxy.getHeight()).thenReturn(8);
        when(imageProxy.getImage()).thenReturn(mock(Image.class));

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
                listener,
                createFailingConverter()
        );

        analyzer.analyze(imageProxy);

        verify(listener).onAnalyzerError(eq("Image conversion failed"), any(IllegalStateException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("Image conversion failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeReportsErrorWhenOcrFails() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch errorLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            errorLatch.countDown();
            return null;
        }).when(listener).onAnalyzerError(eq("OCR failed: ocr failed"), any(IllegalStateException.class));

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                new ThrowingOcrEngine(),
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createSolidBitmap(8, 8, Color.GRAY))
        );

        AtomicBoolean closed = new AtomicBoolean(false);
        ImageProxy imageProxy = createImageProxy(closed, 8, 8);

        analyzer.analyze(imageProxy);

        assertTrue(errorLatch.await(2, TimeUnit.SECONDS));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("OCR failed: ocr failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeEmitsMlTextAndMrzFoundOnSuccess() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                TD3_MRZ,
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        ImageProxy imageProxy = createImageProxy(new AtomicBoolean(false), 320, 240);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));

        InOrder inOrder = org.mockito.Mockito.inOrder(listener);
        inOrder.verify(listener).onOcr(any(), any(), any());
        inOrder.verify(listener).onScanState(eq(ScanState.ML_TEXT_FOUND), eq("ML Kit OCR text detected"));
        inOrder.verify(listener).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
    }

    @Test
    public void analyzeEmitsTessTextFoundOnFallback() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "",
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        OcrEngine tess = new FixedOcrEngine(new OcrResult(
                TD3_MRZ,
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.TESSERACT
        ));

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.AUTO_DUAL,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        ImageProxy imageProxy = createImageProxy(new AtomicBoolean(false), 320, 240);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));

        InOrder inOrder = org.mockito.Mockito.inOrder(listener);
        inOrder.verify(listener).onOcr(any(), any(), any());
        inOrder.verify(listener).onScanState(eq(ScanState.TESS_TEXT_FOUND), eq("Tesseract OCR text detected"));
        inOrder.verify(listener).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
    }

    @Test
    public void analyzeAutoDualSkipsTessWhenMlValid() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                TD3_MRZ,
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        AtomicBoolean tessCalled = new AtomicBoolean(false);
        OcrEngine tess = new FlagOnlyOcrEngine(tessCalled);

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                tess,
                DualOcrRunner.Mode.AUTO_DUAL,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        ImageProxy imageProxy = createImageProxy(new AtomicBoolean(false), 320, 240);
        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        assertTrue(!tessCalled.get());
    }

    @Test
    public void analyzeDetectsMrzAcrossConvertedFrames() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch finalLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalLatch.countDown();
            return null;
        }).when(listener).onFinalMrz(any(), any());
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                TD3_MRZ,
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));
        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));
        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));

        assertTrue(finalLatch.await(3, TimeUnit.SECONDS));
        verify(listener, atLeastOnce()).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
        verify(listener).onFinalMrz(any(), any());
    }

    @Test
    public void analyzeDetectsMrzAfterBrightnessNormalization() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        AtomicBoolean ocrCalled = new AtomicBoolean(false);
        OcrEngine mlKit = new ReadableOcrEngine(ocrCalled);
        CountDownLatch finalLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalLatch.countDown();
            return null;
        }).when(listener).onFinalMrz(any(), any());

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createDarkMrzSampleBitmap(320, 240))
        );

        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));
        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));
        analyzer.analyze(createImageProxy(new AtomicBoolean(false), 320, 240));

        assertTrue(finalLatch.await(3, TimeUnit.SECONDS));
        assertTrue(ocrCalled.get());
        verify(listener, atLeastOnce()).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
        verify(listener).onFinalMrz(any(), any());
    }

    @Test
    public void analyzeUsesDefaultConverterWithYuvImageProxy() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                TD3_MRZ,
                1,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));

        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener
        );

        int width = 320;
        int height = 240;
        byte[] yPlane = createMrzLumaPlane(width, height);
        byte[] uPlane = createChromaPlane(width, height, (byte) 128);
        byte[] vPlane = createChromaPlane(width, height, (byte) 128);

        AtomicBoolean closed = new AtomicBoolean(false);
        ImageProxy imageProxy = createYuvImageProxy(closed, width, height, yPlane, uPlane, vPlane);

        analyzer.analyze(imageProxy);

        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
        verify(listener).onOcr(any(), any(), any());
        assertTrue(closed.get());
    }

    @Test
    public void analyzeDoesNotBlockWhileOcrRunsAsync() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        CountDownLatch ocrLatch = new CountDownLatch(1);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
        doAnswer(invocation -> {
            ocrLatch.countDown();
            return null;
        }).when(listener).onOcr(any(), any(), any());

        OcrEngine mlKit = new DelayedOcrEngine(startLatch, releaseLatch);
        MrzImageAnalyzer analyzer = new MrzImageAnalyzer(
                context,
                mlKit,
                mock(OcrEngine.class),
                DualOcrRunner.Mode.MLKIT_ONLY,
                0,
                listener,
                createTestConverter(createMrzSampleBitmap(320, 240))
        );

        AtomicBoolean closedFirst = new AtomicBoolean(false);
        ImageProxy first = createImageProxy(closedFirst, 320, 240);

        Thread analyzeThread = new Thread(() -> analyzer.analyze(first));
        analyzeThread.start();

        assertTrue(startLatch.await(2, TimeUnit.SECONDS));
        analyzeThread.join(500);
        assertTrue(!analyzeThread.isAlive());
        assertTrue(closedFirst.get());

        AtomicBoolean closedSecond = new AtomicBoolean(false);
        analyzer.analyze(createImageProxy(closedSecond, 320, 240));
        assertTrue(closedSecond.get());

        releaseLatch.countDown();
        assertTrue(ocrLatch.await(2, TimeUnit.SECONDS));
    }

    private static ImageProxy createImageProxy(AtomicBoolean closedFlag, int width, int height) {
        return createImageProxy(closedFlag, width, height, 0);
    }

    private static ImageProxy createImageProxy(AtomicBoolean closedFlag, int width, int height, int rotationDegrees) {
        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(rotationDegrees);
        when(imageProxy.getWidth()).thenReturn(width);
        when(imageProxy.getHeight()).thenReturn(height);
        when(imageProxy.getImage()).thenReturn(mock(Image.class));
        doAnswer(invocation -> {
            closedFlag.set(true);
            return null;
        }).when(imageProxy).close();

        return imageProxy;
    }

    private static ImageProxy createYuvImageProxy(AtomicBoolean closedFlag,
                                                  int width,
                                                  int height,
                                                  byte[] yPlane,
                                                  byte[] uPlane,
                                                  byte[] vPlane) {
        Image image = mock(Image.class);
        when(image.getWidth()).thenReturn(width);
        when(image.getHeight()).thenReturn(height);
        when(image.getFormat()).thenReturn(ImageFormat.YUV_420_888);

        Image.Plane y = mock(Image.Plane.class);
        when(y.getBuffer()).thenReturn(ByteBuffer.wrap(yPlane));
        when(y.getRowStride()).thenReturn(width);
        when(y.getPixelStride()).thenReturn(1);

        int chromaRowStride = width / 2;
        Image.Plane u = mock(Image.Plane.class);
        when(u.getBuffer()).thenReturn(ByteBuffer.wrap(uPlane));
        when(u.getRowStride()).thenReturn(chromaRowStride);
        when(u.getPixelStride()).thenReturn(1);

        Image.Plane v = mock(Image.Plane.class);
        when(v.getBuffer()).thenReturn(ByteBuffer.wrap(vPlane));
        when(v.getRowStride()).thenReturn(chromaRowStride);
        when(v.getPixelStride()).thenReturn(1);

        when(image.getPlanes()).thenReturn(new Image.Plane[]{y, u, v});

        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getWidth()).thenReturn(width);
        when(imageProxy.getHeight()).thenReturn(height);
        when(imageProxy.getImage()).thenReturn(image);
        doAnswer(invocation -> {
            closedFlag.set(true);
            return null;
        }).when(imageProxy).close();

        return imageProxy;
    }

    private static YuvBitmapConverter createTestConverter(Bitmap source) {
        return new YuvBitmapConverter((image, bitmap) -> {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(source, 0, 0, null);
        });
    }

    private static YuvBitmapConverter createFailingConverter() {
        return new YuvBitmapConverter((image, bitmap) -> {
            throw new RuntimeException("conversion");
        });
    }

    private static Bitmap createSolidBitmap(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static Bitmap createMrzSampleBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bandTop = (int) (height * 0.7f);
        int bandBottom = (int) (height * 0.9f);
        for (int x = 0; x < width; x += 6) {
            paint.setColor((x / 6) % 2 == 0 ? Color.BLACK : Color.DKGRAY);
            canvas.drawRect(x, bandTop, x + 3, bandBottom, paint);
        }
        return bitmap;
    }

    private static Bitmap createCheckerboardBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean even = ((x + y) % 2 == 0);
                bitmap.setPixel(x, y, even ? Color.WHITE : Color.BLACK);
            }
        }
        return bitmap;
    }

    private static Bitmap createDarkMrzSampleBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(20, 20, 20));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bandTop = (int) (height * 0.7f);
        int bandBottom = (int) (height * 0.9f);
        for (int x = 0; x < width; x += 6) {
            paint.setColor((x / 6) % 2 == 0 ? Color.WHITE : Color.LTGRAY);
            canvas.drawRect(x, bandTop, x + 3, bandBottom, paint);
        }
        return bitmap;
    }

    private static byte[] createMrzLumaPlane(int width, int height) {
        byte[] luma = new byte[width * height];
        int bandTop = (int) (height * 0.7f);
        int bandBottom = (int) (height * 0.9f);
        for (int y = 0; y < height; y++) {
            boolean inBand = y >= bandTop && y < bandBottom;
            for (int x = 0; x < width; x++) {
                int value;
                if (inBand) {
                    value = ((x / 6) % 2 == 0) ? 30 : 230;
                } else {
                    value = 200;
                }
                luma[y * width + x] = (byte) (value & 0xFF);
            }
        }
        return luma;
    }

    private static byte[] createChromaPlane(int width, int height, byte value) {
        int chromaSize = (width / 2) * (height / 2);
        byte[] plane = new byte[chromaSize];
        for (int i = 0; i < chromaSize; i++) {
            plane[i] = value;
        }
        return plane;
    }

    private static class FlagOcrEngine implements OcrEngine {
        private final AtomicBoolean closedFlag;
        private final AtomicBoolean called;
        private final AtomicBoolean immutableSeen;

        private FlagOcrEngine(AtomicBoolean closedFlag, AtomicBoolean called, AtomicBoolean immutableSeen) {
            this.closedFlag = closedFlag;
            this.called = called;
            this.immutableSeen = immutableSeen;
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
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            assertTrue(closedFlag.get());
            assertTrue(!bitmap.isMutable());
            immutableSeen.set(!bitmap.isMutable());
            called.set(true);
            callback.onSuccess(new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT));
        }

        @Override
        public void close() {
        }
    }

    private static class ImmutableOcrEngine implements OcrEngine {
        private final AtomicBoolean immutableSeen;

        private ImmutableOcrEngine(AtomicBoolean immutableSeen) {
            this.immutableSeen = immutableSeen;
        }

        @Override
        public String getName() {
            return "immutable";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            assertTrue(!bitmap.isMutable());
            immutableSeen.set(!bitmap.isMutable());
            callback.onSuccess(new OcrResult(
                    TD3_MRZ,
                    1,
                    new OcrMetrics(0, 0, 0),
                    OcrResult.Engine.ML_KIT
            ));
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
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            called.set(true);
            callback.onSuccess(new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT));
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
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            callback.onFailure(new IllegalStateException("ocr failed"));
        }

        @Override
        public void close() {
        }
    }

    private static class FixedOcrEngine implements OcrEngine {
        private final OcrResult result;

        private FixedOcrEngine(OcrResult result) {
            this.result = result;
        }

        @Override
        public String getName() {
            return "fixed";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            callback.onSuccess(result);
        }

        @Override
        public void close() {
        }
    }

    private static class FlagOnlyOcrEngine implements OcrEngine {
        private final AtomicBoolean called;

        private FlagOnlyOcrEngine(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public String getName() {
            return "flag-only";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            called.set(true);
            callback.onSuccess(new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT));
        }

        @Override
        public void close() {
        }
    }

    private static class ReadableOcrEngine implements OcrEngine {
        private final AtomicBoolean called;

        private ReadableOcrEngine(AtomicBoolean called) {
            this.called = called;
        }

        @Override
        public String getName() {
            return "readable";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            float avg = averageLuma(bitmap);
            float contrast = contrastBetweenRegions(bitmap);
            called.set(true);
            if (avg >= YuvBitmapConverter.MIN_AVG_LUMA
                    && avg <= YuvBitmapConverter.MAX_AVG_LUMA
                    && contrast >= 60f) {
                callback.onSuccess(new OcrResult(
                        TD3_MRZ,
                        1,
                        new OcrMetrics(0, 0, 0),
                        OcrResult.Engine.ML_KIT
                ));
                return;
            }
            callback.onSuccess(new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT));
        }

        @Override
        public void close() {
        }

        private static float averageLuma(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int step = Math.max(1, Math.min(width, height) / 50);
            long sum = 0L;
            long count = 0L;
            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    int color = bitmap.getPixel(x, y);
                    int luma = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                    sum += luma;
                    count++;
                }
            }
            return count == 0L ? 0f : (float) sum / (float) count;
        }

        private static float contrastBetweenRegions(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int bandTop = (int) (height * 0.7f);
            int bandBottom = (int) (height * 0.9f);
            float bandLuma = averageLuma(bitmap, bandTop, bandBottom);
            float backgroundLuma = averageLuma(bitmap, 0, bandTop / 2);
            return Math.abs(bandLuma - backgroundLuma);
        }

        private static float averageLuma(Bitmap bitmap, int top, int bottom) {
            int width = bitmap.getWidth();
            int step = Math.max(1, width / 50);
            long sum = 0L;
            long count = 0L;
            for (int y = top; y < bottom; y += step) {
                for (int x = 0; x < width; x += step) {
                    int color = bitmap.getPixel(x, y);
                    int luma = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3;
                    sum += luma;
                    count++;
                }
            }
            return count == 0L ? 0f : (float) sum / (float) count;
        }
    }

    private static class DelayedOcrEngine implements OcrEngine {
        private final CountDownLatch startLatch;
        private final CountDownLatch releaseLatch;
        private final AtomicInteger started = new AtomicInteger(0);

        private DelayedOcrEngine(CountDownLatch startLatch, CountDownLatch releaseLatch) {
            this.startLatch = startLatch;
            this.releaseLatch = releaseLatch;
        }

        @Override
        public String getName() {
            return "delayed";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            if (started.getAndIncrement() == 0) {
                startLatch.countDown();
            }
            new Thread(() -> {
                try {
                    releaseLatch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                callback.onSuccess(new OcrResult(
                        TD3_MRZ,
                        1,
                        new OcrMetrics(0, 0, 0),
                        OcrResult.Engine.ML_KIT
                ));
            }).start();
        }

        @Override
        public void close() {
        }
    }
}
