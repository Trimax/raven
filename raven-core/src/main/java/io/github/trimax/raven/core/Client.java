package io.github.trimax.raven.core;

import java.util.UUID;

/**
 * A client connected to a {@link RavenServer}, as the server's handler sees it. The server creates one per
 * connection; handlers receive it in every callback of that connection.
 *
 * <p>An interface, so that handlers can be unit-tested with a stub or a mock instead of a running server.
 */
public interface Client {

    /**
     * Returns the unique identifier of this client.
     */
    UUID getId();

    /**
     * Returns whether this client is still connected.
     */
    boolean isConnected();

    /**
     * Closes the connection to this client. {@code onDisconnect} follows on the client's session thread.
     */
    void disconnect();
}
