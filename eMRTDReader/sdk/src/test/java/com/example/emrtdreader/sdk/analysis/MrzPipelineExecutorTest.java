package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MrzPipelineExecutorTest {
    @Test
    public void submitExecutesTask() throws InterruptedException {
        MrzPipelineExecutor executor = new MrzPipelineExecutor();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(latch::countDown);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    public void dropQueueKeepsLatestTask() throws InterruptedException {
        MrzPipelineExecutor executor = new MrzPipelineExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch latestRan = new CountDownLatch(1);
        AtomicBoolean droppedRan = new AtomicBoolean(false);

        executor.submit(() -> {
            started.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));

        executor.submit(() -> droppedRan.set(true));
        executor.submit(latestRan::countDown);

        release.countDown();

        assertTrue(latestRan.await(1, TimeUnit.SECONDS));
        assertFalse(droppedRan.get());
        executor.shutdown();
    }

    @Test
    public void shutdownPreventsNewTasks() throws InterruptedException {
        MrzPipelineExecutor executor = new MrzPipelineExecutor();
        executor.shutdown();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(latch::countDown);

        assertFalse(latch.await(200, TimeUnit.MILLISECONDS));
    }
}
