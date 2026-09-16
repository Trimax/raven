package io.github.trimax.raven.core.util;

import java.util.function.Consumer;
import java.util.function.Function;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for executing operations that may throw checked exceptions,
 * swallowing or handling failures gracefully.
 * <p>
 * Unlike {@link RetryUtil}, this class does not retry — it executes
 * the operation once and either returns a default/fallback value or delegates
 * the caught exception to a {@link Consumer} or {@link Function}.
 * </p>
 */
@Slf4j
@UtilityClass
public final class TryUtil {

    /**
     * Executes the given supplier, returning its result on success
     * or {@code defaultValue} if an exception is thrown.
     *
     * @param supplier     the operation to execute
     * @param defaultValue the value to return on failure
     * @param <T>          the result type
     * @param <E>          the type of exception that may be thrown
     * @return the supplier result, or {@code defaultValue} on failure
     */
    public <T, E extends Exception> T execute(@NonNull final ThrowingSupplier<T, E> supplier, final T defaultValue) {
        try {
            return supplier.get();
        } catch (final Exception exception) {
            log.debug("Operation failed, returning default value", exception);
            return defaultValue;
        }
    }

    /**
     * Executes the given supplier, returning its result on success
     * or the value produced by the {@code onFailure} fallback function on exception.
     *
     * @param supplier  the operation to execute
     * @param onFailure a fallback function that receives the caught exception and returns a substitute result
     * @param <T>       the result type
     * @param <E>       the type of exception that may be thrown
     * @return the supplier result, or the fallback result on failure
     */
    @SuppressWarnings("unchecked")
    public <T, E extends Exception> T execute(@NonNull final ThrowingSupplier<T, E> supplier,
                                              @NonNull final Function<E, T> onFailure) {
        try {
            return supplier.get();
        } catch (final Exception exception) {
            return onFailure.apply((E) exception);
        }
    }

    /**
     * Executes the given runnable, silently swallowing any exception.
     *
     * @param runnable the operation to execute
     */
    public <E extends Exception> void execute(@NonNull final ThrowingRunnable<E> runnable) {
        execute(runnable, e -> log.debug("Operation failed, swallowing exception", e));
    }

    /**
     * Executes the given runnable, delegating any caught exception to {@code onFailure}.
     *
     * @param runnable  the operation to execute
     * @param onFailure a consumer invoked with the caught exception
     * @param <E>       the type of exception that may be thrown
     */
    public <E extends Exception> void execute(@NonNull final ThrowingRunnable<E> runnable,
                                              @NonNull final Consumer<E> onFailure) {
        execute((ThrowingSupplier<Void, E>) () -> {
            runnable.run();
            return null;
        }, (Function<E, Void>) e -> {
            onFailure.accept(e);
            return null;
        });
    }
}
