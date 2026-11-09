package io.github.trimax.raven.core.internal.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.internal.session.Session;
import io.github.trimax.raven.core.internal.session.SessionListener;
import io.github.trimax.raven.core.internal.session.SessionSettings;
import io.github.trimax.raven.core.internal.session.SilentListener;
import io.github.trimax.raven.core.internal.util.ThreadUtil;
import lombok.RequiredArgsConstructor;

/**
 * Unit tests for {@link SessionSlot}: at most one session at a time, and a new one starts only after the previous
 * one has ended.
 */
class SessionSlotTest {

    private static final SessionSettings SETTINGS = new SessionSettings(new JavaSerializationCodec(), Duration.ofSeconds(5),
            Duration.ZERO, 16, Duration.ofSeconds(5), 1024 * 1024, Duration.ofSeconds(5));

    private final ExecutorService threads = ThreadUtil.newExecutor();
    private final SessionSlot slot = new SessionSlot(threads);
    private final List<String> events = new CopyOnWriteArrayList<>();
    private ServerSocket listener;

    @BeforeEach
    void listen() throws IOException {
        listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        // Answers every connection with a server session, so the slot's sessions complete their handshake
        ThreadUtil.execute(threads, "test-accept", () -> {
            while (!listener.isClosed()) {
                try {
                    Session.accepted(listener.accept(), SETTINGS, new SilentListener()).start(threads);
                } catch (final IOException _) {
                    return;
                }
            }
        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        slot.close().get(2, TimeUnit.SECONDS);
        listener.close();
    }

    @Test
    void openWhileOpenIsANoOp() throws Exception {
        final var made = new AtomicInteger();

        slot.open(() -> dial("1", made)).get(2, TimeUnit.SECONDS);
        final var again = slot.open(() -> dial("2", made));

        assertTrue(again.isDone());
        assertEquals(1, made.get());
        assertTrue(slot.isOpen());
        assertEquals(List.of("open 1"), events);
    }

    @Test
    void newSessionStartsOnlyAfterPreviousEnded() throws Exception {
        final var made = new AtomicInteger();
        slot.open(() -> dial("1", made, Duration.ofMillis(200))).get(2, TimeUnit.SECONDS);

        final var closing = slot.close();
        slot.open(() -> dial("2", made)).get(2, TimeUnit.SECONDS);

        assertTrue(closing.isDone());
        assertEquals(List.of("open 1", "close 1", "open 2"), events);
        assertTrue(slot.isOpen());
    }

    @Test
    void sessionClosedBeforeItStartedEndsWithoutCallbacks() throws Exception {
        final var made = new AtomicInteger();
        final var release = new CountDownLatch(1);
        slot.open(() -> dialBlockingClose("1", release)).get(2, TimeUnit.SECONDS);
        slot.close();

        // Waits for the first session, which cannot end before the latch is released
        final var opened = slot.open(() -> dial("2", made));
        final var ended = slot.close();
        assertFalse(ended.isDone());
        release.countDown();

        ended.get(2, TimeUnit.SECONDS);
        assertTrue(opened.isDone());
        assertFalse(slot.isOpen());
        assertEquals(List.of("open 1", "close 1"), events);
    }

    @Test
    void closeWithoutSessionIsDone() {
        assertTrue(slot.close().isDone());
        assertFalse(slot.isOpen());
        assertDoesNotThrow(() -> slot.send(new byte[]{1}));
    }

    private Session dial(final String name, final AtomicInteger made) {
        return dial(name, made, Duration.ZERO);
    }

    private Session dial(final String name, final AtomicInteger made, final Duration closeDelay) {
        made.incrementAndGet();
        return Session.dialing("localhost", listener.getLocalPort(), SETTINGS, new Recorder(name, () -> sleep(closeDelay)));
    }

    private Session dialBlockingClose(final String name, final CountDownLatch release) {
        return Session.dialing("localhost", listener.getLocalPort(), SETTINGS, new Recorder(name, () -> awaitQuietly(release)));
    }

    private static void sleep(final Duration duration) {
        try {
            Thread.sleep(duration);
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
     * Records open and close of the slot's sessions; {@code onClose} runs {@code beforeClose} first.
     */
    @RequiredArgsConstructor
    private final class Recorder implements SessionListener {
        private final String name;
        private final Runnable beforeClose;

        @Override
        public boolean onOpen(final Session session) {
            events.add("open " + name);
            return true;
        }

        @Override
        public boolean onMessage(final Session session, final Message message) {
            return true;
        }

        @Override
        public void onClose(final Session session) {
            beforeClose.run();
            events.add("close " + name);
        }
    }
}
