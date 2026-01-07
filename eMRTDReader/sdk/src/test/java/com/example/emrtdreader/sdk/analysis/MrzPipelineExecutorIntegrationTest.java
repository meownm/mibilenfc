package com.example.emrtdreader.sdk.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MrzPipelineExecutorIntegrationTest {
    @Test
    public void runsLatestTaskOnSingleThread() throws InterruptedException {
        MrzPipelineExecutor executor = new MrzPipelineExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch latestRan = new CountDownLatch(1);
        AtomicReference<String> firstThread = new AtomicReference<>();
        AtomicReference<String> latestThread = new AtomicReference<>();

        executor.submit(() -> {
            firstThread.set(Thread.currentThread().getName());
            started.countDown();
            try {
                release.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));

        executor.submit(() -> {
            // dropped task
        });
        executor.submit(() -> {
            latestThread.set(Thread.currentThread().getName());
            latestRan.countDown();
        });

        release.countDown();

        assertTrue(latestRan.await(1, TimeUnit.SECONDS));
        assertEquals(firstThread.get(), latestThread.get());
        assertTrue(firstThread.get().startsWith("mrz-pipeline-"));
        executor.shutdown();
    }
}
