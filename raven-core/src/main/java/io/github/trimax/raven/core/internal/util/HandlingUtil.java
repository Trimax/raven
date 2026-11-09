package io.github.trimax.raven.core.internal.util;

import java.util.function.Supplier;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Failure handling shared by the transport classes: isolating user callbacks so that a failing handler ends only
 * its own session.
 *
 * <p>Internal: not part of the API.
 */
@Slf4j
@UtilityClass
public final class HandlingUtil {

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
