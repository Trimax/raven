package io.github.trimax.raven.core;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a single client connected to the {@link RavenServer}.
 * Wraps a {@link Connection} and adds a unique identifier.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Client {

    @Getter
    private final UUID id = UUID.randomUUID();
    private final Connection connection;

    /**
     * Sends a message to this client. Thread-safe.
     */
    void send(final Message message) {
        connection.send(message);
    }

    /**
     * Returns the underlying connection, for the server's receive loop.
     */
    Connection connection() {
        return connection;
    }

    /**
     * Returns whether this client is still connected.
     */
    public boolean isConnected() {
        return connection.isConnected();
    }

    /**
     * Closes the connection to this client.
     */
    public void disconnect() {
        connection.disconnect();
    }
}
