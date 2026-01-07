package com.example.emrtdreader.sdk.analysis;

import com.example.emrtdreader.sdk.domain.MrzKey;
import com.example.emrtdreader.sdk.models.MrzFields;
import com.example.emrtdreader.sdk.models.MrzParseResult;

public final class MrzStateMachine {
    public MrzPipelineState state = MrzPipelineState.SEARCHING;
    public long sessionStartMs;
    public long lastOcrMs;
    public MrzKey lastValidKey;
    public int validStreak;

    public void onStableBox() {
        if (state == MrzPipelineState.SEARCHING || state == MrzPipelineState.TIMEOUT) {
            state = MrzPipelineState.TRACKING;
            sessionStartMs = System.currentTimeMillis();
            resetStreak();
        }
    }

    public void onOcrResult(MrzParseResult result, long nowMs) {
        lastOcrMs = nowMs;
        state = MrzPipelineState.OCR_RUNNING;

        MrzKey key = toKey(result);
        if (key == null) {
            resetStreak();
            state = MrzPipelineState.OCR_COOLDOWN;
            return;
        }

        if (key.equals(lastValidKey)) {
            validStreak += 1;
        } else {
            validStreak = 1;
            lastValidKey = key;
        }

        if (validStreak >= 2) {
            state = MrzPipelineState.CONFIRMED;
        } else {
            state = MrzPipelineState.OCR_COOLDOWN;
        }
    }

    public void onTimeout(long nowMs) {
        state = MrzPipelineState.TIMEOUT;
        lastOcrMs = nowMs;
        resetStreak();
    }

    private void resetStreak() {
        lastValidKey = null;
        validStreak = 0;
    }

    private static MrzKey toKey(MrzParseResult result) {
        if (result == null || !result.valid || result.fields == null) {
            return null;
        }
        MrzFields fields = result.fields;
        return new MrzKey(fields.getDocumentNumber(),
                fields.getBirthDateYYMMDD(),
                fields.getExpiryDateYYMMDD());
    }
}
