package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.example.emrtdreader.sdk.analysis.ScanState;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MRZScanActivityOverlayTest {

    @Rule
    public GrantPermissionRule cameraPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Test
    public void overlayTurnsGrayWhileWaiting() {
        assertOverlayColorForState(ScanState.WAITING, R.color.overlay_waiting_gray);
    }

    @Test
    public void overlayTurnsGreenWhenMrzFound() {
        assertOverlayColorForState(ScanState.MRZ_FOUND, R.color.overlay_mrz_green);
    }

    @Test
    public void overlayTurnsPurpleWhenMlTextFound() {
        assertOverlayColorForState(ScanState.ML_TEXT_FOUND, R.color.overlay_mlkit_purple);
    }

    @Test
    public void overlayTurnsBlueWhenTessTextFound() {
        assertOverlayColorForState(ScanState.TESS_TEXT_FOUND, R.color.overlay_tess_blue);
    }

    @Test
    public void overlayTurnsRedOnError() {
        assertOverlayColorForState(ScanState.ERROR, R.color.overlay_error_red);
    }

    private void assertOverlayColorForState(ScanState state, int expectedColorRes) {
        try (ActivityScenario<MRZScanActivity> scenario = ActivityScenario.launch(createIntent())) {
            scenario.onActivity(activity -> {
                activity.setOverlayAnimationDisabledForTesting(true);
                activity.onScanState(state, "test");
                int actual = getOverlayColor(activity);
                int expected = ContextCompat.getColor(activity, expectedColorRes);
                assertEquals("Overlay color mismatch for " + state, expected, actual);
            });
        }
    }

    private Intent createIntent() {
        Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, MRZScanActivity.class);
        intent.putExtra(MRZScanActivity.EXTRA_DISABLE_CAMERA, true);
        return intent;
    }

    private int getOverlayColor(MRZScanActivity activity) {
        android.view.View view = activity.findViewById(R.id.analysisOverlayView);
        assertNotNull("Overlay view missing", view);
        if (view.getBackground() instanceof GradientDrawable) {
            GradientDrawable drawable = (GradientDrawable) view.getBackground().mutate();
            assertNotNull("Overlay drawable color missing", drawable.getColor());
            return drawable.getColor().getDefaultColor();
        }
        throw new AssertionError("Expected GradientDrawable background");
    }
}
