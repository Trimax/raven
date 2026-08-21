package io.github.trimax.raven.core.handler;

import io.github.trimax.raven.core.Message;

/**
 * Callback interface for client-side events: connection, disconnection, and messages from the server.
 */
public interface ClientHandler {

    /**
     * Called when successfully connected to the server.
     */
    void onConnect();

    /**
     * Called when disconnected from the server.
     */
    void onDisconnect();

    /**
     * Called when a message is received from the server.
     */
    void onMessage(Message message);
}
