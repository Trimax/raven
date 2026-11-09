package io.github.trimax.raven.core.util;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.util.retry.ExponentialRetryStrategy;
import io.github.trimax.raven.core.util.retry.FixedRetryStrategy;

/**
 * Unit tests for {@link RetryUtil}.
 */
class RetryUtilTest {

    @Test
    void supplierSucceedsOnFirstAttempt() {
        final var result = RetryUtil.execute(() -> "ok", new FixedRetryStrategy(3, 10));

        assertEquals("ok", result);
    }

    @Test
    void supplierSucceedsAfterRetries() {
        final var attempts = new AtomicInteger(0);

        final var result = RetryUtil.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("not yet");
            }
            return "done";
        }, new FixedRetryStrategy(5, 10));

        assertEquals("done", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void supplierExhaustsFixedStrategy() {
        final var attempts = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () ->
                RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fails");
                }, new FixedRetryStrategy(3, 10))
        );

        assertEquals(3, attempts.get());
    }

    @Test
    void runnableSucceedsOnFirstAttempt() {
        assertDoesNotThrow(() ->
                RetryUtil.execute(() -> {}, new FixedRetryStrategy(3, 10))
        );
    }

    @Test
    void runnableSucceedsAfterRetries() {
        final var attempts = new AtomicInteger(0);

        RetryUtil.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("not yet");
            }
        }, new FixedRetryStrategy(5, 10));

        assertEquals(2, attempts.get());
    }

    @Test
    void runnableExhaustsFixedStrategy() {
        final var attempts = new AtomicInteger(0);

        assertThrows(RuntimeException.class, () ->
                RetryUtil.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fails");
                }, new FixedRetryStrategy(4, 10))
        );

        assertEquals(4, attempts.get());
    }

    @Test
    void exponentialStrategyRetriesIndefinitelyUntilSuccess() {
        final var attempts = new AtomicInteger(0);

        final var result = RetryUtil.execute(() -> {
            if (attempts.incrementAndGet() < 5) {
                throw new RuntimeException("not yet");
            }
            return "finally";
        }, new ExponentialRetryStrategy(10, 100));

        assertEquals("finally", result);
        assertEquals(5, attempts.get());
    }

    @Test
    void exponentialStrategyBacksOff() {
        final var strategy = new ExponentialRetryStrategy(100, 1000);

        assertEquals(100, strategy.getDelay(1));
        assertEquals(200, strategy.getDelay(2));
        assertEquals(400, strategy.getDelay(3));
        assertEquals(800, strategy.getDelay(4));
        assertEquals(1000, strategy.getDelay(5)); // capped
        assertEquals(1000, strategy.getDelay(10)); // still capped
    }

    @Test
    void fixedStrategyReturnsConstantDelay() {
        final var strategy = new FixedRetryStrategy(3, 500);

        assertEquals(500, strategy.getDelay(1));
        assertEquals(500, strategy.getDelay(2));
        assertEquals(-1, strategy.getDelay(3)); // exhausted (3 total attempts = 2 retries max)
    }

    @Test
    void fixedStrategyRejectsInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(3, 0));
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(3, -1));
    }

    @Test
    void exponentialStrategyRejectsInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(0, 1000));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(-1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(1000, 500));
    }

    @Test
    void checkedExceptionPropagates() {
        final var attempts = new AtomicInteger(0);

        assertThrows(java.io.IOException.class, () ->
                RetryUtil.<Void, java.io.IOException>execute(() -> {
                    attempts.incrementAndGet();
                    throw new java.io.IOException("io error");
                }, new FixedRetryStrategy(2, 10))
        );

        assertEquals(2, attempts.get());
    }

    // --- execute(ThrowingSupplier, RetryStrategy, Predicate, BiConsumer) ---

    @Test
    void reportsEveryRetriedFailureWithItsAttemptNumber() throws IOException {
        final var calls = new AtomicInteger();
        final var retried = new ArrayList<Integer>();

        final var result = RetryUtil.execute(() -> {
            if (calls.incrementAndGet() < 3)
                throw new IOException("not yet");
            return "done";
        }, new FixedRetryStrategy(5, 1), exception -> true, (attempt, exception) -> retried.add(attempt));

        assertEquals("done", result);
        assertEquals(List.of(1, 2), retried);
    }

    @Test
    void rethrowsAtOnceWhenRetryIfRejectsTheFailure() {
        final var calls = new AtomicInteger();
        final var retried = new AtomicInteger();
        final var failure = new IOException("closed");

        final var thrown = assertThrows(IOException.class, () -> RetryUtil.execute(() -> {
            calls.incrementAndGet();
            throw failure;
        }, new FixedRetryStrategy(5, 1), exception -> false, (attempt, exception) -> retried.incrementAndGet()));

        assertSame(failure, thrown);
        assertEquals(1, calls.get());
        assertEquals(0, retried.get());
    }

    @Test
    void stopsRetryingWhenInterruptedDuringAPause() throws InterruptedException {
        final var calls = new AtomicInteger();
        final var outcome = new AtomicReference<Throwable>();
        final var interruptedAfter = new AtomicBoolean();

        final var worker = Thread.ofVirtual().start(() -> {
            try {
                RetryUtil.execute(() -> {
                    calls.incrementAndGet();
                    throw new IOException("failing");
                }, new ExponentialRetryStrategy(10_000, 10_000), exception -> true, (attempt, exception) -> { });
            } catch (final IOException ex) {
                outcome.set(ex);
            }
            interruptedAfter.set(Thread.currentThread().isInterrupted());
        });

        await().atMost(2, TimeUnit.SECONDS).until(() -> calls.get() == 1);
        worker.interrupt();
        worker.join(Duration.ofSeconds(2));

        assertFalse(worker.isAlive(), "an interrupt must end the retries instead of waiting out the pause");
        assertInstanceOf(IOException.class, outcome.get());
        assertTrue(interruptedAfter.get(), "the interrupt flag must stay set");
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotSpinWhenAlreadyInterrupted() throws InterruptedException {
        final var calls = new AtomicInteger();
        final var worker = Thread.ofVirtual().start(() -> {
            Thread.currentThread().interrupt();
            assertThrows(IOException.class, () -> RetryUtil.execute(() -> {
                calls.incrementAndGet();
                throw new IOException("failing");
            }, new ExponentialRetryStrategy(1, 1)));
        });
        worker.join(Duration.ofSeconds(2));

        // Before the fix every later Thread.sleep failed at once and the retries spun without pause
        assertEquals(1, calls.get());
    }
}
