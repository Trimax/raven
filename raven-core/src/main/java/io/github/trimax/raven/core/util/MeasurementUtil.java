package io.github.trimax.raven.core.util;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utility class for measuring the execution time of code blocks.
 * <p>
 * This class provides utility methods for measuring the execution time of
 * {@link ThrowingSupplier} and {@link ThrowingRunnable} instances.
 * The measured {@link Duration} is always reported, even if execution
 * results in an exception.
 * </p>
 * <p>
 * Checked exceptions declared by the supplied operations are propagated
 * to the caller without being wrapped.
 * </p>
 */
@UtilityClass
public final class MeasurementUtil {

    /**
     * Measures the execution time of the given supplier and passes the measured
     * duration to the provided consumer.
     * <p>
     * The duration is reported regardless of whether the supplier completes
     * normally or throws an exception.
     * </p>
     *
     * @param supplier the supplier to execute
     * @param durationConsumer a consumer that receives the measured duration
     * @param <T> the type of the result returned by the supplier
     * @param <E> the type of exception that may be thrown by the supplier
     * @return the result returned by the supplier
     * @throws E if the supplier throws an exception
     */
    public static <T, E extends Exception> T measure(
            @NonNull final ThrowingSupplier<T, E> supplier,
            @NonNull final Consumer<Duration> durationConsumer) throws E {
        final var startTime = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            final var endTime = System.nanoTime();
            durationConsumer.accept(Duration.ofNanos(endTime - startTime));
        }
    }

    /**
     * Measures the execution time of the given supplier and passes both the result
     * (wrapped in {@link Optional}) and the measured duration to the provided
     * {@link BiConsumer}.
     * <p>
     * If the supplier throws an exception, {@link Optional#empty()} is passed
     * as the result and the exception is rethrown to the caller.
     * </p>
     *
     * @param supplier the supplier to execute
     * @param durationConsumer a consumer that receives the optional result and duration
     * @param <T> the type of the supplier result
     * @param <E> the type of exception that may be thrown by the supplier
     * @return the result returned by the supplier
     * @throws E if the supplier throws an exception
     */
    public static <T, E extends Exception> T measure(
            @NonNull final ThrowingSupplier<T, E> supplier,
            @NonNull final BiConsumer<Optional<T>, Duration> durationConsumer) throws E {
        final var startTime = System.nanoTime();
        T result = null;
        try {
            result = supplier.get();
            return result;
        } finally {
            final var endTime = System.nanoTime();
            durationConsumer.accept(Optional.ofNullable(result), Duration.ofNanos(endTime - startTime));
        }
    }

    /**
     * Measures the execution time of the given {@link ThrowingRunnable} and passes
     * the measured duration to the provided {@link Consumer}.
     * <p>
     * The duration is reported even if the runnable throws an exception.
     * Any checked exception declared by the runnable is propagated to the caller.
     * </p>
     *
     * @param runnable the runnable to execute
     * @param durationConsumer a consumer that receives the measured duration
     * @param <E> the type of exception that may be thrown by the runnable
     * @throws E if the runnable throws an exception
     */
    public static <E extends Exception> void measure(
            @NonNull final ThrowingRunnable<E> runnable,
            @NonNull final Consumer<Duration> durationConsumer) throws E {
        measure(() -> {
            runnable.run();
            return null;
        }, durationConsumer);
    }
}
