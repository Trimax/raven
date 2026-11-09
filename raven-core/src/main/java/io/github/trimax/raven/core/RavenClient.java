package io.github.trimax.raven.core;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.internal.client.ClientListener;
import io.github.trimax.raven.core.internal.client.SessionSlot;
import io.github.trimax.raven.core.internal.session.Session;
import io.github.trimax.raven.core.internal.session.SessionSettings;
import io.github.trimax.raven.core.internal.util.ThreadUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ClientHandler}.
 *
 * <p>Every connection is a {@code Session} with its own virtual thread, named {@code raven-receiver}, and all handler
 * callbacks of that connection run on it in order: {@code onConnect}, then {@code onMessage} for each received
 * message, then {@code onDisconnect}. {@code onDisconnect} is called exactly once for every {@code onConnect} that
 * returned normally, however the session ended; a failing {@code onConnect} closes the connection and gets no
 * {@code onDisconnect}. A new connection starts only after {@code onDisconnect} of the previous one has returned.
 *
 * <p>A callback may call any method of the client. This class has no Spring dependencies — it is a plain Java
 * transport layer.
 */
@Slf4j
public final class RavenClient {

    @NonNull
    private final RavenClientConfiguration configuration;

    private final SessionSettings settings;

    /** Never closed: its virtual threads end with their session, and an idle executor holds nothing. */
    private final SessionSlot session = new SessionSlot(ThreadUtil.newExecutor());

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
     * previous one, or once the attempt has failed. Called from any Raven callback, it returns without waiting.
     */
    public void connect() {
        ThreadUtil.awaitOutsideCallbacks(session.open(() -> Session.dialing(configuration.getHost(),
                configuration.getPort(), settings, new ClientListener(configuration))));
    }

    /**
     * Returns whether the client is currently connected to the server.
     */
    public boolean isConnected() {
        return session.isOpen();
    }

    /**
     * Disconnects from the server, aborting a connection attempt in progress. Safe to call multiple times.
     * <p>
     * Returns once {@code onDisconnect} of the closed connection has run. Called from any Raven callback, it returns
     * without waiting.
     */
    public void disconnect() {
        ThreadUtil.awaitOutsideCallbacks(session.close());
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
        if (!isConnected()) {
            log.warn("Cannot send message: not connected");
            return;
        }

        MessageValidator.validateOrThrow(message);
        final var payload = settings.encode(message);
        MeasurementUtil.measure(() -> session.send(payload),
                duration -> log.debug("Message queued in {}ms", duration.toMillis()));
    }
}
