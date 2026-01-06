package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.camera.view.PreviewView;

import com.example.emrtdreader.sdk.analyzer.MrzImageAnalyzer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CameraUiIntegrationTest {

    @Test
    public void mrzScanActivityUsesPreviewViewFromAppUiLayer() throws Exception {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        PreviewView previewView = activity.findViewById(R.id.cameraPreviewView);
        assertNotNull(previewView);

        View overlayView = activity.findViewById(R.id.analysisOverlayView);
        assertNotNull(overlayView);

        Field field = MRZScanActivity.class.getDeclaredField("previewView");
        assertEquals(PreviewView.class, field.getType());
    }

    @Test
    public void appCanReferenceSdkAnalyzerAlongsideUiDependencies() {
        assertNotNull(MrzImageAnalyzer.class);
        assertNotNull(PreviewView.class);
    }

    @Test
    public void mrzScanActivityProvidesScrollableLogView() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        ScrollView scrollView = activity.findViewById(R.id.logScrollView);
        TextView logTextView = activity.findViewById(R.id.logTextView);

        assertNotNull(scrollView);
        assertNotNull(logTextView);
    }
}
