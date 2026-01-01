package com.example.emrtdreader;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.emrtdreader.data.NfcPassportReader;
import com.example.emrtdreader.domain.AccessKey;
import com.example.emrtdreader.domain.PassportReadResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NFCReadActivity extends AppCompatActivity {

    private TextView status;
    private EditText canEdit;
    private Button readButton;

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

        accessKey = (AccessKey.Mrz) getIntent().getSerializableExtra("accessKey");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        readButton.setOnClickListener(v -> {
            armed = true;
            status.setText("Tap document to NFC...");
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

        exec.execute(() -> {
            try {
                NfcPassportReader reader = new NfcPassportReader();
                PassportReadResult result = reader.read(getApplicationContext(), tag, accessKey, can.isEmpty() ? null : can);

                Intent i = new Intent(this, ResultActivity.class);
                i.putExtra("result", result);
                startActivity(i);

                runOnUiThread(() -> status.setText("Done"));

            } catch (Throwable e) {
                runOnUiThread(() -> status.setText("Error: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
