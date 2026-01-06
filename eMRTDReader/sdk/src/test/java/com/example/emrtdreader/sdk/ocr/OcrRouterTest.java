package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

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
    public void usesMlKitWhenValid() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                TD3_VALID_RAW,
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
        assertEquals(TD3_VALID_RAW, resultRef.get().finalText);
        assertEquals(TD3_VALID_RAW, resultRef.get().mlText);
        assertTrue(!tessCalled.get());
        assertNotNull(mrzRef.get());
    }

    @Test
    public void fallsBackToTesseractWhenInvalid() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "INVALID",
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
        assertEquals("INVALID", resultRef.get().mlText);
        assertEquals(TD3_VALID_RAW, resultRef.get().tessText);
        assertNotNull(mrzRef.get());
    }

    @Test
    public void reportsFailureWhenFallbackFails() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "INVALID",
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
