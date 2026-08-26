package io.github.trimax.raven.core.util;

import java.util.List;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for running interceptor chains with fault isolation.
 * Exceptions thrown by interceptors are logged and treated as rejection.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InterceptorUtil {

    /**
     * Runs the server interceptor chain. Returns {@code true} if all interceptors pass.
     *
     * @param interceptors the interceptor list
     * @param client       the client that sent the message
     * @param message      the received message
     * @return {@code true} if the message should proceed to handlers
     */
    public static boolean shouldProceed(final List<ServerMessageInterceptor> interceptors,
                                        final Client client,
                                        final Message message) {
        if (CollectionUtil.isEmpty(interceptors))
            return true;

        for (final var interceptor : interceptors)
            if (shouldReject(() -> interceptor.preHandle(client, message)))
                return false;

        return true;
    }

    /**
     * Runs the client interceptor chain. Returns {@code true} if all interceptors pass.
     *
     * @param interceptors the interceptor list
     * @param message      the received message
     * @return {@code true} if the message should proceed to handlers
     */
    public static boolean shouldProceed(final List<ClientMessageInterceptor> interceptors,
                                        final Message message) {
        if (CollectionUtil.isEmpty(interceptors))
            return true;

        for (final var interceptor : interceptors)
            if (shouldReject(() -> interceptor.preHandle(message)))
                return false;

        return true;
    }

    private static boolean shouldReject(final ThrowingSupplier<Boolean, Exception> supplier) {
        try {
            return !supplier.get();
        } catch (final Exception exception) {
            log.warn("Interceptor threw an unexpected exception, message will be rejected: {}",
                    exception.getMessage(), exception);
            return true;
        }
    }
}
