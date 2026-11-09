package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import lombok.RequiredArgsConstructor;

/**
 * Verifies on which thread and in which order {@link RavenClient} runs its handler callbacks, and that callbacks
 * may call back into the client without deadlocking it.
 */
class RavenClientCallbackTest {

    private static final Runnable NOTHING = () -> {
    };

    private final AtomicReference<Client> serverSide = new AtomicReference<>();
    private final AtomicInteger serverConnects = new AtomicInteger();
    private RavenServer server;

    @BeforeEach
    void setUp() {
        server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        serverSide.set(client);
                        // Only the first connection gets a greeting, so later sessions stay quiet
                        if (serverConnects.incrementAndGet() == 1)
                            client.send(new TestMessage("hello"));
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
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void callbacksRunInOrderOnTheReceiveThread() {
        final var handler = new RecordingHandler(NOTHING, NOTHING, NOTHING);
        final var client = newClient(handler);

        client.connect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> handler.events.size() == 2);
        client.disconnect();

        assertEquals(List.of("connect", "message", "disconnect"), handler.events);
        assertEquals(1, handler.threads.stream().distinct().count(), "all callbacks of a session run on one thread");
        assertEquals("raven-receiver", handler.threads.getFirst().getName());
    }

    @Test
    void connectAndDisconnectReturnAfterTheirCallbacks() {
        final var connected = new AtomicBoolean();
        final var disconnected = new AtomicBoolean();
        final var client = newClient(new RecordingHandler(
                () -> sleepThenSet(connected), () -> sleepThenSet(disconnected), NOTHING));

        client.connect();
        assertTrue(connected.get(), "connect() returned before onConnect ran");

        client.disconnect();
        assertTrue(disconnected.get(), "disconnect() returned before onDisconnect ran");
    }

    @Test
    void fastReconnectWhileOldSessionIsEndingReportsBothCallbacksInOrder() throws Exception {
        final var inMessage = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var handler = new RecordingHandler(NOTHING, NOTHING, () -> {
            inMessage.countDown();
            awaitQuietly(release);
        });
        final var client = newClient(handler);
        client.connect();

        // The old session is stuck in onMessage, so its receive thread cannot report onDisconnect yet
        assertTrue(inMessage.await(2, TimeUnit.SECONDS));
        final var disconnecting = CompletableFuture.runAsync(client::disconnect);
        await().atMost(2, TimeUnit.SECONDS).until(() -> !client.isConnected());

        // The old connection is closed but its session has not ended: the window in which a reconnect used to win
        final var reconnecting = CompletableFuture.runAsync(client::connect);
        Thread.sleep(200);
        assertFalse(reconnecting.isDone(), "connect() must wait for the new session");
        assertFalse(client.isConnected(), "the new session must not connect before the old one has ended");
        assertEquals(List.of("connect", "message"), handler.events, "the new session must wait for the old one to end");

        release.countDown();
        reconnecting.get(2, TimeUnit.SECONDS);
        disconnecting.get(2, TimeUnit.SECONDS);
        assertEquals(List.of("connect", "message", "disconnect", "connect"), handler.events);

        client.disconnect();
        assertEquals(List.of("connect", "message", "disconnect", "connect", "disconnect"), handler.events);
    }

    @Test
    void onConnectMayWaitForAnotherThreadUsingTheClient() {
        final var client = new AtomicReference<RavenClient>();
        final var helperSawConnected = new AtomicBoolean();
        final var handler = new RecordingHandler(() -> {
            // The client must not hold its lock during the callback, or this would deadlock
            final var helper = CompletableFuture.supplyAsync(() -> {
                client.get().send(new TestMessage("from helper"));
                return client.get().isConnected();
            });
            helperSawConnected.set(assertDoesNotThrow(() -> helper.get(2, TimeUnit.SECONDS)));
        }, NOTHING, NOTHING);
        client.set(newClient(handler));

        assertTimeoutPreemptively(Duration.ofSeconds(5), client.get()::connect);
        assertTrue(helperSawConnected.get());
        await().atMost(2, TimeUnit.SECONDS).until(() -> handler.events.size() == 2);
        client.get().disconnect();
        assertEquals(List.of("connect", "message", "disconnect"), handler.events);
    }

    @Test
    void disconnectDuringOnConnectReportsOneDisconnectAfterIt() throws Exception {
        final var client = new AtomicReference<RavenClient>();
        final var disconnecting = new CompletableFuture<Void>();
        final var handler = new RecordingHandler(() -> {
            CompletableFuture.runAsync(() -> client.get().disconnect())
                    .whenComplete((_, failure) -> disconnecting.complete(null));
            // disconnect() on the other thread closes the connection while this callback still runs
            await().atMost(2, TimeUnit.SECONDS).until(() -> !client.get().isConnected());
        }, NOTHING, NOTHING);
        client.set(newClient(handler));

        client.get().connect();
        disconnecting.get(2, TimeUnit.SECONDS);

        assertFalse(client.get().isConnected());
        assertEquals(List.of("connect", "disconnect"), handler.events);
    }

    @Test
    void connectFromOnDisconnectReconnects() {
        final var client = new AtomicReference<RavenClient>();
        final var reconnects = new AtomicInteger();
        final var onDisconnectReturned = new AtomicInteger();
        final var handler = new RecordingHandler(NOTHING, () -> {
            // The usual auto-reconnect pattern; must neither deadlock nor wait for the new session's onConnect
            if (reconnects.incrementAndGet() == 1)
                client.get().connect();
            onDisconnectReturned.incrementAndGet();
        }, NOTHING);
        client.set(newClient(handler));
        client.get().connect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> handler.events.size() == 2);

        // The server drops the client, so the session ends on its own
        serverSide.get().disconnect();

        await().atMost(5, TimeUnit.SECONDS).until(() -> handler.events.size() == 4);
        assertEquals(1, onDisconnectReturned.get());
        assertTrue(client.get().isConnected());
        assertEquals(List.of("connect", "message", "disconnect", "connect"), handler.events);

        client.get().disconnect();
        assertEquals(List.of("connect", "message", "disconnect", "connect", "disconnect"), handler.events);
        assertEquals(2, onDisconnectReturned.get());
    }

    @Test
    void failingOnConnectGetsNoOnDisconnect() {
        final var handler = new RecordingHandler(() -> {
            throw new IllegalStateException("handler failure");
        }, NOTHING, NOTHING);
        final var client = newClient(handler);

        client.connect();
        assertFalse(client.isConnected());

        client.disconnect();
        assertEquals(List.of("connect"), handler.events);
    }

    private RavenClient newClient(final ClientHandler handler) {
        return new RavenClient(RavenClientConfiguration.builder()
                .host("localhost")
                .port(server.getPort())
                .handler(handler)
                .build());
    }

    private static void sleepThenSet(final AtomicBoolean flag) {
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        flag.set(true);
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Records each callback and its thread on entry, then runs the test's hook for it.
     */
    @RequiredArgsConstructor
    private static final class RecordingHandler implements ClientHandler {

        private final List<String> events = new CopyOnWriteArrayList<>();
        private final List<Thread> threads = new CopyOnWriteArrayList<>();
        private final Runnable connectHook;
        private final Runnable disconnectHook;
        private final Runnable messageHook;

        @Override
        public void onConnect() {
            record("connect");
            connectHook.run();
        }

        @Override
        public void onDisconnect() {
            record("disconnect");
            disconnectHook.run();
        }

        @Override
        public void onMessage(final Message message) {
            record("message");
            messageHook.run();
        }

        private void record(final String event) {
            events.add(event);
            threads.add(Thread.currentThread());
        }
    }
}
