package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.InterceptorUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ClientHandler}.
 *
 * <p>Every connection gets its own virtual receive thread, named {@code raven-receiver}, and all handler callbacks of
 * that connection run on it in order: {@code onConnect}, then {@code onMessage} for each received message, then
 * {@code onDisconnect}. {@code onDisconnect} is called exactly once for every {@code onConnect} that returned
 * normally, however the session ended; a failing {@code onConnect} closes the connection and gets no
 * {@code onDisconnect}. The callbacks of a new connection start only after {@code onDisconnect} of the previous one
 * has returned.
 *
 * <p>No callback runs while the client holds its internal lock, so a callback may call any method of the client and
 * may wait for other threads that do. This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenClient {

    /** Bound on the receive threads of a client, so that its methods called from a callback do not wait for it. */
    private static final ScopedValue<RavenClient> CALLBACK_CLIENT = ScopedValue.newInstance();

    @NonNull
    private final RavenClientConfiguration configuration;

    /** Guards {@link #session} updates and {@link #pendingConnect}. Never held while a callback runs or I/O blocks. */
    private final Object lock = new Object();

    /**
     * Current session, or {@code null}. Written under {@link #lock}; volatile because {@link #send(Message)} reads it
     * without the lock. A session whose connection has closed stays here until its receive thread detaches it.
     */
    private volatile Session session;

    /**
     * The connection attempt in progress, aborted by {@link #disconnect()}. Guarded by {@link #lock}.
     */
    private final PendingConnect pendingConnect = new PendingConnect();

    /**
     * One connection and the progress of its callbacks.
     *
     * @param connection the connection
     * @param previous   completes once the previous session has ended; this session's callbacks wait for it
     * @param announced  completes once {@code onConnect} has returned or failed
     * @param ended      completes once the session is over: {@code onDisconnect} has returned, or was never due
     */
    private record Session(Connection connection, CompletableFuture<Void> previous,
                           CompletableFuture<Void> announced, CompletableFuture<Void> ended) {

        Session(final Connection connection, final CompletableFuture<Void> previous) {
            this(connection, previous, new CompletableFuture<>(), new CompletableFuture<>());
        }
    }

    public RavenClient(@NonNull final RavenClientConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
    }

    /**
     * Connects to the server. Does nothing if already connected or if another connection attempt is in progress.
     * <p>
     * Network I/O runs outside the client's lock, so {@link #isConnected()}, {@link #send(Message)} and
     * {@link #disconnect()} remain responsive while a connection attempt is blocked.
     * {@link #disconnect()} aborts an attempt in progress.
     * <p>
     * Returns once {@code onConnect} of the new connection has run, which is after {@code onDisconnect} of the
     * previous one. Called from a callback of this client, it returns without waiting, since the new connection's
     * callbacks cannot start before the current callback returns.
     */
    public void connect() {
        final PendingConnect.Attempt attempt;
        synchronized (lock) {
            attempt = isConnected() ? null : pendingConnect.begin();
        }
        if (attempt == null)
            return;

        final Connection newConnection;
        try {
            newConnection = openConnection(attempt.socket());
        } catch (final IOException ex) {
            if (finishAttempt(attempt))
                log.warn("RavenClient failed to connect to {}:{} - {}", configuration.getHost(), configuration.getPort(), ex.getMessage());
            else
                logCancelled();
            return;
        } catch (final RuntimeException ex) {
            // Otherwise every later connect() would see an attempt in progress and return
            finishAttempt(attempt);
            throw ex;
        }

        final Session started;
        synchronized (lock) {
            if (!pendingConnect.finish(attempt)) {
                // disconnect() was called while the connection was being opened
                newConnection.disconnect();
                logCancelled();
                return;
            }

            // The previous session may still be ending: its connection has closed, but its receive thread has not
            // reported onDisconnect yet. The new session waits for it, so the callbacks never overlap or reorder
            final var previous = session == null ? CompletableFuture.<Void>completedFuture(null) : session.ended();
            started = new Session(newConnection, previous);
            session = started;
            log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());

            Thread.ofVirtual().name("raven-receiver").start(() ->
                    ScopedValue.where(CALLBACK_CLIENT, this).run(() -> runSession(started)));
        }

        awaitUnlessCallback(started.announced());
    }

    private boolean finishAttempt(final PendingConnect.Attempt attempt) {
        synchronized (lock) {
            return pendingConnect.finish(attempt);
        }
    }

    private void logCancelled() {
        log.info("RavenClient connection attempt to {}:{} cancelled by disconnect", configuration.getHost(), configuration.getPort());
    }

    private Connection openConnection(final Socket socket) throws IOException {
        final var connectTimeout = configuration.getConnectTimeout();
        return HandlingUtil.closeOnFailure(socket, () -> {
            socket.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()), (int) connectTimeout.toMillis());
            // The connect timeout also bounds the stream-header exchange with the server
            return Connection.open(socket, connectTimeout, configuration.getSendQueueCapacity(), configuration.getSendTimeout());
        });
    }

    /**
     * Runs all callbacks of one session on its receive thread: {@code onConnect}, the receive loop, {@code onDisconnect}.
     */
    private void runSession(final Session current) {
        try {
            current.previous().join();
            // Closed by disconnect() or the peer before it was ever announced: there is nothing to report
            if (!current.connection().isConnected()) {
                detach(current);
                return;
            }

            if (!announce(current))
                return;

            try {
                ReceiveLoop.run(current.connection(), () -> true, this::deliver, () -> detach(current));
            } finally {
                // Even if the loop throws (e.g. a failing validator), every announced session reports its end once
                log.info("RavenClient disconnected");
                HandlingUtil.callHandler(configuration.getHandler()::onDisconnect, () -> "on disconnect");
            }
        } finally {
            // Also releases connect() if the session ended before it was announced
            current.announced().complete(null);
            current.ended().complete(null);
        }
    }

    /**
     * Calls {@code onConnect}; if it fails, closes the connection, which then gets no {@code onDisconnect}.
     *
     * @return {@code true} if the session may proceed to its receive loop
     */
    private boolean announce(final Session current) {
        final var announced = HandlingUtil.callHandler(configuration.getHandler()::onConnect,
                () -> "on connect, closing the connection");
        if (!announced) {
            // Without this the client would look connected while no receiver is running
            current.connection().disconnect();
            detach(current);
        }

        current.announced().complete(null);
        return announced;
    }

    /**
     * Removes the session from the client unless a newer connect() has replaced it already.
     */
    private void detach(final Session ended) {
        synchronized (lock) {
            if (session == ended)
                session = null;
        }
    }

    /**
     * Waits for a session milestone, unless the calling thread is one of this client's receive threads: that thread
     * itself has to return from its callback before any milestone of this client can be reached.
     */
    private void awaitUnlessCallback(final CompletableFuture<Void> milestone) {
        if (CALLBACK_CLIENT.isBound() && CALLBACK_CLIENT.get() == this)
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
     * Returns whether the client is currently connected to the server.
     */
    public boolean isConnected() {
        synchronized (lock) {
            return session != null && session.connection().isConnected();
        }
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
            pendingConnect.cancel();
            current = session;
            // Closing the socket ends the receive loop, whose thread then reports onDisconnect; interrupting it is
            // not needed and would leave the interrupt flag set inside onDisconnect
            if (current != null)
                current.connection().disconnect();
        }

        if (current != null)
            awaitUnlessCallback(current.ended());
    }

    /**
     * Sends a message to the server. Thread-safe.
     *
     * <p>Delivery is asynchronous: the call returns once the message is queued for the connection's writer, which
     * serializes it later, so the message must not be modified after this call.
     * If that queue is full, it blocks for up to the configured {@code sendTimeout} (5 seconds by default) and then disconnects the stalled peer.
     * Write errors are logged and close the connection rather than being thrown; messages still queued
     * when a connection is closed are dropped.
     *
     * @param message the message to send
     */
    public void send(final Message message) {
        // Read the field once, so that a concurrent session change cannot swap the connection mid-call
        final var current = session;
        send(current == null ? null : current.connection(), message);
    }

    private void send(final Connection currentConnection, final Message message) {
        if (currentConnection == null || !currentConnection.isConnected()) {
            log.warn("Cannot send message: not connected");
            return;
        }

        MessageValidator.validateOrThrow(message);
        MeasurementUtil.measure(() -> currentConnection.send(message),
                duration -> log.debug("Message queued in {}ms", duration.toMillis()));
    }

    /**
     * Validates a received message, passes it through the interceptor chain and dispatches it to the handler.
     *
     * @return {@code false} if the handler failed and the session must end
     */
    private boolean deliver(final Message message) {
        final var violations = MessageValidator.validate(message);
        if (!violations.isEmpty()) {
            log.warn("Received invalid {}: {}", message.getClass().getSimpleName(), violations);
            return true;
        }

        if (!InterceptorUtil.shouldProceed(configuration.getInterceptors(), message))
            return true;

        // A failing handler ends this session; the receive loop then releases the connection
        return HandlingUtil.callHandler(() -> configuration.getHandler().onMessage(message),
                () -> "for received message, closing the connection");
    }
}
