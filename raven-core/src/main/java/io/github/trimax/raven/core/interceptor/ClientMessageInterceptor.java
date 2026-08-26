package io.github.trimax.raven.core.interceptor;

import io.github.trimax.raven.core.Message;

/**
 * Intercepts incoming messages on the client before they reach handlers.
 * Implementations can reject messages by returning {@code false}.
 */
@FunctionalInterface
public interface ClientMessageInterceptor {

    /**
     * Intercepts a message before it is dispatched to handlers.
     *
     * @param message the received message
     * @return {@code true} to proceed with dispatch, {@code false} to reject the message
     */
    boolean intercept(Message message);
}
