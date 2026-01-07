package com.example.emrtdreader.sdk.analysis;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-threaded executor that keeps only the latest queued task.
 */
public final class MrzPipelineExecutor {
    private final ThreadPoolExecutor executor;

    public MrzPipelineExecutor() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1);
        executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                new NamedThreadFactory("mrz-pipeline"),
                new LatestOnlyPolicy());
    }

    public void submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (executor.isShutdown()) {
            return;
        }
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private static final class LatestOnlyPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                return;
            }
            BlockingQueue<Runnable> queue = executor.getQueue();
            queue.poll();
            queue.offer(task);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(0);
        private final String baseName;

        private NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(baseName + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
