package com.example.emrtdreader.data;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.example.emrtdreader.crypto.PassiveAuthVerifier;
import com.example.emrtdreader.domain.AccessKey;
import com.example.emrtdreader.domain.PassportReadResult;
import com.example.emrtdreader.error.PassportReadException;
import com.example.emrtdreader.models.PassportChipData;
import com.example.emrtdreader.models.VerificationResult;

import net.sf.scuba.smartcards.IsoDepCardService;

import org.jmrtd.PassportService;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class NfcPassportReader {
    private static final int ISO_DEP_TIMEOUT_MS = 12000;
    private static final int MAX_RETRIES = 2;

    public PassportReadResult read(Context ctx, Tag tag, AccessKey.Mrz mrz, String can) throws PassportReadException {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) throw new PassportReadException.TagNotIsoDep();

        isoDep.setTimeout(ISO_DEP_TIMEOUT_MS);

        PassportReadException last = null;

        for (int attempt=0; attempt<=MAX_RETRIES; attempt++) {
            IsoDepCardService cardService = new IsoDepCardService(isoDep);
            PassportService ps = null;

            try {
                cardService.open();

                ps = new PassportService(
                        cardService,
                        PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                        PassportService.DEFAULT_MAX_BLOCKSIZE,
                        false,
                        false
                );
                ps.open();

                try { ps.sendSelectApplet(false); } catch (Throwable ignore) {}

                new JmrtdAccessController(ps).establish(mrz, can);

                byte[] dg1Bytes = readEfOrThrow(ps, PassportService.EF_DG1);
                byte[] dg2Bytes = readEfOrNull(ps, PassportService.EF_DG2);
                byte[] sodBytes = readEfOrNull(ps, PassportService.EF_SOD);

                DG1File dg1 = new DG1File(new ByteArrayInputStream(dg1Bytes));
                var mrzInfo = dg1.getMRZInfo();

                byte[] photo = null;
                if (dg2Bytes != null) {
                    try {
                        DG2File dg2 = new DG2File(new ByteArrayInputStream(dg2Bytes));
                        if (!dg2.getFaceInfos().isEmpty()
                                && !dg2.getFaceInfos().get(0).getFaceImageInfos().isEmpty()) {
                            photo = dg2.getFaceInfos().get(0).getFaceImageInfos().get(0).getImageInputStream().readAllBytes();
                        }
                    } catch (Throwable ignore) {}
                }

                Map<Integer, byte[]> dgs = new HashMap<>();
                dgs.put(1, dg1Bytes);
                if (dg2Bytes != null) dgs.put(2, dg2Bytes);

                VerificationResult vr = PassiveAuthVerifier.verify(ctx, sodBytes, dgs);

                PassportChipData chip = new PassportChipData(
                        mrzInfo.getDocumentNumber(),
                        mrzInfo.getPrimaryIdentifier(),
                        mrzInfo.getSecondaryIdentifier(),
                        mrzInfo.getNationality(),
                        mrzInfo.getDateOfBirth(),
                        String.valueOf(mrzInfo.getGender()),
                        mrzInfo.getDateOfExpiry(),
                        photo
                );

                return new PassportReadResult(chip, vr);

            } catch (PassportReadException e) {
                last = e;
            } catch (Throwable t) {
                last = new PassportReadException.ReadFailed(t);
            } finally {
                try { if (ps != null) ps.close(); } catch (Throwable ignore) {}
                try { cardService.close(); } catch (Throwable ignore) {}
                // do NOT close isoDep here; Android will manage tag lifecycle; but we can try:
                // try { isoDep.close(); } catch (Throwable ignore) {}
            }

            // retry on TagLost/IO-like errors
            if (last != null && attempt < MAX_RETRIES) {
                try { Thread.sleep(150); } catch (InterruptedException ignore) {}
                continue;
            }
        }

        throw last != null ? last : new PassportReadException.ReadFailed(new IllegalStateException("Unknown NFC error"));
    }

    private byte[] readEfOrThrow(PassportService ps, short fid) throws Exception {
        byte[] b = readEfOrNull(ps, fid);
        if (b == null) throw new IllegalStateException("Required EF missing: " + fid);
        return b;
    }

    private byte[] readEfOrNull(PassportService ps, short fid) {
        try {
            return ps.getInputStream(fid).readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }
}
