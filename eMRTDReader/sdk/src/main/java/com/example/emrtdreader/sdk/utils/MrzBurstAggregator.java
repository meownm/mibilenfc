package com.example.emrtdreader.sdk.utils;

import android.util.Log;

import com.example.emrtdreader.sdk.models.MrzFormat;
import com.example.emrtdreader.sdk.models.MrzResult;

import java.util.HashMap;
import java.util.Map;

public final class MrzBurstAggregator {

    private static final String TAG = "MRZ_AGG";

    private final int minAcceptCount;
    private final int maxBurst;

    private final Map<String, Bucket> buckets = new HashMap<>();
    private int totalFrames;

    public MrzBurstAggregator(int minAcceptCount, int maxBurst) {
        this.minAcceptCount = minAcceptCount;
        this.maxBurst = maxBurst;
    }

    public synchronized void reset() {
        buckets.clear();
        totalFrames = 0;
    }

    public synchronized MrzResult addAndMaybeAggregate(MrzResult mrz) {
        if (mrz == null) return null;

        totalFrames++;

        String key = normalizeKey(mrz.asMrzText());

        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new Bucket(mrz);
            buckets.put(key, bucket);
        } else {
            bucket.add(mrz);
        }

        if (bucket.count >= minAcceptCount && bucket.best.confidence >= 2) {
            Log.d(TAG, "Early accept MRZ");
            return bucket.best;
        }

        if (totalFrames >= maxBurst) {
            Log.d(TAG, "Force accept MRZ");
            return pickBest();
        }

        return null;
    }

    private MrzResult pickBest() {
        Bucket best = null;
        for (Bucket b : buckets.values()) {
            if (best == null || b.best.confidence > best.best.confidence) {
                best = b;
            }
        }
        return best != null ? best.best : null;
    }

    private static String normalizeKey(String mrz) {
        return mrz.replace("\n", "").replace("<", "").trim();
    }

    private static final class Bucket {
        MrzResult best;
        int count;

        Bucket(MrzResult mrz) {
            this.best = mrz;
            this.count = 1;
        }

        void add(MrzResult mrz) {
            count++;
            if (mrz.confidence > best.confidence) {
                best = mrz;
            }
        }
    }
}
