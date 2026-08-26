package io.github.trimax.raven.core.util.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FixedRetryStrategy}.
 */
class FixedRetryStrategyTest {

    @Test
    void returnsConstantDelayWithinAttempts() {
        final var strategy = new FixedRetryStrategy(5, 200);

        assertEquals(200, strategy.getDelay(1));
        assertEquals(200, strategy.getDelay(2));
        assertEquals(200, strategy.getDelay(3));
        assertEquals(200, strategy.getDelay(4));
    }

    @Test
    void returnsNegativeWhenExhausted() {
        final var strategy = new FixedRetryStrategy(3, 100);

        assertEquals(100, strategy.getDelay(1));
        assertEquals(100, strategy.getDelay(2));
        assertEquals(-1, strategy.getDelay(3));
        assertEquals(-1, strategy.getDelay(4));
        assertEquals(-1, strategy.getDelay(100));
    }

    @Test
    void singleAttemptExhaustsImmediately() {
        final var strategy = new FixedRetryStrategy(1, 100);

        assertEquals(-1, strategy.getDelay(1));
    }

    @Test
    void twoAttemptsAllowsOneRetry() {
        final var strategy = new FixedRetryStrategy(2, 300);

        assertEquals(300, strategy.getDelay(1));
        assertEquals(-1, strategy.getDelay(2));
    }

    @Test
    void rejectsZeroMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(0, 100));
    }

    @Test
    void rejectsNegativeMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(-1, 100));
    }

    @Test
    void rejectsZeroDelay() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(3, 0));
    }

    @Test
    void rejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRetryStrategy(3, -1));
    }
}
