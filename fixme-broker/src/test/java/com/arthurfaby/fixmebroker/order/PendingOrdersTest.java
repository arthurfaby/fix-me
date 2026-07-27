package com.arthurfaby.fixmebroker.order;

import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PendingOrdersTest {

    private static Order order(int clOrdId) {
        return new Order(clOrdId, 100_002, "AAPL", Side.BUY, 100, new BigDecimal("150.50"));
    }

    @Test
    void clOrdIdsStartAtOneAndIncrement() {
        PendingOrders pending = new PendingOrders();

        assertThat(pending.nextClOrdId()).isEqualTo(1);
        assertThat(pending.nextClOrdId()).isEqualTo(2);
        assertThat(pending.nextClOrdId()).isEqualTo(3);
    }

    @Test
    void aRememberedOrderCanBeTakenExactlyOnce() {
        PendingOrders pending = new PendingOrders();
        Order order = order(1);
        pending.remember(order);

        assertThat(pending.take(1)).contains(order);
        assertThat(pending.take(1)).isEmpty();
        assertThat(pending.size()).isZero();
    }

    @Test
    void takingAnUnknownClOrdIdReturnsEmpty() {
        assertThat(new PendingOrders().take(999)).isEmpty();
    }

    @Test
    void sizeReflectsTheNumberOfOutstandingOrders() {
        PendingOrders pending = new PendingOrders();
        pending.remember(order(1));
        pending.remember(order(2));

        assertThat(pending.size()).isEqualTo(2);
        pending.take(1);
        assertThat(pending.size()).isEqualTo(1);
    }

    @Test
    void clOrdIdsAreUniqueUnderConcurrentAllocation() throws InterruptedException {
        PendingOrders pending = new PendingOrders();
        Set<Integer> ids = ConcurrentHashMap.newKeySet();
        int threads = 500;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        ids.add(pending.nextClOrdId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(ids).hasSize(threads);
    }
}
