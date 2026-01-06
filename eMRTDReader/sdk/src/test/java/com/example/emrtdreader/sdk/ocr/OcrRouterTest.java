package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OcrRouterTest {
    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final String TD3_VALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2;

    @Test
    public void usesMlKitWhenNonEmpty() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "INVALID",
                12,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        AtomicBoolean tessCalled = new AtomicBoolean(false);
        OcrEngine tess = new FlagOcrEngine(tessCalled, OcrResult.Engine.TESSERACT);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OcrRouter.Result> resultRef = new AtomicReference<>();
        AtomicReference<MrzResult> mrzRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        OcrRouter.runAsync(context, mlKit, tess, bitmap, 0, new OcrRouter.Callback() {
            @Override
            public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                resultRef.set(result);
                mrzRef.set(mrz);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(resultRef.get());
        assertEquals(OcrResult.Engine.ML_KIT, resultRef.get().engine);
        assertEquals("INVALID", resultRef.get().finalText);
        assertEquals("INVALID", resultRef.get().mlText);
        assertTrue(!tessCalled.get());
        assertNull(mrzRef.get());
    }

    @Test
    public void fallsBackToTesseractWhenMlEmpty() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "",
                6,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        AtomicBoolean tessCalled = new AtomicBoolean(false);
        OcrEngine tess = new FlagOcrEngine(tessCalled, OcrResult.Engine.TESSERACT, TD3_VALID_RAW);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OcrRouter.Result> resultRef = new AtomicReference<>();
        AtomicReference<MrzResult> mrzRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        OcrRouter.runAsync(context, mlKit, tess, bitmap, 0, new OcrRouter.Callback() {
            @Override
            public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                resultRef.set(result);
                mrzRef.set(mrz);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(resultRef.get());
        assertTrue(tessCalled.get());
        assertEquals(OcrResult.Engine.TESSERACT, resultRef.get().engine);
        assertEquals(TD3_VALID_RAW, resultRef.get().finalText);
        assertEquals("", resultRef.get().mlText);
        assertEquals(TD3_VALID_RAW, resultRef.get().tessText);
        assertNotNull(mrzRef.get());
    }

    @Test
    public void reportsFailureWhenFallbackFails() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "",
                6,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        OcrEngine tess = new FailingOcrEngine();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        OcrRouter.runAsync(context, mlKit, tess, bitmap, 0, new OcrRouter.Callback() {
            @Override
            public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
    }

    @Test
    public void fallbackUsesSplitPreprocessPipelines() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = createGradientBitmap(8, 8);
        CapturingOcrEngine mlKit = new CapturingOcrEngine(new OcrResult(
                "",
                6,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        CapturingOcrEngine tess = new CapturingOcrEngine(new OcrResult(
                TD3_VALID_RAW,
                8,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.TESSERACT
        ));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OcrRouter.Result> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        OcrRouter.runAsync(context, mlKit, tess, bitmap, 0, new OcrRouter.Callback() {
            @Override
            public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(resultRef.get());
        assertEquals(OcrResult.Engine.TESSERACT, resultRef.get().engine);
        Bitmap mlBitmap = mlKit.lastBitmap.get();
        Bitmap tessBitmap = tess.lastBitmap.get();
        assertNotNull(mlBitmap);
        assertNotNull(tessBitmap);
        assertTrue("ML input should remain non-binarized", hasNonBinaryPixel(mlBitmap));
        for (Bitmap candidate : tess.capturedBitmaps) {
            assertTrue("Tesseract input should be binarized", isBinarized(candidate));
            assertTrue("Tesseract input should be scaled up", candidate.getWidth() > mlBitmap.getWidth());
        }
        assertEquals(PreprocessParamSet.getCandidates().size(), tess.capturedBitmaps.size());
    }

    @Test
    public void boostsConfidenceForValidTesseractMrz() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "",
                6,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));

        String weakenedLine2 = TD3_LINE2.substring(0, 9) + "0" + TD3_LINE2.substring(10);
        String tessRaw = TD3_LINE1 + "\n" + weakenedLine2;
        OcrEngine tess = new FixedOcrEngine(new OcrResult(
                tessRaw,
                8,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.TESSERACT
        ));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MrzResult> mrzRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        OcrRouter.runAsync(context, mlKit, tess, bitmap, 0, new OcrRouter.Callback() {
            @Override
            public void onSuccess(OcrRouter.Result result, MrzResult mrz) {
                mrzRef.set(mrz);
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(mrzRef.get());
        assertEquals(4, mrzRef.get().confidence);
    }

    private static final class FixedOcrEngine implements OcrEngine {
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

    private static Bitmap createGradientBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = Math.min(255, (x + y) * 16);
                bitmap.setPixel(x, y, Color.rgb(value, value / 2, 255 - value));
            }
        }
        return bitmap;
    }

    private static boolean isBinarized(Bitmap bitmap) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                if (r != g || r != b || (r != 0 && r != 255)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasNonBinaryPixel(Bitmap bitmap) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                if (r == g && r == b && (r == 0 || r == 255)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static final class FlagOcrEngine implements OcrEngine {
        private final AtomicBoolean called;
        private final OcrResult.Engine engine;
        private final String text;

        private FlagOcrEngine(AtomicBoolean called, OcrResult.Engine engine) {
            this(called, engine, "");
        }

        private FlagOcrEngine(AtomicBoolean called, OcrResult.Engine engine, String text) {
            this.called = called;
            this.engine = engine;
            this.text = text;
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
            called.set(true);
            callback.onSuccess(new OcrResult(text, 5, new OcrMetrics(0, 0, 0), engine));
        }

        @Override
        public void close() {
        }
    }

    private static final class CapturingOcrEngine implements OcrEngine {
        private final OcrResult result;
        private final AtomicReference<Bitmap> lastBitmap = new AtomicReference<>();
        private final java.util.List<Bitmap> capturedBitmaps =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        private CapturingOcrEngine(OcrResult result) {
            this.result = result;
        }

        @Override
        public String getName() {
            return "capture";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            lastBitmap.set(bitmap);
            capturedBitmaps.add(bitmap);
            callback.onSuccess(result);
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingOcrEngine implements OcrEngine {
        @Override
        public String getName() {
            return "failing";
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            callback.onFailure(new IllegalStateException("tess failed"));
        }

        @Override
        public void close() {
        }
    }
}
