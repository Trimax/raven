package io.github.trimax.raven.core.interceptor;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;

/**
 * Intercepts incoming messages on the server before they reach handlers.
 * Implementations can reject messages by returning {@code false}.
 */
@FunctionalInterface
public interface ServerMessageInterceptor {

    /**
     * Called before a message is dispatched to handlers.
     *
     * @param sender  the client that sent the message
     * @param message the received message
     * @return {@code true} to proceed with dispatch, {@code false} to reject the message
     */
    boolean preHandle(Client sender, Message message);
}
