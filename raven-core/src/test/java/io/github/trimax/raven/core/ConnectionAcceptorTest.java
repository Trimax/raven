package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that an accept loop ends with its own server socket, so restarting a server does not leave
 * the previous loop spinning on a closed socket, and that it backs off while accept() keeps failing.
 */
class ConnectionAcceptorTest {

    private static final RavenServerConfiguration CONFIGURATION = RavenServerConfiguration.builder()
            .port(0)
            .handler(new ServerHandler() {
                @Override
                public void onConnect(final Client client) {
                }

                @Override
                public void onDisconnect(final Client client) {
                }

                @Override
                public void onMessage(final Client sender, final Message message) {
                }
            })
            .handshakeTimeout(Duration.ofSeconds(1))
            .build();

    @Test
    void acceptLoopEndsWhenItsSocketIsClosedWhileServerIsRunningAgain() throws IOException, InterruptedException {
        // The owning server reports running throughout, as it does after stop() and start()
        final var acceptor = new ConnectionAcceptor(CONFIGURATION, () -> true, (_, _) -> {
        });

        final var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        final var acceptThread = acceptor.start(socket);
        socket.close();

        acceptThread.join(Duration.ofSeconds(2));
        assertFalse(acceptThread.isAlive(), "accept loop must end once its server socket is closed");
    }

    @Test
    void restartedServerAcceptsClients() {
        final var connected = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        connected.add(client);
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                    }
                })
                .build());

        server.start();
        server.stop();
        server.start();

        try {
            final var client = new RavenClient(RavenClientConfiguration.builder()
                    .host("localhost")
                    .port(server.getPort())
                    .handler(new ClientHandler() {
                        @Override
                        public void onConnect() {
                        }

                        @Override
                        public void onDisconnect() {
                        }

                        @Override
                        public void onMessage(final Message message) {
                        }
                    })
                    .build());
            client.connect();

            await().atMost(2, TimeUnit.SECONDS).until(() -> connected.size() == 1);
            assertEquals(1, server.getClients().size());

            client.disconnect();
        } finally {
            server.stop();
        }
    }

    @Test
    void backsOffWhileAcceptFailsAndRecovers() throws Exception {
        final int failing = 5;
        final var accepted = new CopyOnWriteArrayList<Client>();
        final var acceptor = new ConnectionAcceptor(CONFIGURATION, () -> true, (_, client) -> accepted.add(client));

        try (final var socket = new FailingServerSocket(failing)) {
            acceptor.start(socket);
            // Becomes acceptable once the failures are over: the backlog holds the connection until then
            try (final var peer = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort())) {
                new ObjectOutputStream(peer.getOutputStream()).flush();

                await().atMost(5, TimeUnit.SECONDS).until(() -> accepted.size() == 1);
                accepted.getFirst().disconnect();
            }

            // The failures, the successful accept() and possibly the next, still blocked one
            final var attempts = socket.attempts;
            assertTrue(attempts.size() <= failing + 2, "accept() must not be retried without a pause: " + attempts.size());
            // 10, 20, 40, 80 and 160 ms between the attempts; Thread.sleep never wakes early
            for (int i = 1; i <= failing; i++) {
                final long gapMs = TimeUnit.NANOSECONDS.toMillis(attempts.get(i) - attempts.get(i - 1));
                final long expectedMs = 10L << (i - 1);
                assertTrue(gapMs >= expectedMs - 1, "attempt " + i + " came after " + gapMs + "ms, expected " + expectedMs + "ms");
            }
        }
    }

    @Test
    void closingDuringBackoffEndsLoopPromptly() throws Exception {
        final var acceptor = new ConnectionAcceptor(CONFIGURATION, () -> true, (_, _) -> {
        });

        final var socket = new FailingServerSocket(Integer.MAX_VALUE);
        final var acceptThread = acceptor.start(socket);
        // After 8 failures the delay has reached its 1 s cap
        await().atMost(5, TimeUnit.SECONDS).until(() -> socket.attempts.size() >= 8);

        socket.close();
        assertTrue(acceptThread.join(Duration.ofMillis(1500)), "accept loop must end within one backoff delay of a close");
    }

    /** A loopback server socket whose first {@code failures} accept() calls fail; records when each call came. */
    private static final class FailingServerSocket extends ServerSocket {
        private final AtomicInteger remainingFailures;
        private final List<Long> attempts = new CopyOnWriteArrayList<>();

        FailingServerSocket(final int failures) throws IOException {
            super(0, 0, InetAddress.getLoopbackAddress());
            this.remainingFailures = new AtomicInteger(failures);
        }

        @Override
        public Socket accept() throws IOException {
            attempts.add(System.nanoTime());
            if (remainingFailures.getAndDecrement() > 0)
                throw new IOException("simulated accept failure");

            return super.accept();
        }
    }
}
