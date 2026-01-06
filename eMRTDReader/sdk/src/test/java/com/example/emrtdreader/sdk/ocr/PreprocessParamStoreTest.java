package com.example.emrtdreader.sdk.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PreprocessParamStoreTest {

    private SharedPreferences preferences() {
        Context context = ApplicationProvider.getApplicationContext();
        return context.getSharedPreferences(PreprocessParamStore.PREF_NAME, Context.MODE_PRIVATE);
    }

    private void clearPrefs() {
        preferences().edit().clear().commit();
    }

    @Test
    public void serializeRoundTrip() {
        PreprocessParams params = new PreprocessParams(15, 5, 2.0f, 0);
        String json = PreprocessParamStore.toJson(params);
        assertNotNull(json);

        PreprocessParams restored = PreprocessParamStore.fromJson(json);
        assertEquals(params, restored);
    }

    @Test
    public void loadReturnsNullWhenMissing() {
        clearPrefs();
        Context context = ApplicationProvider.getApplicationContext();
        PreprocessParamStore store = new PreprocessParamStore(context);

        assertNull(store.load("camera", 1280, 720));
    }

    @Test
    public void loadReturnsNullForInvalidJson() {
        clearPrefs();
        SharedPreferences prefs = preferences();
        String key = PreprocessParamStore.buildKey("camera", 1280, 720);
        prefs.edit().putString(key, "not-json").commit();

        Context context = ApplicationProvider.getApplicationContext();
        PreprocessParamStore store = new PreprocessParamStore(context);

        assertNull(store.load("camera", 1280, 720));
    }

    @Test
    public void loadReturnsNullForInvalidParams() {
        clearPrefs();
        SharedPreferences prefs = preferences();
        String key = PreprocessParamStore.buildKey("camera", 1280, 720);
        prefs.edit().putString(key, "{\"blockSize\":2,\"c\":1,\"scale\":1.0,\"blurRadius\":0}").commit();

        Context context = ApplicationProvider.getApplicationContext();
        PreprocessParamStore store = new PreprocessParamStore(context);

        assertNull(store.load("camera", 1280, 720));
    }

    @Test
    public void persistsAcrossStoreInstances() {
        clearPrefs();
        Context context = ApplicationProvider.getApplicationContext();
        PreprocessParamStore store = new PreprocessParamStore(context);
        PreprocessParams params = new PreprocessParams(17, 7, 2.25f, 1);

        store.save("camera", 1280, 720, params);

        PreprocessParamStore restartedStore = new PreprocessParamStore(context);
        PreprocessParams restored = restartedStore.load("camera", 1280, 720);
        assertEquals(params, restored);
    }
}
