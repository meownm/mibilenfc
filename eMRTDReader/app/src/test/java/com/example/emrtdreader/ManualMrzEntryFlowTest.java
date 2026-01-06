package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;

import com.example.emrtdreader.sdk.domain.AccessKey;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ManualMrzEntryFlowTest {

    @Test
    public void manualEntryWithMissingFieldsShowsValidationToast() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        EditText docEdit = activity.findViewById(R.id.docNumberEdit);
        EditText dobEdit = activity.findViewById(R.id.dobEdit);
        EditText doeEdit = activity.findViewById(R.id.doeEdit);
        Button confirmManualButton = activity.findViewById(R.id.confirmManualButton);

        docEdit.setText("");
        dobEdit.setText("900101");
        doeEdit.setText("300101");
        confirmManualButton.performClick();

        assertEquals("Fill doc number + YYMMDD dates", ShadowToast.getTextOfLatestToast());
        assertNull(ShadowApplication.getInstance().getNextStartedActivity());
    }

    @Test
    public void manualEntryWithValidFieldsStartsNfcReadActivity() {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        MRZScanActivity activity = Robolectric.buildActivity(MRZScanActivity.class).setup().get();

        EditText docEdit = activity.findViewById(R.id.docNumberEdit);
        EditText dobEdit = activity.findViewById(R.id.dobEdit);
        EditText doeEdit = activity.findViewById(R.id.doeEdit);
        Button confirmManualButton = activity.findViewById(R.id.confirmManualButton);

        docEdit.setText("L898902C3");
        dobEdit.setText("740812");
        doeEdit.setText("120415");
        confirmManualButton.performClick();

        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertNotNull(startedIntent);
        assertEquals(NFCReadActivity.class.getName(), startedIntent.getComponent().getClassName());

        AccessKey.Mrz key = (AccessKey.Mrz) startedIntent.getSerializableExtra("accessKey");
        assertNotNull(key);
        assertEquals("L898902C3", key.documentNumber);
        assertEquals("740812", key.dateOfBirthYYMMDD);
        assertEquals("120415", key.dateOfExpiryYYMMDD);
    }
}
