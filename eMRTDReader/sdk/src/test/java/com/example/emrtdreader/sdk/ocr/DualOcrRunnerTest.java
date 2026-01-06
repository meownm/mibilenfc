package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import com.example.emrtdreader.sdk.models.OcrMetrics;
import com.example.emrtdreader.sdk.models.OcrResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DualOcrRunnerTest {

    private static final String TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
    private static final String TD3_VALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2;
    private static final String TD3_INVALID_RAW = TD3_LINE1 + "\n" + TD3_LINE2.substring(0, 43) + "1";

    @Test
    public void dualRunnerPicksBestMrzWhenBothComplete() {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit", new OcrResult(TD3_VALID_RAW, 12, new OcrMetrics(0, 0, 0)), 0, false);
        OcrEngine tess = new FakeOcrEngine("tess", new OcrResult(TD3_INVALID_RAW, 14, new OcrMetrics(0, 0, 0)), 0, false);

        DualOcrRunner.RunResult result = DualOcrRunner.runWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500);

        assertNotNull(result.mrz);
        assertEquals(TD3_LINE1, result.mrz.line1);
        assertEquals(TD3_VALID_RAW, result.ocr.rawText);
    }

    @Test
    public void dualRunnerReturnsAvailableResultWhenOtherFails() {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit", new OcrResult(TD3_VALID_RAW, 10, new OcrMetrics(0, 0, 0)), 0, true);
        OcrEngine tess = new FakeOcrEngine("tess", new OcrResult(TD3_VALID_RAW, 8, new OcrMetrics(0, 0, 0)), 0, false);

        DualOcrRunner.RunResult result = DualOcrRunner.runWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                500);

        assertNotNull(result.mrz);
        assertEquals(TD3_LINE1, result.mrz.line1);
        assertEquals(TD3_VALID_RAW, result.ocr.rawText);
    }

    @Test
    public void dualRunnerTimesOutAndUsesBestAvailable() {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        OcrEngine mlKit = new FakeOcrEngine("mlkit", new OcrResult(TD3_VALID_RAW, 18, new OcrMetrics(0, 0, 0)), 200, false);
        OcrEngine tess = new FakeOcrEngine("tess", new OcrResult(TD3_VALID_RAW, 9, new OcrMetrics(0, 0, 0)), 0, false);

        DualOcrRunner.RunResult result = DualOcrRunner.runWithTimeout(
                context,
                DualOcrRunner.Mode.AUTO_DUAL,
                mlKit,
                tess,
                bitmap,
                0,
                50);

        assertNotNull(result.mrz);
        assertEquals(TD3_LINE1, result.mrz.line1);
        assertEquals(TD3_VALID_RAW, result.ocr.rawText);
    }

    private static final class FakeOcrEngine implements OcrEngine {
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
        public OcrResult recognize(Context ctx, Bitmap bitmap, int rotationDegrees) {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (shouldThrow) {
                throw new IllegalStateException("OCR failed");
            }
            return result;
        }

        @Override
        public void close() {
        }
    }
}
