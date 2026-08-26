package io.github.trimax.raven.core.util.retry;

/**
 * Fixed interval retry strategy.
 * Retries up to a maximum number of attempts with a constant delay between them.
 *
 * @param maxAttempts maximum number of retry attempts
 * @param delayMs     delay between attempts in milliseconds
 */
public record FixedRetryStrategy(int maxAttempts, long delayMs) implements RetryStrategy {

    public FixedRetryStrategy {
        if (maxAttempts <= 0)
            throw new IllegalArgumentException("maxAttempts must be positive");

        if (delayMs <= 0)
            throw new IllegalArgumentException("delayMs must be positive");
    }

    @Override
    public long getDelay(final int attempt) {
        if (attempt >= maxAttempts)
            return -1;

        return delayMs;
    }
}
