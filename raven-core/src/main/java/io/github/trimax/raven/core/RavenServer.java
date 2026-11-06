package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.ArrayUtil;
import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.InterceptorUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.util.TryUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP server. Accepts client connections, manages their lifecycle,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ServerHandler}.
 *
 * <p>This class is the public facade. Responsibilities are delegated to:
 * <ul>
 *   <li>{@link SessionManager} — the connected clients and the exactly-once disconnect rule;</li>
 *   <li>{@link ConnectionAcceptor} — the accept loop and the bounded handshake of each new connection;</li>
 *   <li>{@link ReceiveLoop} — reading messages per client until the session ends.</li>
 * </ul>
 *
 * <p>Uses virtual threads for the acceptance loop, the handshakes and per-client receiver loops.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenServer {

    @NonNull
    private final RavenServerConfiguration configuration;

    private final ConnectionAcceptor acceptor;
    private final SessionManager sessions;

    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();

    public RavenServer(@NonNull final RavenServerConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
        this.acceptor = new ConnectionAcceptor(this.configuration,
                this::isRunning, this::onConnectionAccepted);
        this.sessions = new SessionManager(this.configuration.getHandler(), this::isRunning);
    }

    /**
     * Starts the server and begins accepting connections.
     */
    public void start() {
        try {
            // Backlog 0 keeps the JDK default; a null bind address listens on all local addresses
            final var socket = new ServerSocket(configuration.getPort(), 0, configuration.getBindAddress());
            if (!serverSocket.compareAndSet(null, socket)) {
                socket.close();
                return;
            }

            log.info("RavenServer started on {}:{}", socket.getInetAddress().getHostAddress(), socket.getLocalPort());
            acceptor.start(socket);
        } catch (final IOException ex) {
            log.error("Failed to start RavenServer on port {}: {}", configuration.getPort(), ex.getMessage());
        }
    }

    /**
     * Stops the server and disconnects all clients.
     * Per-client receive loops will not invoke handler.onDisconnect() after the server is stopped.
     */
    public void stop() {
        final var socket = serverSocket.getAndSet(null);
        if (socket == null)
            return;

        log.info("RavenServer stopping...");

        // Release the port first, so nothing that follows can leave it accepting connections
        TryUtil.execute(socket::close);

        acceptor.closeHandshakes();
        sessions.closeAll();

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

        MeasurementUtil.measure(() -> send(message, sessions.ids()),
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
     * <p>Delivery is asynchronous: the call returns once the message is queued for each recipient.
     * If a recipient's queue is full, it blocks for up to the configured {@code sendTimeout} (5 seconds by default) per recipient and then disconnects
     * that stalled peer. Write errors are logged and close the connection rather than being thrown; messages
     * still queued when a connection is closed are dropped.
     *
     * @param message    the message to send
     * @param recipients set with client IDs; an empty set means no-op
     */
    public void send(final Message message, final Set<UUID> recipients) {
        MessageValidator.validateOrThrow(message);

        for (final var recipientId : recipients) {
            final var client = sessions.get(recipientId);
            if (client == null) {
                log.warn("Cannot send message: client {} not found", recipientId);
                continue;
            }

            client.send(message);
        }
    }

    /**
     * Returns the set of currently connected client IDs.
     */
    public Set<UUID> getClients() {
        return sessions.ids();
    }

    /**
     * Returns the port the server is bound to.
     * If started with port 0, this returns the actual ephemeral port assigned by the OS.
     * Returns -1 if the server is not running.
     */
    public int getPort() {
        final var socket = serverSocket.get();
        return socket == null ? -1 : socket.getLocalPort();
    }

    /**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return serverSocket.get() != null;
    }

    /**
     * Returns the number of connections that can still be accepted before the connection limit is reached.
     * Package-private for tests only.
     */
    int availableConnections() {
        return acceptor.availablePermits();
    }

    /**
     * Called on the handshake thread once a connection is established. Opens the client's session and starts
     * its receive loop.
     */
    private void onConnectionAccepted(final Socket socket, final Client client) {
        if (!sessions.open(socket, client))
            return;

        Thread.ofVirtual()
                .name("raven-client-" + client.getId())
                .start(() -> ReceiveLoop.run(client.connection(), this::isRunning,
                        message -> deliver(client, message), () -> sessions.close(client)));
    }

    /**
     * Validates a received message, passes it through the interceptor chain and dispatches it to the handler.
     *
     * @return {@code false} if the handler failed and the session must end
     */
    private boolean deliver(final Client client, final Message message) {
        final var violations = MessageValidator.validate(message);
        if (!violations.isEmpty()) {
            log.warn("Received invalid {} from {}: {}", message.getClass().getSimpleName(), client.getId(), violations);
            return true;
        }

        if (!InterceptorUtil.shouldProceed(configuration.getInterceptors(), client, message))
            return true;

        // A failing handler ends this session; the receive loop then releases the connection
        return HandlingUtil.callHandler(() -> configuration.getHandler().onMessage(client, message),
                () -> "for message from " + client.getId() + ", closing its connection");
    }
}
