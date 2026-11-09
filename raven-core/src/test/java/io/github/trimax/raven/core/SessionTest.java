package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.codec.JavaSerializationCodec;

/**
 * Unit tests for {@link Session}: one thread runs every callback in order, and closing from any thread at any point
 * of the life cycle ends the session with at most one {@code onClose}, and only after a successful {@code onOpen}.
 */
class SessionTest {

    private static final SessionSettings SETTINGS = settings(Duration.ZERO, 1024, Duration.ofSeconds(5));

    private final ExecutorService threads = TransportThreads.newExecutor();
    private ServerSocket listener;

    @BeforeEach
    void listen() throws IOException {
        listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void cleanUp() throws IOException {
        listener.close();
        // Every test ends its sessions, so this returns at once unless a session thread leaked
        threads.close();
    }

    @Test
    void oneThreadRunsAllCallbacksInOrder() throws Exception {
        final var server = new Recorder();
        final var accepted = acceptAsync(server);
        final var dialing = dial(new Recorder());
        dialing.opened().get(2, TimeUnit.SECONDS);

        dialing.send(SETTINGS.encode(new TestMessage("first")));
        dialing.send(SETTINGS.encode(new TestMessage("second")));
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.events.size() == 3);
        dialing.close();
        accepted.get().ended().get(2, TimeUnit.SECONDS);

        assertEquals(List.of("open", "message first", "message second", "close"), server.events);
        assertEquals(1, server.threads.stream().distinct().count(), "all callbacks of a session run on one thread");
        assertTrue(server.threads.getFirst().getName().startsWith("raven-client-"));
        assertFalse(accepted.get().isOpen());
    }

    @Test
    void closeDuringHandshakeEndsWithoutCallbacks() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder);
        // This peer never sends its hello, so the session waits in the handshake
        try (final var silent = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            RawPeer.readHello(silent.getInputStream());

            accepted.get().close();

            accepted.get().ended().get(2, TimeUnit.SECONDS);
            assertTrue(accepted.get().opened().isDone());
            assertTrue(recorder.events.isEmpty());
        }
    }

    @Test
    void closeDialingSessionAbortsTheHandshake() throws Exception {
        final var recorder = new Recorder();
        // Accepted at TCP level, but this side never answers the hello
        final var dialing = dial(recorder);
        try (final var ignored = listener.accept()) {
            Thread.sleep(100);
            dialing.close();

            dialing.ended().get(2, TimeUnit.SECONDS);
            assertTrue(recorder.events.isEmpty());
        }
    }

    @Test
    void closeDuringOnOpenReportsOneCloseAfterIt() throws Exception {
        final var inOpen = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var recorder = new Recorder() {
            @Override
            public boolean onOpen(final Session session) {
                super.onOpen(session);
                inOpen.countDown();
                awaitQuietly(release);
                return true;
            }
        };
        final var accepted = acceptAsync(recorder);
        final var dialing = dial(new Recorder());

        assertTrue(inOpen.await(2, TimeUnit.SECONDS));
        accepted.get().close();
        assertFalse(accepted.get().isOpen());
        release.countDown();

        accepted.get().ended().get(2, TimeUnit.SECONDS);
        assertEquals(List.of("open", "close"), recorder.events);
        dialing.close();
    }

    @Test
    void closeDuringReadAndPeerCloseReportOneClose() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder);
        final var dialing = dial(new Recorder());
        dialing.opened().get(2, TimeUnit.SECONDS);
        await().atMost(2, TimeUnit.SECONDS).until(() -> recorder.events.size() == 1);

        // Every close at once: the peer's, and several local ones
        dialing.close();
        for (int i = 0; i < 4; i++)
            Thread.ofVirtual().start(accepted.get()::close);

        accepted.get().ended().get(2, TimeUnit.SECONDS);
        assertEquals(List.of("open", "close"), recorder.events);
    }

    @Test
    void refusedOnOpenGetsNoOnClose() throws Exception {
        final var recorder = new Recorder() {
            @Override
            public boolean onOpen(final Session session) {
                super.onOpen(session);
                return false;
            }
        };
        final var accepted = acceptAsync(recorder);
        final var peerRecorder = new Recorder();
        final var dialing = dial(peerRecorder);

        accepted.get().ended().get(2, TimeUnit.SECONDS);
        assertEquals(List.of("open"), recorder.events);
        // The peer sees the connection close
        dialing.ended().get(2, TimeUnit.SECONDS);
    }

    @Test
    void falseFromOnMessageEndsTheSession() throws Exception {
        final var recorder = new Recorder();
        recorder.keepGoing = message -> false;
        final var accepted = acceptAsync(recorder);
        final var dialing = dial(new Recorder());
        dialing.opened().get(2, TimeUnit.SECONDS);

        dialing.send(SETTINGS.encode(new TestMessage("last")));

        accepted.get().ended().get(2, TimeUnit.SECONDS);
        assertEquals(List.of("open", "message last", "close"), recorder.events);
        dialing.ended().get(2, TimeUnit.SECONDS);
    }

    @Test
    void idleSessionIsClosed() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder, settings(Duration.ofMillis(200), 1024, Duration.ofSeconds(5)));
        final var dialing = dial(new Recorder());

        accepted.get().ended().get(3, TimeUnit.SECONDS);
        assertEquals(List.of("open", "close"), recorder.events);
        dialing.ended().get(2, TimeUnit.SECONDS);
    }

    @Test
    void foreignHelloEndsWithoutCallbacks() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder);
        try (final var peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            peer.getOutputStream().write(new byte[]{'H', 'T', 'T', 'P', ' ', '/', ' ', ' '});

            accepted.get().ended().get(2, TimeUnit.SECONDS);
            assertTrue(recorder.events.isEmpty());
        }
    }

    @Test
    void oversizeFrameClosesTheSession() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder);
        try (final var peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            peer.setSoTimeout(5_000);
            RawPeer.writeHello(peer.getOutputStream());
            RawPeer.readHello(peer.getInputStream());
            new DataOutputStream(peer.getOutputStream()).writeInt(SETTINGS.maxMessageSize() + 1);

            accepted.get().ended().get(2, TimeUnit.SECONDS);
            assertEquals(List.of("open", "close"), recorder.events);
            assertClosed(peer.getInputStream());
        }
    }

    @Test
    void stalledPeerIsDisconnected() throws Exception {
        final var recorder = new Recorder();
        final var accepted = acceptAsync(recorder, settings(Duration.ZERO, 1, Duration.ofMillis(200)));
        // The peer completes the handshake and then never reads, so the socket buffers fill up
        try (final var peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            RawPeer.writeHello(peer.getOutputStream());
            await().atMost(2, TimeUnit.SECONDS).until(() -> recorder.events.size() == 1);

            final var big = new byte[1 << 20];
            for (int i = 0; i < 64 && accepted.get().isOpen(); i++)
                accepted.get().send(big);

            assertFalse(accepted.get().isOpen(), "a peer that stops reading must be disconnected");
            accepted.get().ended().get(2, TimeUnit.SECONDS);
            assertEquals(List.of("open", "close"), recorder.events);
        }
    }

    @Test
    void sendBeforeOpenIsDropped() {
        final var session = Session.dialing(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1), SETTINGS, new Recorder());

        assertDoesNotThrow(() -> session.send(new byte[]{1}));
        session.close();
        assertTrue(session.isClosed());
    }

    private Session dial(final SessionListener listener) {
        final var session = Session.dialing(
                InetSocketAddress.createUnresolved("localhost", this.listener.getLocalPort()), SETTINGS, listener);
        session.start(threads);
        return session;
    }

    private Future<Session> acceptAsync(final SessionListener listener) {
        return acceptAsync(listener, SETTINGS);
    }

    private Future<Session> acceptAsync(final SessionListener listener, final SessionSettings settings) {
        return threads.submit(() -> {
            final var session = Session.accepted(this.listener.accept(), settings, listener);
            session.start(threads);
            return session;
        });
    }

    private static SessionSettings settings(final Duration idleTimeout, final int queueCapacity, final Duration sendTimeout) {
        return new SessionSettings(new JavaSerializationCodec(), Duration.ofSeconds(5), idleTimeout, queueCapacity,
                sendTimeout, 16 * 1024 * 1024, Duration.ofSeconds(5));
    }

    private static void assertClosed(final InputStream in) throws IOException {
        try {
            assertEquals(-1, in.read());
        } catch (final SocketException ex) {
            assertTrue(String.valueOf(ex.getMessage()).contains("reset"), "unexpected socket error: " + ex);
        }
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Records each callback and its thread.
     */
    private static class Recorder implements SessionListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        Predicate<Message> keepGoing = message -> true;

        @Override
        public boolean onOpen(final Session session) {
            record("open");
            return true;
        }

        @Override
        public boolean onMessage(final Session session, final Message message) {
            record("message " + ((TestMessage) message).getContent());
            return keepGoing.test(message);
        }

        @Override
        public void onClose(final Session session) {
            record("close");
        }

        private void record(final String event) {
            events.add(event);
            threads.add(Thread.currentThread());
        }
    }
}
