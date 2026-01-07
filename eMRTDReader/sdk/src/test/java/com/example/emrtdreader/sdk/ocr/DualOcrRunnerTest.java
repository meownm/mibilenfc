package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DualOcrRunnerTest {

    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final String TD3_VALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2;
    private static final String TD3_INVALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2.substring(0, 43) + "1";

    @Test
    public void dualRunnerPicksBestMrzWhenBothComplete() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit",
                new OcrResult(TD3_VALID_RAW, 12, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT),
                0,
                false);
        OcrEngine tess = new FakeOcrEngine("tess",
                new OcrResult(TD3_INVALID_RAW, 14, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT),
                0,
                false);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
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
        DualOcrRunner.RunResult result = resultRef.get();
        assertNotNull(result);
        assertNotNull(result.mrz);
        assertEquals(TD3_LINE1, result.mrz.line1);
        assertEquals(TD3_VALID_RAW, result.ocr.rawText);
        assertEquals(OcrResult.Engine.ML_KIT, result.ocr.engine);
    }

    @Test
    public void dualRunnerReturnsAvailableResultWhenOtherFails() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit",
                new OcrResult(TD3_VALID_RAW, 10, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT),
                0,
                true);
        OcrEngine tess = new FakeOcrEngine("tess",
                new OcrResult(TD3_VALID_RAW, 8, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT),
                0,
                false);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
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
        DualOcrRunner.RunResult result = resultRef.get();
        assertNotNull(result);
        assertNotNull(result.mrz);
        assertEquals(TD3_LINE1, result.mrz.line1);
        assertEquals(TD3_VALID_RAW, result.ocr.rawText);
        assertEquals(OcrResult.Engine.TESSERACT, result.ocr.engine);
    }

    @Test
    public void dualRunnerReportsFailureWhenBothFail() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit",
                new OcrResult("", 10, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT),
                0,
                true);
        OcrEngine tess = new FakeOcrEngine("tess",
                new OcrResult("", 8, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT),
                0,
                true);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
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
        assertNull(resultRef.get());
        assertNotNull(errorRef.get());
    }

    @Test
    public void mlKitOnlyUsesNonBinarizedPreprocess() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = createGradientBitmap(8, 8);

        CapturingOcrEngine mlKit = new CapturingOcrEngine(OcrResult.Engine.ML_KIT);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.MLKIT_ONLY,
                mlKit,
                null,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
                        resultRef.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertNotNull(mlKit.lastBitmap.get());
        assertTrue("ML Kit preprocess should preserve grayscale (non-binary) pixels",
                hasNonBinaryPixel(mlKit.lastBitmap.get()));
    }

    @Test
    public void tessOnlyUsesBinarizedPreprocess() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = createGradientBitmap(8, 8);

        CapturingOcrEngine tess = new CapturingOcrEngine(OcrResult.Engine.TESSERACT);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.TESS_ONLY,
                null,
                tess,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
                        resultRef.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertNotNull(tess.lastBitmap.get());
        assertTrue("Tesseract preprocess should be binarized",
                isBinarized(tess.lastBitmap.get()));
    }

    @Test
    public void autoDualUsesDifferentPreprocessForEngines() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = createGradientBitmap(8, 8);

        CapturingOcrEngine mlKit = new CapturingOcrEngine(OcrResult.Engine.ML_KIT);
        CapturingOcrEngine tess = new CapturingOcrEngine(OcrResult.Engine.TESSERACT);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500,
                new DualOcrRunner.RunCallback() {
                    @Override
                    public void onSuccess(DualOcrRunner.RunResult result) {
                        resultRef.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        Bitmap mlBitmap = mlKit.lastBitmap.get();
        Bitmap tessBitmap = tess.lastBitmap.get();
        assertNotNull(mlBitmap);
        assertNotNull(tessBitmap);
        assertTrue("ML Kit should receive non-binarized pixels", hasNonBinaryPixel(mlBitmap));
        assertTrue("Tesseract should receive binarized pixels", isBinarized(tessBitmap));
        assertTrue("ML and Tesseract inputs should differ", mlBitmap != tessBitmap);
        assertTrue("Tesseract input should be scaled up", tessBitmap.getWidth() > mlBitmap.getWidth());
    }

    @Test
    public void pickBestPrefersTd3WhenConfidenceTies() throws Exception {
        MrzResult td1 = new MrzResult("L1", "L2", "L3", MrzFormat.TD1, 2);
        MrzResult td3 = new MrzResult("L1", "L2", null, MrzFormat.TD3, 2);

        MrzResult result = invokePickBest(td1, td3);

        assertEquals(MrzFormat.TD3, result.format);
        assertEquals(td3, result);
    }

    @Test
    public void pickBestReturnsFirstWhenConfidenceAndFormatMatch() throws Exception {
        MrzResult first = new MrzResult("L1", "L2", "L3", MrzFormat.TD1, 3);
        MrzResult second = new MrzResult("L1", "L2", "L3", MrzFormat.TD1, 3);

        MrzResult result = invokePickBest(first, second);

        assertEquals(first, result);
    }

    @Test
    public void pickBestReturnsFirstWhenConfidenceAndFormatTieWithDifferentData() throws Exception {
        MrzResult first = new MrzResult("L1", "L2", "L3", MrzFormat.TD1, 2);
        MrzResult second = new MrzResult("X1", "X2", "X3", MrzFormat.TD1, 2);

        MrzResult result = invokePickBest(first, second);

        assertNotNull(result);
        assertEquals(first, result);
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

    private static MrzResult invokePickBest(MrzResult a, MrzResult b) throws Exception {
        Method method = DualOcrRunner.class.getDeclaredMethod("pickBest", MrzResult.class, MrzResult.class);
        method.setAccessible(true);
        return (MrzResult) method.invoke(null, a, b);
    }

    private static final class FakeOcrEngine implements OcrEngine {
        private static final ScheduledExecutorService EXECUTOR =
                Executors.newScheduledThreadPool(2);

        private final String name;
        private final OcrResult result;
        private final long delayMs;
        private final boolean shouldThrow;

        private FakeOcrEngine(String name, OcrResult result, long delayMs, boolean shouldThrow) {
            this.name = name;
            this.result = result;
            this.delayMs = delayMs;
            this.shouldThrow = shouldThrow;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            EXECUTOR.schedule(() -> {
                if (shouldThrow) {
                    callback.onFailure(new IllegalStateException("OCR failed"));
                } else {
                    callback.onSuccess(result);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
        }
    }

    private static final class CapturingOcrEngine implements OcrEngine {
        private static final ScheduledExecutorService EXECUTOR =
                Executors.newScheduledThreadPool(2);

        private final OcrResult.Engine engine;
        private final AtomicReference<Bitmap> lastBitmap = new AtomicReference<>();

        private CapturingOcrEngine(OcrResult.Engine engine) {
            this.engine = engine;
        }

        @Override
        public String getName() {
            return engine.name();
        }

        @Override
        public boolean isAvailable(Context ctx) {
            return true;
        }

        @Override
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            lastBitmap.set(bitmap);
            EXECUTOR.schedule(() -> callback.onSuccess(new OcrResult("", 0, new OcrMetrics(0, 0, 0), engine)),
                    0,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
        }
    }
}
