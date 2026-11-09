package io.github.trimax.raven.core;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.ArrayUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP server. Accepts client connections, manages their lifecycle,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ServerHandler}.
 *
 * <p>This class is the public facade; each run between {@link #start()} and {@link #stop()} is a
 * {@link ServerRuntime}. Every connection is a {@link Session} with its own virtual thread, which runs all handler
 * callbacks of that client in order: {@code onConnect}, {@code onMessage} for each received message, then
 * {@code onDisconnect}. {@code onDisconnect} is called exactly once for every {@code onConnect} that returned
 * normally; a failing {@code onConnect} closes the connection and gets no {@code onDisconnect}.
 *
 * <p>This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenServer {

    @NonNull
    private final RavenServerConfiguration configuration;

    /** Encodes outgoing messages; the same settings serve every session. */
    private final SessionSettings settings;

    /** The current run, or {@code null} while the server is stopped. */
    private final AtomicReference<ServerRuntime> runtime = new AtomicReference<>();

    public RavenServer(@NonNull final RavenServerConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
        this.settings = SessionSettings.of(configuration);
    }

    /**
     * Starts the server and begins accepting connections. Does nothing if it is running already.
     */
    public void start() {
        if (isRunning())
            return;

        final ServerRuntime started;
        try {
            started = ServerRuntime.bind(configuration);
        } catch (final IOException ex) {
            log.error("Failed to start RavenServer on port {}: {}", configuration.getPort(), ex.getMessage());
            return;
        }

        // A concurrent start() won the race
        if (!runtime.compareAndSet(null, started)) {
            started.stop();
            return;
        }

        log.info("RavenServer started on {}:{}", started.host(), started.port());
        started.start();
    }

    /**
     * Stops the server and disconnects all clients. Returns once {@code onDisconnect} has returned for every client,
     * unless called from a handler callback of this server, which cannot wait for its own session to end.
     */
    public void stop() {
        final var stopping = runtime.getAndSet(null);
        if (stopping == null)
            return;

        log.info("RavenServer stopping...");
        stopping.stop();
        log.info("RavenServer stopped");
    }

    /**
     * Broadcasts a message to all connected clients. Delivery is asynchronous, as described at
     * {@link #send(Message, Set)}.
     *
     * @param message the message to broadcast
     */
    public void broadcast(final Message message) {
        MessageValidator.validateOrThrow(message);
        final var frame = settings.encode(message);

        final var current = runtime.get();
        if (current == null)
            return;

        MeasurementUtil.measure(() -> current.send(frame, current.ids()),
                duration -> log.debug("Message {} queued for broadcast in {}ms", message.getId(), duration.toMillis()));
    }

    /**
     * Sends a message to the specified recipients.
     * If the recipient array is empty, no message is sent (no-op). Delivery is asynchronous, as described at
     * {@link #send(Message, Set)}.
     *
     * @param message    the message to send
     * @param recipients one or more client IDs; an empty array means no-op
     */
    public void send(final Message message, final UUID... recipients) {
        if (ArrayUtil.isEmpty(recipients))
            return;

        MeasurementUtil.measure(() -> send(message, Set.of(recipients)),
                duration -> log.debug("Message {} queued in {}ms", message.getId(), duration.toMillis()));
    }

    /**
     * Sends a message to the specified recipients.
     * If the recipient set is empty, no message is sent (no-op).
     *
     * <p>The message is serialized once, on the calling thread, before this method returns: later changes to it are
     * not sent. Delivery is asynchronous: the call returns once the message is queued for each recipient.
     * If a recipient's queue is full, it blocks for up to the configured {@code sendTimeout} (5 seconds by default) per recipient and then disconnects
     * that stalled peer. Write errors are logged and close the connection rather than being thrown; messages
     * still queued when a connection is closed are dropped.
     *
     * @param message    the message to send
     * @param recipients set with client IDs; an empty set means no-op
     * @throws IllegalArgumentException if the message cannot be serialized or exceeds {@code maxMessageSize}
     */
    public void send(final Message message, final Set<UUID> recipients) {
        MessageValidator.validateOrThrow(message);
        final var frame = settings.encode(message);

        final var current = runtime.get();
        if (current == null) {
            log.warn("Cannot send message: server is not running");
            return;
        }

        current.send(frame, recipients);
    }

    /**
     * Returns the set of currently connected client IDs.
     */
    public Set<UUID> getClients() {
        final var current = runtime.get();
        return current == null ? Set.of() : current.ids();
    }

    /**
     * Returns the port the server is bound to.
     * If started with port 0, this returns the actual ephemeral port assigned by the OS.
     * Returns -1 if the server is not running.
     */
    public int getPort() {
        final var current = runtime.get();
        return current == null ? -1 : current.port();
    }

    /**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return runtime.get() != null;
    }

    /**
     * Returns the number of connections that can still be accepted before the connection limit is reached.
     * Package-private for tests only.
     */
    int availableConnections() {
        final var current = runtime.get();
        return current == null ? configuration.getMaxConnections() : current.availableConnections();
    }
}
