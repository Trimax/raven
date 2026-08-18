package io.github.trimax.raven;

import java.io.IOException;
import java.net.Socket;
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

    Client(final Socket socket) throws IOException {
        this.id = UUID.randomUUID();
        this.connection = new Connection(socket);
    }

    /**
     * Sends a message to this client. Thread-safe.
     */
    void send(final Message message) {
        connection.send(message);
    }

    /**
     * Reads the next message. Blocks until available.
     *
     * @return the received message, or null on error/disconnect
     */
    Message receive() {
        return connection.receive();
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
