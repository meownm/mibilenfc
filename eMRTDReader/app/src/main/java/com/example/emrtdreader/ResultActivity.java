package com.example.emrtdreader;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.emrtdreader.sdk.domain.PassportReadResult;
import com.example.emrtdreader.sdk.models.PassportChipData;
import com.example.emrtdreader.sdk.models.VerificationResult;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        PassportReadResult res = (PassportReadResult) getIntent().getSerializableExtra("result");
        ImageView photo = findViewById(R.id.photoImageView);
        TextView data = findViewById(R.id.dataTextView);
        TextView ver = findViewById(R.id.verificationStatusTextView);

        if (res == null) {
            data.setText("No result");
            return;
        }

        PassportChipData d = res.chipData;
        VerificationResult v = res.verification;

        if (d.photoJpeg != null && d.photoJpeg.length > 0) {
            photo.setImageBitmap(BitmapFactory.decodeByteArray(d.photoJpeg, 0, d.photoJpeg.length));
        }

        data.setText(
                "Document: " + d.documentNumber + "\n" +
                "Surname: " + d.surname + "\n" +
                "Given: " + d.givenNames + "\n" +
                "Nationality: " + d.nationality + "\n" +
                "DOB: " + d.dateOfBirth + "\n" +
                "Sex: " + d.sex + "\n" +
                "Expiry: " + d.dateOfExpiry + "\n"
        );

        ver.setText(
                "SOD signature: " + v.sodSignatureValid + "\n" +
                "DG hashes: " + v.dgHashesMatch + "\n" +
                "CSCA trusted: " + v.cscaTrusted + "\n\n" +
                v.details
        );
    }
}
