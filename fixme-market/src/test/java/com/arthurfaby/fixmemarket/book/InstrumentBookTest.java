package com.arthurfaby.fixmemarket.book;

import com.arthurfaby.fixmecommon.protocol.enums.Side;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD phase 6 (PLAN.md) : le book se teste entierement sans reseau.
 * Le piege vise ici est le check-et-decrement non atomique
 * (if (stock >= qty) stock -= qty) : d'ou le test a 100 threads.
 */
class InstrumentBookTest {

    @Test
    void buyWithEnoughStockIsExecutedAndDecrements() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 1000));

        assertThat(book.tryExecute("AAPL", Side.BUY, 100)).isEqualTo(InstrumentBook.Result.EXECUTED);
        assertThat(book.quantityOf("AAPL")).isEqualTo(900);
    }

    @Test
    void buyOfExactlyTheWholeStockIsExecuted() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.tryExecute("AAPL", Side.BUY, 100)).isEqualTo(InstrumentBook.Result.EXECUTED);
        assertThat(book.quantityOf("AAPL")).isZero();
    }

    @Test
    void buyBeyondStockIsRejectedAndLeavesTheStockUntouched() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.tryExecute("AAPL", Side.BUY, 101))
                .isEqualTo(InstrumentBook.Result.INSUFFICIENT_QUANTITY);
        // le piege : decrementer puis constater le negatif
        assertThat(book.quantityOf("AAPL")).isEqualTo(100);
    }

    @Test
    void buyOnUnknownInstrumentIsRejectedAndCreatesNoEntry() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.tryExecute("TSLA", Side.BUY, 1))
                .isEqualTo(InstrumentBook.Result.UNKNOWN_INSTRUMENT);
        assertThat(book.isKnown("TSLA")).isFalse();
        assertThat(book.quantities()).containsOnlyKeys("AAPL");
    }

    @Test
    void sellIsExecutedAndIncrements() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.tryExecute("AAPL", Side.SELL, 50)).isEqualTo(InstrumentBook.Result.EXECUTED);
        assertThat(book.quantityOf("AAPL")).isEqualTo(150);
    }

    @Test
    void sellOnUnknownInstrumentIsRejected() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.tryExecute("TSLA", Side.SELL, 50))
                .isEqualTo(InstrumentBook.Result.UNKNOWN_INSTRUMENT);
        assertThat(book.isKnown("TSLA")).isFalse();
    }

    @Test
    void quantityOfUnknownInstrumentIsZeroAndIsKnownReflectsTheBook() {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));

        assertThat(book.quantityOf("TSLA")).isZero();
        assertThat(book.isKnown("AAPL")).isTrue();
        assertThat(book.isKnown("TSLA")).isFalse();
    }

    @Test
    void bookCopiesItsInitialMapInsteadOfAliasingIt() {
        Map<String, Integer> initial = new java.util.HashMap<>(Map.of("AAPL", 100));
        InstrumentBook book = new InstrumentBook(initial);

        initial.put("AAPL", 999);
        initial.put("GOOG", 500);

        assertThat(book.quantityOf("AAPL")).isEqualTo(100);
        assertThat(book.isKnown("GOOG")).isFalse();
    }

    @Test
    void hundredConcurrentBuysOfOneOnAStockOfHundredAllSucceed() throws InterruptedException {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));
        AtomicInteger executed = new AtomicInteger();

        runConcurrently(100, () -> {
            if (book.tryExecute("AAPL", Side.BUY, 1) == InstrumentBook.Result.EXECUTED) {
                executed.incrementAndGet();
            }
        });

        assertThat(executed.get()).isEqualTo(100);
        assertThat(book.quantityOf("AAPL")).isZero();
    }

    @Test
    void concurrentOverbookingNeverDrivesTheStockNegative() throws InterruptedException {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 100));
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        // deux fois plus d'acheteurs que de stock : exactement 100 doivent passer
        runConcurrently(200, () -> {
            if (book.tryExecute("AAPL", Side.BUY, 1) == InstrumentBook.Result.EXECUTED) {
                executed.incrementAndGet();
            } else {
                refused.incrementAndGet();
            }
        });

        assertThat(executed.get()).isEqualTo(100);
        assertThat(refused.get()).isEqualTo(100);
        assertThat(book.quantityOf("AAPL")).isZero();
    }

    @Test
    void concurrentBuysAndSellsKeepTheStockExact() throws InterruptedException {
        InstrumentBook book = InstrumentBook.of(new Instrument("AAPL", 500));
        AtomicInteger index = new AtomicInteger();
        AtomicInteger executed = new AtomicInteger();

        // 200 taches : les paires achetent 1, les impaires vendent 1 -> stock final inchange
        runConcurrently(200, () -> {
            Side side = index.getAndIncrement() % 2 == 0 ? Side.BUY : Side.SELL;
            if (book.tryExecute("AAPL", side, 1) == InstrumentBook.Result.EXECUTED) {
                executed.incrementAndGet();
            }
        });

        assertThat(executed.get()).isEqualTo(200);
        assertThat(book.quantityOf("AAPL")).isEqualTo(500);
    }

    /** Lance {@code count} taches en parallele, toutes relachees au meme instant. */
    private static void runConcurrently(int count, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        try {
            for (int i = 0; i < count; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        task.run();
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
    }
}
