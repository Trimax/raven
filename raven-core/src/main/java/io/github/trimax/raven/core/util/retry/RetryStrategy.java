package io.github.trimax.raven.core.util.retry;

/**
 * Defines the retry behavior: how many attempts and how long to wait between them.
 */
public sealed interface RetryStrategy permits ExponentialRetryStrategy, FixedRetryStrategy {

    /**
     * Returns the delay in milliseconds before the given attempt.
     *
     * @param attempt the current attempt number (1-based)
     * @return delay in milliseconds, or -1 if no more retries should be attempted
     */
    long getDelay(int attempt);
}
