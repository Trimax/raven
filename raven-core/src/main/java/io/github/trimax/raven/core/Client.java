package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;

import lombok.Getter;

/**
 * Represents a single client connected to the {@link RavenServer}.
 * Wraps a {@link Connection} and adds a unique identifier.
 */
public final class Client {

    @Getter
    private final UUID id;
    private final Connection connection;

    Client(final Socket socket, final int sendQueueCapacity, final Duration sendTimeout) throws IOException {
        this.id = UUID.randomUUID();
        this.connection = new Connection(socket, sendQueueCapacity, sendTimeout);
    }

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
