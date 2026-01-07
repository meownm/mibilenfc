package com.example.emrtdreader.sdk.analyzer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-thread executor for MRZ background pipeline.
 * Keeps only latest task to avoid backlog.
 */
public final class MrzPipelineExecutor {

    private final ExecutorService executor;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public MrzPipelineExecutor() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mrz-pipeline");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submit task if executor is idle.
     * Drops task if another one is running.
     */
    public void submit(Runnable task) {
        if (!busy.compareAndSet(false, true)) {
            return; // drop task, keep latest behavior
        }

        executor.execute(() -> {
            try {
                task.run();
            } finally {
                busy.set(false);
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
