package xyz.geik.farmer.modules.autoseller.handlers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedBatchCounterTest {

    @Test
    void rejectsInvalidInitialAmount() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedBatchCounter(0));
    }

    @Test
    void enforcesBoundAndOneWayClose() {
        BoundedBatchCounter counter = new BoundedBatchCounter(5);
        assertTrue(counter.add(5, 10));
        assertFalse(counter.add(1, 10));
        assertEquals(10, counter.closeAndGet());
        assertEquals(-1, counter.closeAndGet());
        assertFalse(counter.add(1, 10));
    }

    @Test
    void concurrentAddsAreNeverLostOrOverBound() throws Exception {
        int workers = 64;
        BoundedBatchCounter counter = new BoundedBatchCounter(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Runnable> jobs = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            jobs.add(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (counter.add(1, 33)) accepted.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        jobs.forEach(pool::submit);
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(32, accepted.get());
        assertEquals(33, counter.closeAndGet());
    }
}
