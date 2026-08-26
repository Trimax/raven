package io.github.trimax.raven.core.util.retry;

/**
 * Exponential backoff retry strategy.
 * Delay doubles after each attempt, capped at a maximum value.
 * Retries indefinitely (never returns -1).
 *
 * @param initialDelayMs initial delay in milliseconds
 * @param maxDelayMs     maximum delay cap in milliseconds
 */
public record ExponentialRetryStrategy(long initialDelayMs, long maxDelayMs) implements RetryStrategy {

    public ExponentialRetryStrategy {
        if (initialDelayMs <= 0)
            throw new IllegalArgumentException("initialDelayMs must be positive");

        if (maxDelayMs < initialDelayMs)
            throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
    }

    @Override
    public long getDelay(final int attempt) {
        final var delay = initialDelayMs * (1L << Math.min(attempt - 1, 62));

        if (delay <= 0)
            return maxDelayMs;

        return Math.min(delay, maxDelayMs);
    }
}
