package io.github.trimax.raven.handler;

import io.github.trimax.raven.Client;
import io.github.trimax.raven.Message;

/**
 * Callback interface for server-side events: client connections, disconnections, and messages.
 */
public interface ServerHandler {

    /**
     * Called when a new client connects to the server.
     */
    void onConnect(Client client);

    /**
     * Called when a client disconnects from the server.
     */
    void onDisconnect(Client client);

    /**
     * Called when a message is received from a connected client.
     */
    void onMessage(Client sender, Message message);
}
