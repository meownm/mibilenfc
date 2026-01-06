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
public class OcrRouterSwitchIntegrationTest {
    @Test
    public void usesMlResultWithoutInvokingTesseract() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        OcrEngine mlKit = new FixedOcrEngine(new OcrResult(
                "ML_TEXT",
                9,
                new OcrMetrics(0, 0, 0),
                OcrResult.Engine.ML_KIT
        ));
        AtomicBoolean tessCalled = new AtomicBoolean(false);
        OcrEngine tess = new FlagOcrEngine(tessCalled);

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
        assertEquals("ML_TEXT", resultRef.get().finalText);
        assertFalse(tessCalled.get());
        assertNull(mrzRef.get());
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

        private FlagOcrEngine(AtomicBoolean called) {
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
        public void recognizeAsync(Context ctx, Bitmap bitmap, int rotationDegrees, Callback callback) {
            called.set(true);
            callback.onSuccess(new OcrResult("", 5, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT));
        }

        @Override
        public void close() {
        }
    }
}
