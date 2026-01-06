package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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
}
