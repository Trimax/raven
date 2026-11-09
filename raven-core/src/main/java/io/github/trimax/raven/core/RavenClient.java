package io.github.trimax.raven.core;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ClientHandler}.
 *
 * <p>Every connection is a {@link Session} with its own virtual thread, named {@code raven-receiver}, and all handler
 * callbacks of that connection run on it in order: {@code onConnect}, then {@code onMessage} for each received
 * message, then {@code onDisconnect}. {@code onDisconnect} is called exactly once for every {@code onConnect} that
 * returned normally, however the session ended; a failing {@code onConnect} closes the connection and gets no
 * {@code onDisconnect}. The callbacks of a new connection start only after {@code onDisconnect} of the previous one
 * has returned.
 *
 * <p>No callback runs while the client holds its internal lock, so a callback may call any method of the client and
 * may wait for other threads that do. This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenClient {

    @NonNull
    private final RavenClientConfiguration configuration;

    private final SessionSettings settings;

    /**
     * Runs the session and writer threads. Never closed: its virtual threads end with their session, and an idle
     * executor holds nothing, so it needs no shutdown.
     */
    private final ExecutorService threads = TransportThreads.newExecutor();

    /** Guards {@link #session} updates. Never held while a callback runs or I/O blocks. */
    private final Object lock = new Object();

    /**
     * The latest session, possibly still connecting or already closed, or {@code null} before the first connect().
     * Written under {@link #lock}; volatile because {@link #send(Message)} and {@link #isConnected()} read it without.
     */
    private volatile Session session;

    public RavenClient(@NonNull final RavenClientConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
        this.settings = SessionSettings.of(configuration);
    }

    /**
     * Connects to the server. Does nothing if already connected or if another connection attempt is in progress.
     * <p>
     * Network I/O runs on the session thread, so {@link #isConnected()}, {@link #send(Message)} and
     * {@link #disconnect()} remain responsive while a connection attempt is blocked.
     * {@link #disconnect()} aborts an attempt in progress.
     * <p>
     * Returns once {@code onConnect} of the new connection has run, which is after {@code onDisconnect} of the
     * previous one, or once the attempt has failed. Called from a callback of this client, it returns without
     * waiting, since the new connection's callbacks cannot start before the current callback returns.
     */
    public void connect() {
        final Session started;
        synchronized (lock) {
            final var previous = session;
            if (previous != null && !previous.isClosed())
                return;

            // The previous session may still be ending: its socket has closed, but its thread has not reported
            // onDisconnect yet. The new session waits for it, so the callbacks never overlap or reorder
            final var previousEnded = previous == null ? CompletableFuture.<Void>completedFuture(null) : previous.ended();
            // Resolved on the session thread, so a slow DNS lookup does not block the caller under the lock
            final var target = InetSocketAddress.createUnresolved(configuration.getHost(), configuration.getPort());
            started = Session.dialing(target, settings, new Callbacks(previousEnded));
            session = started;
            started.start(threads);
        }

        awaitUnlessCallback(started.opened());
    }

    /**
     * Returns whether the client is currently connected to the server.
     */
    public boolean isConnected() {
        final var current = session;
        return current != null && current.isOpen();
    }

    /**
     * Disconnects from the server, aborting a connection attempt in progress. Safe to call multiple times.
     * <p>
     * Returns once {@code onDisconnect} of the closed connection has run. Called from a callback of this client, it
     * returns without waiting; {@code onDisconnect} then follows once the current callback returns.
     */
    public void disconnect() {
        final Session current;
        synchronized (lock) {
            current = session;
            // Closing the socket ends the session, whose thread then reports onDisconnect; interrupting it is
            // not needed and would leave the interrupt flag set inside onDisconnect
            if (current != null)
                current.close();
        }

        if (current != null)
            awaitUnlessCallback(current.ended());
    }

    /**
     * Sends a message to the server. Thread-safe.
     *
     * <p>The message is serialized on the calling thread before this method returns: later changes to it are not
     * sent. Delivery is asynchronous: the call returns once the message is queued for the connection's writer.
     * If that queue is full, it blocks for up to the configured {@code sendTimeout} (5 seconds by default) and then disconnects the stalled peer.
     * Write errors are logged and close the connection rather than being thrown; messages still queued
     * when a connection is closed are dropped.
     *
     * @param message the message to send
     * @throws IllegalArgumentException if the message cannot be serialized or exceeds {@code maxMessageSize}
     */
    public void send(final Message message) {
        // Read the field once, so that a concurrent reconnect cannot swap the session mid-call
        final var current = session;
        if (current == null || !current.isOpen()) {
            log.warn("Cannot send message: not connected");
            return;
        }

        MessageValidator.validateOrThrow(message);
        final var frame = settings.encode(message);
        MeasurementUtil.measure(() -> current.send(frame),
                duration -> log.debug("Message queued in {}ms", duration.toMillis()));
    }

    /**
     * Waits for a session milestone, unless the calling thread is a session thread of this client: that thread
     * itself has to return from its callback before any milestone of this client can be reached.
     */
    private void awaitUnlessCallback(final CompletableFuture<Void> milestone) {
        if (TransportThreads.callbackListener() instanceof Callbacks callbacks && callbacks.client() == this)
            return;

        try {
            milestone.get();
        } catch (final InterruptedException _) {
            // Stop waiting; the session carries on and its callbacks still run
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ex) {
            // Never completed exceptionally
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Reports one session to the handler.
     */
    @RequiredArgsConstructor
    private final class Callbacks implements SessionListener {

        /** Completes once the previous session has ended; this session's callbacks wait for it. */
        private final CompletableFuture<Void> previousEnded;

        private final InboundPipeline inbound = InboundPipeline.of(configuration);

        RavenClient client() {
            return RavenClient.this;
        }

        @Override
        public boolean onOpen(final Session session) {
            previousEnded.join();
            // Closed by disconnect() or the peer before it was ever announced: there is nothing to report
            if (!session.isOpen())
                return false;

            log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());
            return HandlingUtil.callHandler(configuration.getHandler()::onConnect, () -> "on connect, closing the connection");
        }

        @Override
        public boolean onMessage(final Session session, final Message message) {
            return inbound.deliver(message);
        }

        @Override
        public void onClose(final Session session) {
            inbound.summarize();
            log.info("RavenClient disconnected");
            HandlingUtil.callHandler(configuration.getHandler()::onDisconnect, () -> "on disconnect");
        }
    }
}
