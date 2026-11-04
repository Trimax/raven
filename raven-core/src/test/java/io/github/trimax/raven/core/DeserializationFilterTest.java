package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the network input filter only admits {@link Message} subclasses and safe JDK types.
 */
class DeserializationFilterTest {

    /**
     * A listening socket with a server-side Connection pending accept, plus the raw client socket.
     * The server Connection only completes once the client side has written its stream header.
     */
    private static final class Pair implements AutoCloseable {
        private final ServerSocket listener;
        private final Socket rawClient;
        private final CompletableFuture<Connection> serverFuture;

        Pair() throws Exception {
            listener = new ServerSocket(0);
            serverFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new Connection(listener.accept());
                } catch (final Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            rawClient = new Socket("localhost", listener.getLocalPort());
        }

        Connection server() throws Exception {
            return serverFuture.get();
        }

        Socket rawClient() {
            return rawClient;
        }

        @Override
        public void close() throws Exception {
            rawClient.close();
            if (serverFuture.isDone() && !serverFuture.isCompletedExceptionally())
                serverFuture.get().disconnect();
            listener.close();
        }
    }

    @Test
    void messageSubclassIsAccepted() throws Exception {
        try (final var pair = new Pair()) {
            final var client = new Connection(pair.rawClient());
            final var server = pair.server();

            client.send(new TestMessage("hello"));
            final var received = server.receive();

            assertInstanceOf(TestMessage.class, received);
            assertEquals("hello", ((TestMessage) received).getContent());
            client.disconnect();
        }
    }

    @Test
    void nonMessageObjectIsRejected() throws Exception {
        try (final var pair = new Pair()) {
            final var out = new ObjectOutputStream(pair.rawClient().getOutputStream());
            final var server = pair.server();

            out.writeObject(new Date());
            out.flush();

            assertNull(server.receive());
            assertFalse(server.isConnected());
        }
    }
}
