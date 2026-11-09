package io.github.trimax.raven.core;

import java.util.Set;
import java.util.UUID;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.ArrayUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.util.ThreadUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP server. Accepts client connections, manages their lifecycle,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ServerHandler}.
 *
 * <p>This class is the public facade; each run between {@link #start()} and {@link #stop()} is a
 * {@link ServerRuntime}, held by a {@link ServerSlot}. Every connection is a {@link Session} with its own virtual thread, which runs all handler
 * callbacks of that client in order: {@code onConnect}, {@code onMessage} for each received message, then
 * {@code onDisconnect}. {@code onDisconnect} is called exactly once for every {@code onConnect} that returned
 * normally; a failing {@code onConnect} closes the connection and gets no {@code onDisconnect}.
 *
 * <p>This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenServer {

    private final SessionSettings settings;
    private final ServerSlot server;

    public RavenServer(@NonNull final RavenServerConfiguration configuration) {
        configuration.validate();

        this.settings = SessionSettings.of(configuration);
        this.server = new ServerSlot(configuration);
    }

/**
     * Starts the server and begins accepting connections. Does nothing if it is running already.
     */
    public void start() {
        server.open();
    }

/**
     * Stops the server and disconnects all clients. Returns once {@code onDisconnect} has returned for every client.
     * Called from any Raven callback, it returns without waiting.
     */
    public void stop() {
        ThreadUtil.awaitOutsideCallbacks(server.close());
    }

/**
     * Broadcasts a message to all connected clients. Delivery is asynchronous, as described at
     * {@link #send(Message, Set)}.
     *
     * @param message the message to broadcast
     */
    public void broadcast(final Message message) {
        MessageValidator.validateOrThrow(message);
        final var payload = settings.encode(message);

        MeasurementUtil.measure(() -> server.send(payload, server.ids()),
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
        final var payload = settings.encode(message);

        if (!server.send(payload, recipients))
            log.warn("Cannot send message: server is not running");
    }

/**
     * Returns the set of currently connected client IDs.
     */
    public Set<UUID> getClients() {
        return server.ids();
    }

/**
     * Returns the port the server is bound to.
     * If started with port 0, this returns the actual ephemeral port assigned by the OS.
     * Returns -1 if the server is not running.
     */
    public int getPort() {
        return server.port();
    }

/**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return server.isOpen();
    }

/**
     * Returns the number of connections that can still be accepted before the connection limit is reached.
     * Package-private for tests only.
     */
    int availableConnections() {
        return server.availableConnections();
    }
}
