package io.github.trimax.raven.core.util.retry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExponentialRetryStrategy}.
 */
class ExponentialRetryStrategyTest {

    @Test
    void firstAttemptReturnsInitialDelay() {
        final var strategy = new ExponentialRetryStrategy(100, 5000);

        assertEquals(100, strategy.getDelay(1));
    }

    @Test
    void delayDoublesEachAttempt() {
        final var strategy = new ExponentialRetryStrategy(100, 10000);

        assertEquals(100, strategy.getDelay(1));
        assertEquals(200, strategy.getDelay(2));
        assertEquals(400, strategy.getDelay(3));
        assertEquals(800, strategy.getDelay(4));
        assertEquals(1600, strategy.getDelay(5));
        assertEquals(3200, strategy.getDelay(6));
        assertEquals(6400, strategy.getDelay(7));
    }

    @Test
    void delayCapsAtMaximum() {
        final var strategy = new ExponentialRetryStrategy(100, 500);

        assertEquals(100, strategy.getDelay(1));
        assertEquals(200, strategy.getDelay(2));
        assertEquals(400, strategy.getDelay(3));
        assertEquals(500, strategy.getDelay(4)); // capped
        assertEquals(500, strategy.getDelay(5));
        assertEquals(500, strategy.getDelay(100));
    }

    @Test
    void neverReturnsNegative() {
        final var strategy = new ExponentialRetryStrategy(1, 1000);

        for (int attempt = 1; attempt <= 100; attempt++) {
            assertTrue(strategy.getDelay(attempt) > 0);
        }
    }

    @Test
    void initialDelayEqualsMaxDelayIsValid() {
        final var strategy = new ExponentialRetryStrategy(500, 500);

        assertEquals(500, strategy.getDelay(1));
        assertEquals(500, strategy.getDelay(2));
        assertEquals(500, strategy.getDelay(10));
    }

    @Test
    void rejectsZeroInitialDelay() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(0, 1000));
    }

    @Test
    void rejectsNegativeInitialDelay() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(-1, 1000));
    }

    @Test
    void rejectsMaxDelayLessThanInitial() {
        assertThrows(IllegalArgumentException.class, () -> new ExponentialRetryStrategy(1000, 500));
    }
}
