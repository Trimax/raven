package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.junit.jupiter.api.Test;

/**
 * Verifies that a {@link Connection} that fails during construction closes the socket it was given.
 */
class ConnectionSetupFailureTest {

    @Test
    void closesSocketWhenStreamSetupFails() {
        final var socket = new FailingInputSocket();

        assertThrows(IOException.class, () -> TestConnections.open(socket));
        assertTrue(socket.isClosed(), "socket must be closed when Connection construction fails");
    }

    /**
     * A socket whose input stream cannot be obtained, simulating a failure during stream setup.
     */
    private static final class FailingInputSocket extends Socket {

        @Override
        public synchronized void close() throws IOException {
            super.close();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("simulated input stream failure");
        }
    }
}
