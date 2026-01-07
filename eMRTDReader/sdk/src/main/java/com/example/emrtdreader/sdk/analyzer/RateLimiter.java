package com.example.emrtdreader.sdk.analyzer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple thread-safe rate limiter.
 * Allows action only if minIntervalMs passed since last acquire.
 */
public final class RateLimiter {

    private final long minIntervalMs;
    private final AtomicLong lastTs = new AtomicLong(0L);

    public RateLimiter(long minIntervalMs) {
        if (minIntervalMs < 0) {
            throw new IllegalArgumentException("minIntervalMs must be >= 0");
        }
        this.minIntervalMs = minIntervalMs;
    }

    /**
     * @param nowMs current time in milliseconds
     * @return true if allowed, false otherwise
     */
    public boolean tryAcquire(long nowMs) {
        long prev = lastTs.get();
        if (nowMs - prev < minIntervalMs) {
            return false;
        }
        return lastTs.compareAndSet(prev, nowMs);
    }

    /** For tests / reset scenarios */
    public void reset() {
        lastTs.set(0L);
    }
}
