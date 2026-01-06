package com.example.emrtdreader.sdk.ocr;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

public final class PreprocessParamStore {
    static final String PREF_NAME = "mrz_preprocess_params";
    private static final String KEY_PREFIX = "preprocess_params";
    private static final String FIELD_BLOCK_SIZE = "blockSize";
    private static final String FIELD_C = "c";
    private static final String FIELD_SCALE = "scale";
    private static final String FIELD_BLUR_RADIUS = "blurRadius";

    private final SharedPreferences preferences;

    public PreprocessParamStore(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void save(String cameraId, int width, int height, PreprocessParams params) {
        if (params == null || width <= 0 || height <= 0) {
            return;
        }
        String json = toJson(params);
        if (json == null) {
            return;
        }
        preferences.edit()
                .putString(buildKey(cameraId, width, height), json)
                .apply();
    }

    @Nullable
    public PreprocessParams load(String cameraId, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        String json = preferences.getString(buildKey(cameraId, width, height), null);
        if (json == null) {
            return null;
        }
        return fromJson(json);
    }

    @VisibleForTesting
    static String buildKey(String cameraId, int width, int height) {
        String safeId = (cameraId == null || cameraId.trim().isEmpty()) ? "unknown" : cameraId.trim();
        return KEY_PREFIX + ":" + safeId + ":" + width + "x" + height;
    }

    @VisibleForTesting
    static String toJson(PreprocessParams params) {
        try {
            JSONObject json = new JSONObject();
            json.put(FIELD_BLOCK_SIZE, params.blockSize);
            json.put(FIELD_C, params.c);
            json.put(FIELD_SCALE, params.scale);
            json.put(FIELD_BLUR_RADIUS, params.blurRadius);
            return json.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    static PreprocessParams fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            int blockSize = obj.getInt(FIELD_BLOCK_SIZE);
            int c = obj.getInt(FIELD_C);
            float scale = (float) obj.getDouble(FIELD_SCALE);
            int blurRadius = obj.getInt(FIELD_BLUR_RADIUS);
            return new PreprocessParams(blockSize, c, scale, blurRadius);
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }
}
