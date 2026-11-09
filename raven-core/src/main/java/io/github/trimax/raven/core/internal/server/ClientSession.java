package io.github.trimax.raven.core.internal.server;

import java.util.UUID;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.internal.session.Session;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The server side of one session, handed to the server's handler as its {@link Client}.
 *
 * <p>Internal: not part of the API.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ClientSession implements Client {

    private final Session session;

    @Override
    public UUID getId() {
        return session.id();
    }

    /**
     * Sends a message to this client. Thread-safe. The message is serialized on the calling thread, without
     * validation.
     *
     * <p>Public only for tests outside this package; handlers send through the server, not through {@link Client}.
     *
     * @throws IllegalArgumentException if the message cannot be serialized
     */
    public void send(final Message message) {
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
