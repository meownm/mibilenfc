package com.example.emrtdreader.sdk.recognition;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-thread executor with "keep latest" semantics.
 *
 * If tasks are submitted while a task is running, only the most recent submitted task will run next.
 * This prevents backlogs on slow devices.
 */
public final class KeepLatestExecutor {

    private final ExecutorService executor;
    private final AtomicReference<Runnable> pending = new AtomicReference<>(null);
    private final AtomicBoolean workerScheduled = new AtomicBoolean(false);

    public KeepLatestExecutor(String threadName) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(Runnable task) {
        if (task == null) return;
        pending.set(task);
        scheduleWorkerIfNeeded();
    }

    private void scheduleWorkerIfNeeded() {
        if (!workerScheduled.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                while (true) {
                    Runnable r = pending.getAndSet(null);
                    if (r == null) break;
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                        // Never crash worker loop
                    }
                }
            } finally {
                workerScheduled.set(false);
                // Handle race: task submitted after we cleared workerScheduled
                if (pending.get() != null) {
                    scheduleWorkerIfNeeded();
                }
            }
        });
    }

    public void shutdownNow() {
        executor.shutdownNow();
    }
}
