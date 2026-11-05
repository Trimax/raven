package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
 * <p>This class is the public facade. Responsibilities are delegated to:
 * <ul>
 *   <li>{@link ClientRegistry} — the set of connected clients and the exactly-once disconnect rule;</li>
 *   <li>{@link ConnectionAcceptor} — the accept loop and the bounded handshake of each new connection;</li>
 *   <li>{@link ReceiveLoop} — reading, validating, intercepting and dispatching messages per client.</li>
 * </ul>
 *
 * <p>Uses virtual threads for the acceptance loop, the handshakes and per-client receiver loops.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenServer {

    @NonNull
    private final RavenServerConfiguration configuration;

    private final ReceiveLoop receiveLoop;
    private final ConnectionAcceptor acceptor;

    private final ClientRegistry clients = new ClientRegistry();
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();

    public RavenServer(@NonNull final RavenServerConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
        this.acceptor = new ConnectionAcceptor(this.configuration.getHandshakeTimeout(),
                this::isRunning, this::onConnectionAccepted);
        this.receiveLoop = new ReceiveLoop(this.configuration.getHandler(), this.configuration.getInterceptors(),
                this::isRunning, this::onSessionEnded);
    }

    /**
     * Starts the server and begins accepting connections.
     */
    public void start() {
        try {
            final var socket = new ServerSocket(configuration.getPort());
            if (!serverSocket.compareAndSet(null, socket)) {
                socket.close();
                return;
            }

            log.info("RavenServer started on port {}", socket.getLocalPort());
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

        acceptor.closeHandshakes();

        // Clients are removed from the registry before they are disconnected, so exactly one of stop() and
        // the client's own receive loop reports its disconnect. Clients still in onConnect are skipped and
        // reported by their registering thread once onConnect returns
        for (final var client : clients.removeAll())
            disconnectClient(client);

        try {
            socket.close();
        } catch (final IOException ex) {
            log.debug("Error closing server socket: {}", ex.getMessage());
        }

        log.info("RavenServer stopped");
    }

    /**
     * Broadcasts a message to all connected clients.
     *
     * @param message the message to broadcast
     */
    public void broadcast(final Message message) {
        MessageValidator.validateOrThrow(message);

        MeasurementUtil.measure(() -> send(message, clients.ids()),
                duration -> log.debug("Message {} broadcasted in {}ms", message.getId(), duration.toMillis()));
    }

    /**
     * Sends a message to the specified recipients.
     * If the recipient array is empty, no message is sent (no-op).
     *
     * @param message    the message to send
     * @param recipients one or more client IDs; an empty array means no-op
     */
    public void send(final Message message, final UUID... recipients) {
        if (ArrayUtil.isEmpty(recipients))
            return;

        MeasurementUtil.measure(() -> send(message, Set.of(recipients)),
                duration -> log.debug("Message {} sent in {}ms", message.getId(), duration.toMillis()));
    }

    /**
     * Sends a message to the specified recipients.
     * If the recipient array is empty, no message is sent (no-op).
     *
     * @param message    the message to send
     * @param recipients set with client IDs; an empty set means no-op
     */
    public void send(final Message message, final Set<UUID> recipients) {
        MessageValidator.validateOrThrow(message);

        for (final var recipientId : recipients) {
            final var client = clients.get(recipientId);
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
        return clients.ids();
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
     * Called on the handshake thread once a connection is established. Registers the client, notifies the
     * handler and starts the receive loop. Any failure of the handler's {@code onConnect} releases the
     * connection, so the accept path keeps running.
     */
    private void onConnectionAccepted(final Socket socket, final Client client) {
        // Registered before the running check, so stop() either sees this client or this thread sees the stop
        clients.addConnecting(client);
        try {
            if (!register(socket, client))
                return;
        } finally {
            clients.markConnected(client.getId());
        }

        // stop() skipped this client while onConnect ran, so its single disconnect is reported here and the
        // receive loop, which would exit at once, is not started
        if (!isRunning()) {
            if (clients.remove(client.getId()) != null)
                disconnectClient(client);
            return;
        }

        Thread.ofVirtual()
                .name("raven-client-" + client.getId())
                .start(() -> receiveLoop.run(client));
    }

    /**
     * Announces the client to the handler.
     *
     * @return {@code false} if the client was dropped without a disconnect being reported: its handshake
     *         completed after {@link #stop()}, or the handler's {@code onConnect} failed
     */
    private boolean register(final Socket socket, final Client client) {
        // A client whose handshake completed after stop() is dropped without ever being announced
        if (!isRunning()) {
            clients.remove(client.getId());
            client.disconnect();
            return false;
        }

        log.info("Client connected: {} ({})", client.getId(), socket.getRemoteSocketAddress());
        try {
            configuration.getHandler().onConnect(client);
        } catch (final RuntimeException ex) {
            // The receive loop never starts, so the connection must be released here
            log.error("Handler failed on connect for {}: {}", socket.getRemoteSocketAddress(), ex.getMessage(), ex);
            clients.remove(client.getId());
            client.disconnect();
            return false;
        }

        return true;
    }

    /**
     * Called once a client session has ended. Removal from the registry decides who reports the disconnect,
     * so {@code onDisconnect} runs once per client.
     */
    private void onSessionEnded(final Client client) {
        if (clients.remove(client.getId()) != null)
            disconnectClient(client);
    }

    private void disconnectClient(final Client client) {
        client.disconnect();
        configuration.getHandler().onDisconnect(client);

        log.info("Client disconnected: {}", client.getId());
    }
}
