package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Opens {@link Connection}s for tests that do not care about the handshake and send queue settings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestConnections {

    static final int QUEUE_CAPACITY = 1024;
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Opens a started connection, as the server and the client do.
     */
    static Connection open(final Socket socket) throws IOException {
        return Connection.open(socket, HANDSHAKE_TIMEOUT, QUEUE_CAPACITY, SEND_TIMEOUT);
    }
}
