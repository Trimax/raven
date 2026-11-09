package io.github.trimax.raven.core;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The server side of one session, handed to the server's handler as its {@link Client}.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ClientSession implements Client {

    private final Session session;

    @Override
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
        session.send(session.settings().encode(message));
    }

    @Override
    public boolean isConnected() {
        return session.isOpen();
    }

    @Override
    public void disconnect() {
        session.close();
    }
}
