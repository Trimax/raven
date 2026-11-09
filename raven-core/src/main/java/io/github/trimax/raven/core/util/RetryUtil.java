package io.github.trimax.raven.core.util;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import io.github.trimax.raven.core.util.retry.RetryStrategy;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for retrying operations with a configurable {@link RetryStrategy}.
 */
@Slf4j
@UtilityClass
public final class RetryUtil {

    /**
     * Executes a {@link ThrowingRunnable} operation with retry logic.
     * Retries on any exception according to the given strategy.
     * When the strategy returns a negative delay, the last exception thrown by the operation
     * is rethrown as-is; no wrapper exception is created.
     *
     * @param <E>       the type of exception the operation may throw
     * @param operation the operation to execute
     * @param strategy  the retry strategy defining delays and max attempts
     * @throws E if the operation still fails after the strategy stops retrying
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
     * When the strategy returns a negative delay, the last exception thrown by the operation
     * is rethrown as-is; no wrapper exception is created.
     *
     * @param <R>       the type of result returned by the operation
     * @param <E>       the type of exception the operation may throw
     * @param operation the operation to execute
     * @param strategy  the retry strategy defining delays and max attempts
     * @return the result of the operation if it succeeds
     * @throws E if the operation still fails after the strategy stops retrying
     */
    public static <R, E extends Exception> R execute(final ThrowingSupplier<R, E> operation,
                                                     final RetryStrategy strategy) throws E {
        return execute(operation, strategy, _ -> true, (_, _) -> { });
    }

    /**
     * Executes a {@link ThrowingSupplier} operation with retry logic, retrying only failures that {@code retryIf}
     * accepts and reporting each retried failure to {@code onRetry} before waiting.
     * The last exception is rethrown as-is when the strategy returns a negative delay, when {@code retryIf} rejects
     * it, or when the thread is interrupted while waiting (the interrupt flag stays set).
     * @param <R>       the type of result returned by the operation
     * @param <E>       the type of exception the operation may throw
     * @param operation the operation to execute
     * @param strategy  the retry strategy defining delays and max attempts
     * @param retryIf   whether a failure may be retried; {@code false} rethrows it at once
     * @param onRetry   receives the attempt number (1-based) and the failure of every attempt that is retried
     * @return the result of the operation if it succeeds
     * @throws E if the operation fails and is not retried any further
     */
    public static <R, E extends Exception> R execute(final ThrowingSupplier<R, E> operation,
                                                     final RetryStrategy strategy,
                                                     final Predicate<? super Exception> retryIf,
                                                     final BiConsumer<Integer, ? super Exception> onRetry) throws E {
        var attempt = 0;

        while (true) {
            try {
                return operation.get();
            } catch (final Exception exception) {
                attempt++;

                final var delay = strategy.getDelay(attempt);
                if (delay < 0 || !retryIf.test(exception))
                    throw exception;

                onRetry.accept(attempt, exception);
                log.debug("Retry attempt {} - waiting {}ms", attempt, delay);
                if (!waitBeforeNextAttempt(delay))
                    throw exception;
            }
        }
    }

    /**
     * @return {@code false} if the thread was interrupted: retrying then would spin, since every later wait fails at once
     */
    private static boolean waitBeforeNextAttempt(final long delayMs) {
        if (delayMs <= 0)
            return !Thread.currentThread().isInterrupted();

        try {
            Thread.sleep(delayMs);
            return true;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
