package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Opens {@link Connection}s for tests that do not care about the send queue settings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestConnections {

    static final int QUEUE_CAPACITY = 1024;
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    static Connection open(final Socket socket) throws IOException {
        return new Connection(socket, QUEUE_CAPACITY, SEND_TIMEOUT);
    }
}
