package com.arthurfaby.fixmecommon.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SerialExecutorTest {

    @Test
    void executesTasksInSubmissionOrder() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            List<Integer> executed = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(100);

            for (int i = 0; i < 100; i++) {
                int value = i;
                serial.execute(() -> {
                    executed.add(value);
                    latch.countDown();
                });
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executed).containsExactlyElementsOf(IntStream.range(0, 100).boxed().toList());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void neverRunsTwoTasksConcurrentlyUnderStress() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        ExecutorService producers = Executors.newFixedThreadPool(8);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            int taskCount = 1000;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger active = new AtomicInteger(0);
            AtomicInteger completed = new AtomicInteger(0);
            AtomicBoolean overlapDetected = new AtomicBoolean(false);

            for (int i = 0; i < taskCount; i++) {
                producers.submit(() -> serial.execute(() -> {
                    if (active.incrementAndGet() > 1) {
                        overlapDetected.set(true);
                    }
                    Thread.onSpinWait();
                    active.decrementAndGet();
                    completed.incrementAndGet();
                    latch.countDown();
                }));
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(overlapDetected).isFalse();
            assertThat(completed).hasValue(taskCount);
        } finally {
            producers.shutdown();
            pool.shutdown();
        }
    }
}
