package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.util.ThreadUtil;

/**
 * Unit tests for {@link SessionGroup}: its IDs are the announced sessions only, closeAll() completes once every
 * session has ended, and a session added after it is closed at once.
 */
class SessionGroupTest {

    private static final SessionSettings SETTINGS = new SessionSettings(new JavaSerializationCodec(), Duration.ofSeconds(5),
            Duration.ZERO, 16, Duration.ofSeconds(5), 1024 * 1024, Duration.ofSeconds(5));

    private final ExecutorService threads = ThreadUtil.newExecutor();
    private final SessionGroup group = new SessionGroup(threads);
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<Session> peers = new ArrayList<>();
    private ServerSocket listener;

    @BeforeEach
    void listen() throws IOException {
        listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void cleanUp() throws Exception {
        group.closeAll().get(2, TimeUnit.SECONDS);
        peers.forEach(Session::close);
        listener.close();
    }

    @Test
    void idsReflectOnlyAnnouncedSessions() throws Exception {
        final var inOpen = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        // This peer never sends its hello, so its session stays in the handshake
        try (final var silent = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
            final var handshaking = accept(new Recorder());
            RawPeer.readHello(silent.getInputStream());

            dialPeer();
            final var announced = accept(new Recorder() {
                @Override
                public boolean onOpen(final Session session) {
                    inOpen.countDown();
                    awaitQuietly(release);
                    return super.onOpen(session);
                }
            });

            assertTrue(inOpen.await(2, TimeUnit.SECONDS));
            assertEquals(Set.of(announced.id()), group.ids(), "an announced session is listed while onOpen runs");
            assertNull(group.get(handshaking.id()));
            assertSame(announced, group.get(announced.id()));
            release.countDown();

            announced.close();
            assertTrue(group.ids().isEmpty(), "a closed session is no longer listed");
            assertNull(group.get(announced.id()));
        }
    }

    @Test
    void closeAllCompletesOnceEverySessionHasEnded() throws Exception {
        for (int i = 0; i < 3; i++) {
            dialPeer();
            accept(new Recorder() {
                @Override
                public void onClose(final Session session) {
                    sleep(200);
                    super.onClose(session);
                }
            });
        }
        await().atMost(2, TimeUnit.SECONDS).until(() -> group.ids().size() == 3);

        group.closeAll().get(2, TimeUnit.SECONDS);

        assertEquals(3, events.stream().filter("close"::equals).count());
        assertTrue(group.ids().isEmpty());
    }

    @Test
    void sessionAddedAfterCloseAllIsClosedWithoutCallbacks() throws Exception {
        group.closeAll().get(2, TimeUnit.SECONDS);

        dialPeer();
        final var late = accept(new Recorder());

        late.ended().get(2, TimeUnit.SECONDS);
        assertTrue(late.opened().isDone());
        assertTrue(events.isEmpty());
        assertTrue(group.ids().isEmpty());
    }

    private Session accept(final SessionListener listener) throws IOException {
        final var session = Session.accepted(this.listener.accept(), SETTINGS, listener);
        group.add(session);
        return session;
    }

    private void dialPeer() {
        final var peer = Session.dialing("localhost", listener.getLocalPort(), SETTINGS, new SilentListener());
        peers.add(peer);
        peer.start(threads);
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
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
     * Records the open and close of a group's session.
     */
    private class Recorder implements SessionListener {
        @Override
        public boolean onOpen(final Session session) {
            events.add("open");
            return true;
        }

        @Override
        public boolean onMessage(final Session session, final Message message) {
            return true;
        }

        @Override
        public void onClose(final Session session) {
            events.add("close");
        }
    }
}
