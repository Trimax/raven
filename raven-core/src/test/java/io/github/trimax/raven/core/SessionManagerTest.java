package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Unit tests for {@link SessionManager}.
 */
class SessionManagerTest {

    private final List<Closeable> resources = new ArrayList<>();
    private final RecordingHandler handler = new RecordingHandler();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final SessionManager sessions = new SessionManager(handler, running::get);

    @AfterEach
    void closeResources() throws IOException {
        for (final var resource : resources)
            resource.close();
    }

    @Test
    void openThenCloseReportsDisconnectOnce() throws Exception {
        final var accepted = newClient();
        final var client = accepted.client();

        assertTrue(sessions.open(accepted.socket(), client));
        assertSame(client, sessions.get(client.getId()));

        sessions.close(client);
        sessions.close(client);

        assertEquals(List.of("connect " + client.getId(), "connect returned " + client.getId(),
                "disconnect " + client.getId()), handler.events);
        assertFalse(client.isConnected());
        assertTrue(sessions.ids().isEmpty());
    }

    @Test
    void closeAllRacingWithCloseReportsDisconnectOnce() throws Exception {
        for (int iteration = 0; iteration < 20; iteration++) {
            final var accepted = newClient();
        final var client = accepted.client();
            assertTrue(sessions.open(accepted.socket(), client));
            handler.events.clear();

            final var start = new CountDownLatch(1);
            final var closing = CompletableFuture.runAsync(() -> {
                awaitQuietly(start);
                sessions.close(client);
            });
            final var closingAll = CompletableFuture.runAsync(() -> {
                awaitQuietly(start);
                sessions.closeAll();
            });
            start.countDown();
            CompletableFuture.allOf(closing, closingAll).get(5, TimeUnit.SECONDS);

            assertEquals(List.of("disconnect " + client.getId()), handler.events);
        }
    }

    @Test
    void clientInOnConnectDuringCloseAllIsDisconnectedOnceAfterOnConnectReturns() throws Exception {
        final var accepted = newClient();
        final var client = accepted.client();
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        handler.onConnect = () -> {
            entered.countDown();
            awaitQuietly(release);
        };

        final var opened = CompletableFuture.supplyAsync(() -> sessions.open(accepted.socket(), client));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        running.set(false);
        sessions.closeAll();
        assertEquals(List.of("connect " + client.getId()), handler.events, "closeAll() must skip a client still in onConnect");

        release.countDown();
        assertFalse(opened.get(5, TimeUnit.SECONDS), "the receive loop must not start once the server stopped");

        assertEquals(List.of("connect " + client.getId(), "connect returned " + client.getId(),
                "disconnect " + client.getId()), handler.events);
        assertFalse(client.isConnected());
        assertTrue(sessions.ids().isEmpty());
    }

    @Test
    void failingOnDisconnectDoesNotStopCloseAll() throws Exception {
        final var reported = new CopyOnWriteArrayList<UUID>();
        final var failing = new SessionManager(new ServerHandler() {
            @Override
            public void onConnect(final Client client) {
            }

            @Override
            public void onDisconnect(final Client client) {
                reported.add(client.getId());
                throw new IllegalStateException("handler failure");
            }

            @Override
            public void onMessage(final Client sender, final Message message) {
            }
        }, running::get);
        final var accepted = List.of(newClient(), newClient(), newClient());
        for (final var each : accepted)
            assertTrue(failing.open(each.socket(), each.client()));

        running.set(false);
        assertDoesNotThrow(failing::closeAll);

        assertEquals(3, reported.size());
        for (final var each : accepted)
            assertFalse(each.client().isConnected());
    }

    @Test
    void failingOnConnectDisconnectsWithoutReportingDisconnect() throws Exception {
        final var accepted = newClient();
        final var client = accepted.client();
        handler.onConnect = () -> {
            throw new IllegalStateException("handler failure");
        };

        assertFalse(sessions.open(accepted.socket(), client));

        assertEquals(List.of("connect " + client.getId()), handler.events);
        assertFalse(client.isConnected());
        assertTrue(sessions.ids().isEmpty());
    }

    @Test
    void openAfterStopDoesNotAnnounceClient() throws Exception {
        final var accepted = newClient();
        final var client = accepted.client();
        running.set(false);

        assertFalse(sessions.open(accepted.socket(), client));

        assertTrue(handler.events.isEmpty(), "a client accepted after stop must not be announced");
        assertFalse(client.isConnected());
        assertTrue(sessions.ids().isEmpty());
    }

    /** Records the handler's events in order; {@link #onConnect} runs inside {@code onConnect}. */
    private static final class RecordingHandler implements ServerHandler {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private volatile Runnable onConnect = () -> { };

        @Override
        public void onConnect(final Client client) {
            events.add("connect " + client.getId());
            onConnect.run();
            events.add("connect returned " + client.getId());
        }

        @Override
        public void onDisconnect(final Client client) {
            events.add("disconnect " + client.getId());
        }

        @Override
        public void onMessage(final Client sender, final Message message) {
        }
    }
    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** A client and the socket it was accepted on. */
    private record Accepted(Socket socket, Client client) {
    }

    /**
     * Creates a connected client over loopback. The peer side writes the stream header the client expects.
     */
    private Accepted newClient() throws IOException {
        final var server = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        resources.add(server);
        final var socket = new Socket("localhost", server.getLocalPort());
        resources.add(socket);
        final var peer = server.accept();
        resources.add(peer);

        new ObjectOutputStream(peer.getOutputStream()).flush();
        return new Accepted(socket, new Client(TestConnections.open(socket)));
    }
}
