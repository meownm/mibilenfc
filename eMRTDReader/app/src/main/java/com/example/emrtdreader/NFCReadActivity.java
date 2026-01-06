package com.example.emrtdreader;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.emrtdreader.sdk.data.NfcPassportReader;
import com.example.emrtdreader.sdk.domain.AccessKey;
import com.example.emrtdreader.sdk.domain.PassportReadResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NFCReadActivity extends AppCompatActivity {

    private TextView status;
    private EditText canEdit;
    private Button readButton;
    private ScrollView logScrollView;
    private TextView logTextView;

    private AccessKey.Mrz accessKey;
    private NfcAdapter nfcAdapter;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean armed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_read);

        status = findViewById(R.id.statusTextView);
        canEdit = findViewById(R.id.canEditText);
        readButton = findViewById(R.id.readNfcButton);
        logScrollView = findViewById(R.id.nfcLogScrollView);
        logTextView = findViewById(R.id.nfcLogTextView);

        accessKey = (AccessKey.Mrz) getIntent().getSerializableExtra("accessKey");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        readButton.setOnClickListener(v -> {
            armed = true;
            status.setText("Tap document to NFC...");
            appendLogLine("NFC: waiting for document tap");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableReaderMode(this, this::onTagDiscovered,
                    NfcAdapter.FLAG_READER_NFC_A
                            | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    private void onTagDiscovered(Tag tag) {
        if (!armed) return;
        armed = false;

        String can = canEdit.getText().toString().trim();
        runOnUiThread(() -> status.setText("Reading NFC..."));
        appendLogLine("NFC: tag discovered");
        appendLogLine("NFC: starting read");

        exec.execute(() -> {
            try {
                NfcPassportReader reader = new NfcPassportReader();
                PassportReadResult result = reader.read(getApplicationContext(), tag, accessKey, can.isEmpty() ? null : can);

                Intent i = new Intent(this, ResultActivity.class);
                i.putExtra("result", result);
                startActivity(i);

                runOnUiThread(() -> status.setText("Done"));
                appendLogLine("NFC: read complete");
                appendLogLine(formatPassiveAuthResult(result));

            } catch (Throwable e) {
                runOnUiThread(() -> status.setText("Error: " + e.getMessage()));
                appendLogLine("NFC: error " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }

    private void appendLogLine(String line) {
        runOnUiThread(() -> {
            CharSequence existing = logTextView.getText();
            StringBuilder builder = new StringBuilder();
            if (existing != null && existing.length() > 0) {
                builder.append(existing).append("\n");
            }
            builder.append(line);
            logTextView.setText(builder.toString());
            if (logScrollView != null) {
                logScrollView.post(() -> logScrollView.fullScroll(android.view.View.FOCUS_DOWN));
            }
        });
    }

    String formatPassiveAuthResult(PassportReadResult result) {
        if (result == null || result.verification == null) {
            return "Passive auth: unavailable";
        }
        return "Passive auth:"
                + " signature=" + result.verification.sodSignatureValid
                + " hashes=" + result.verification.dgHashesMatch
                + " cscaTrusted=" + result.verification.cscaTrusted
                + " details=" + result.verification.details;
    }
}
