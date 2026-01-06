package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DualOcrRunnerIntegrationTest {
    private static final String TD1_LINE1 = "IDUSA1234567897<<<<<<<<<<<<<<<";
    private static final String TD1_LINE2 = "7001017M2501017<<<<<<<<<<<<<<<";
    private static final String TD1_LINE3 = "DOE<<JOHN<<<<<<<<<<<<<<<<<<<<4";
    private static final String TD1_RAW = TD1_LINE1 + "\n" + TD1_LINE2 + "\n" + TD1_LINE3;

    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final String TD3_RAW = TD3_LINE1 + "\n" + TD3_LINE2;

    @Test
    public void autoDualPrefersTd3ForTieAcrossFormats() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine td1Engine = new FixedOcrEngine(
                new OcrResult(TD1_RAW, 8, new OcrMetrics(0, 0, 0), OcrResult.Engine.ML_KIT));
        OcrEngine td3Engine = new FixedOcrEngine(
                new OcrResult(TD3_RAW, 8, new OcrMetrics(0, 0, 0), OcrResult.Engine.TESSERACT));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DualOcrRunner.RunResult> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        DualOcrRunner.runAsyncWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                td1Engine,
                td3Engine,
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
        assertEquals(MrzFormat.TD3, result.mrz.format);
        assertEquals(TD3_LINE1, result.mrz.line1);
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
}
