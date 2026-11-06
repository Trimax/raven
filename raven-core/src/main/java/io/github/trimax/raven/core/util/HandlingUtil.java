package io.github.trimax.raven.core.util;

import java.util.function.Supplier;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Failure handling shared by the transport classes: releasing a resource when its setup fails, and isolating
 * user callbacks so that a failing handler ends only its own session.
 */
@Slf4j
@UtilityClass
public final class HandlingUtil {

    /**
     * Runs {@code body}; if it throws, closes {@code resource} quietly and rethrows the original exception.
     * Use it while a resource is being set up and the caller would otherwise never receive anything to close.
     *
     * @param resource the resource to release if {@code body} fails
     * @param body     the setup step
     * @param <T>      the result type
     * @param <E>      the checked exception {@code body} may throw
     * @return the result of {@code body}
     * @throws E the exception thrown by {@code body}
     */
    public <T, E extends Exception> T closeOnFailure(@NonNull final AutoCloseable resource,
                                                     @NonNull final ThrowingSupplier<T, E> body) throws E {
        try {
            return body.get();
        } catch (final Exception exception) {
            TryUtil.execute(resource::close);
            throw exception;
        }
    }

    /**
     * Runs {@code body}; if it throws, closes {@code resource} quietly and rethrows the original exception.
     *
     * @param resource the resource to release if {@code body} fails
     * @param body     the setup step
     * @param <E>      the checked exception {@code body} may throw
     * @throws E the exception thrown by {@code body}
     */
    public <E extends Exception> void closeOnFailure(@NonNull final AutoCloseable resource,
                                                     @NonNull final ThrowingRunnable<E> body) throws E {
        closeOnFailure(resource, (ThrowingSupplier<Void, E>) () -> {
            body.run();
            return null;
        });
    }

    /**
     * Runs a user callback. A {@link RuntimeException} is logged and reported as {@code false}, so the caller can
     * end the affected session instead of letting the exception escape into the transport threads.
     *
     * @param callback the user callback
     * @param context  describes the call and its consequence for the log, e.g. "for message from &lt;id&gt;, closing its
     *                 connection"; evaluated only on failure
     * @return {@code true} if the callback returned normally, {@code false} if it threw
     */
    public boolean callHandler(@NonNull final Runnable callback, @NonNull final Supplier<String> context) {
        try {
            callback.run();
            return true;
        } catch (final RuntimeException exception) {
            log.error("Handler failed {}: {}", context.get(), exception.getMessage(), exception);
            return false;
        }
    }
}
