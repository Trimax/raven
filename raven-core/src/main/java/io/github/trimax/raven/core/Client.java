package io.github.trimax.raven.core;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Represents a single client connected to the {@link RavenServer}: the server side of one session.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Client {

    private final Session session;

    /**
     * Returns the unique identifier of this client.
     */
    public UUID getId() {
        return session.id();
    }

    /**
     * Sends a message to this client. Thread-safe. The message is serialized on the calling thread, without
     * validation.
     *
     * @throws IllegalArgumentException if the message cannot be serialized
     */
    void send(final Message message) {
        send(session.settings().encode(message));
    }

    /**
     * Queues an already encoded frame for this client.
     */
    void send(final byte[] frame) {
        session.send(frame);
    }

    /**
     * Returns whether this client is still connected.
     */
    public boolean isConnected() {
        return session.isOpen();
    }

    /**
     * Closes the connection to this client. {@code onDisconnect} follows on the client's session thread.
     */
    public void disconnect() {
        session.close();
    }
}
