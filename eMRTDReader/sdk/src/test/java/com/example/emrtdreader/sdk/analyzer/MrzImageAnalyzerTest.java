package com.example.emrtdreader.sdk.analyzer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.Image;

import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.analysis.ScanState;
import com.example.emrtdreader.sdk.ocr.DualOcrRunner;
import com.example.emrtdreader.sdk.ocr.OcrEngine;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public void analyzeUsesSafeBitmapAfterClose() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean recognizeCalled = new AtomicBoolean(false);
        AtomicBoolean immutableSeen = new AtomicBoolean(false);
        OcrEngine mlKit = new FlagOcrEngine(closed, recognizeCalled, immutableSeen);
        OcrEngine tess = mock(OcrEngine.class);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

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

        assertTrue(recognizeCalled.get());
        assertTrue(immutableSeen.get());
        verify(listener).onOcr(any(), any(), any());
        assertTrue(closed.get());
    }

    @Test
    public void analyzePassesImmutableBitmapToOcr() {
        Context context = ApplicationProvider.getApplicationContext();
        AtomicBoolean immutableSeen = new AtomicBoolean(false);
        OcrEngine mlKit = new ImmutableOcrEngine(immutableSeen);
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

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

        assertTrue(immutableSeen.get());
        verify(listener).onOcr(any(), any(), any());
    }

    @Test
    public void analyzeRecoversAfterConversionFailure() {
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
    public void analyzeReportsErrorWhenOcrFails() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);

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

        verify(listener).onAnalyzerError(eq("OCR failed: ocr failed"), any(IllegalStateException.class));
        verify(listener).onScanState(eq(ScanState.ERROR), eq("OCR failed: ocr failed"));
        assertTrue(closed.get());
    }

    @Test
    public void analyzeEmitsMlTextAndMrzFoundOnSuccess() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
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

        InOrder inOrder = org.mockito.Mockito.inOrder(listener);
        inOrder.verify(listener).onOcr(any(), any(), any());
        inOrder.verify(listener).onScanState(eq(ScanState.ML_TEXT_FOUND), eq("ML Kit OCR text detected"));
        inOrder.verify(listener).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
    }

    @Test
    public void analyzeEmitsTessTextFoundOnFallback() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
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

        InOrder inOrder = org.mockito.Mockito.inOrder(listener);
        inOrder.verify(listener).onOcr(any(), any(), any());
        inOrder.verify(listener).onScanState(eq(ScanState.TESS_TEXT_FOUND), eq("Tesseract OCR text detected"));
        inOrder.verify(listener).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
    }

    @Test
    public void analyzeDetectsMrzAcrossConvertedFrames() {
        Context context = ApplicationProvider.getApplicationContext();
        MrzImageAnalyzer.Listener listener = mock(MrzImageAnalyzer.Listener.class);
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

        verify(listener, atLeastOnce()).onScanState(eq(ScanState.MRZ_FOUND), eq("MRZ detected"));
        verify(listener).onFinalMrz(any(), any());
    }

    private static ImageProxy createImageProxy(AtomicBoolean closedFlag, int width, int height) {
        ImageProxy imageProxy = mock(ImageProxy.class);
        ImageInfo imageInfo = mock(ImageInfo.class);
        when(imageProxy.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getRotationDegrees()).thenReturn(0);
        when(imageProxy.getWidth()).thenReturn(width);
        when(imageProxy.getHeight()).thenReturn(height);
        when(imageProxy.getImage()).thenReturn(mock(Image.class));
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
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            assertTrue(closedFlag.get());
            assertTrue(!bitmap.isMutable());
            immutableSeen.set(!bitmap.isMutable());
            called.set(true);
            return new OcrResult("", 1, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT);
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
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            assertTrue(!bitmap.isMutable());
            immutableSeen.set(!bitmap.isMutable());
            return new OcrResult(
                    TD3_MRZ,
                    1,
                    new OcrMetrics(0, 0, 0),
                    OcrResult.Engine.ML_KIT
            );
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
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            return result;
        }

        @Override
        public void close() {
        }
    }
}
