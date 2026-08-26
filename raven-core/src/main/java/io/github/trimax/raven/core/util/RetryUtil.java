package io.github.trimax.raven.core.util;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import io.github.trimax.raven.core.util.retry.RetryStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for retrying operations with a configurable {@link RetryStrategy}.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryUtil {

    /**
     * Executes a {@link ThrowingRunnable} operation with retry logic.
     * Retries on any exception according to the given strategy.
     * If the strategy is exhausted, throws {@link UnsupportedOperationException}.
     *
     * @param <E>       the type of exception the operation may throw
     * @param operation the operation to execute
     * @param strategy  the retry strategy defining delays and max attempts
     * @throws E if the operation throws a non-retryable exception
     */
    public static <E extends Exception> void execute(final ThrowingRunnable<E> operation,
                                                     final RetryStrategy strategy) throws E {
        execute((ThrowingSupplier<Void, E>) () -> {
            operation.run();
            return null;
        }, strategy);
    }

    /**
     * Executes a {@link ThrowingSupplier} operation with retry logic and returns the result.
     * Retries on any exception according to the given strategy.
     * If the strategy is exhausted, throws {@link UnsupportedOperationException}.
     *
     * @param <R>       the type of result returned by the operation
     * @param <E>       the type of exception the operation may throw
     * @param operation the operation to execute
     * @param strategy  the retry strategy defining delays and max attempts
     * @return the result of the operation if it succeeds
     * @throws E if the operation throws a non-retryable exception
     */
    public static <R, E extends Exception> R execute(final ThrowingSupplier<R, E> operation,
                                                     final RetryStrategy strategy) throws E {
        var attempt = 0;

        while (true) {
            try {
                return operation.get();
            } catch (final Exception exception) {
                attempt++;

                final var delay = strategy.getDelay(attempt);
                if (delay < 0)
                    throw exception;

                log.debug("Retry attempt {} - waiting {}ms", attempt, delay);
                waitBeforeNextAttempt(delay);
            }
        }
    }

    private static void waitBeforeNextAttempt(final long delayMs) {
        if (delayMs <= 0)
            return;

        try {
            Thread.sleep(delayMs);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
