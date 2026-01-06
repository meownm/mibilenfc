package com.example.emrtdreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.domain.PassportReadResult;
import com.example.emrtdreader.sdk.models.PassportChipData;
import com.example.emrtdreader.sdk.models.VerificationResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NFCReadActivityTest {

    @Test
    public void readButtonAppendsLogLine() {
        NFCReadActivity activity = Robolectric.buildActivity(NFCReadActivity.class, buildIntent()).setup().get();

        Button readButton = activity.findViewById(R.id.readNfcButton);
        readButton.performClick();

        TextView logTextView = activity.findViewById(R.id.nfcLogTextView);
        assertTrue(logTextView.getText().toString().contains("NFC: waiting for document tap"));
    }

    @Test
    public void formatPassiveAuthResultHandlesNullResult() {
        NFCReadActivity activity = Robolectric.buildActivity(NFCReadActivity.class, buildIntent()).setup().get();

        assertEquals("Passive auth: unavailable", activity.formatPassiveAuthResult(null));
    }

    @Test
    public void formatPassiveAuthResultReportsVerification() {
        NFCReadActivity activity = Robolectric.buildActivity(NFCReadActivity.class, buildIntent()).setup().get();

        PassportChipData chipData = new PassportChipData(
                "L898902C3",
                "ERIKSSON",
                "ANNA MARIA",
                "UTO",
                "740812",
                "F",
                "120415",
                null
        );
        VerificationResult verificationResult = new VerificationResult(true, false, true, "CSCA missing");
        PassportReadResult result = new PassportReadResult(chipData, verificationResult);

        String text = activity.formatPassiveAuthResult(result);
        assertTrue(text.contains("signature=true"));
        assertTrue(text.contains("hashes=false"));
        assertTrue(text.contains("cscaTrusted=true"));
        assertTrue(text.contains("details=CSCA missing"));
    }

    private Intent buildIntent() {
        Intent intent = new Intent();
        intent.putExtra("accessKey", new AccessKey.Mrz("L898902C3", "740812", "120415"));
        return intent;
    }
}
