package io.github.trimax.raven;

import io.github.trimax.raven.handler.ServerHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pure TCP server. Accepts client connections, manages their lifecycle,
 * and delegates events to a {@link ServerHandler}.
 *
 * <p>Uses virtual threads for the acceptance loop and per-client receiver loops.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
@RequiredArgsConstructor
public final class RavenServer {

    private final int port;

    @NonNull
    private final ServerHandler handler;

    private final Map<UUID, Client> clients = new ConcurrentHashMap<>();
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();

    /**
     * Starts the server and begins accepting connections.
     */
    public void start() {
        try {
            final var socket = new ServerSocket(port);
            if (!serverSocket.compareAndSet(null, socket)) {
                socket.close();
                return;
            }

            log.info("RavenServer started on port {}", port);
            Thread.ofVirtual().name("raven-accept").start(this::acceptLoop);
        } catch (final IOException ex) {
            log.error("Failed to start RavenServer on port {}: {}", port, ex.getMessage());
        }
    }

    /**
     * Stops the server and disconnects all clients.
     * Note: per-client receive loops may still call handler.onDisconnect() after this method returns,
     * but the client's map will already be cleared — this is harmless.
     */
    public void stop() {
        final var socket = serverSocket.getAndSet(null);
        if (socket == null) {
            return;
        }

        log.info("RavenServer stopping...");

        clients.values().forEach(Client::disconnect);
        clients.clear();

        try {
            socket.close();
        } catch (final IOException ex) {
            log.debug("Error closing server socket: {}", ex.getMessage());
        }

        log.info("RavenServer stopped");
    }

    /**
     * Sends a message to the specified recipients.
     * If no recipients are provided, the message is broadcast to all connected clients.
     *
     * @param message    the message to send
     * @param recipients zero or more client IDs; empty means broadcast to all
     */
    public void send(final Message message, final UUID... recipients) {
        if (ArrayUtils.isEmpty(recipients)) {
            clients.values().forEach(client -> client.send(message));
        } else {
            for (final var recipientId : recipients) {
                final var client = clients.get(recipientId);
                if (client != null) {
                    client.send(message);
                } else {
                    log.warn("Cannot send message: client {} not found", recipientId);
                }
            }
        }
    }

    /**
     * Returns the set of currently connected client IDs.
     */
    public Set<UUID> getClients() {
        return Set.copyOf(clients.keySet());
    }

    /**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return serverSocket.get() != null;
    }

    private void acceptLoop() {
        while (isRunning()) {
            try {
                final var socket = serverSocket.get();
                if (socket == null) {
                    break;
                }

                final var clientSocket = socket.accept();
                final var client = new Client(clientSocket);

                clients.put(client.getId(), client);
                log.info("Client connected: {} ({})", client.getId(), clientSocket.getRemoteSocketAddress());

                handler.onConnect(client);

                Thread.ofVirtual()
                        .name("raven-client-" + client.getId())
                        .start(() -> receiveLoop(client));
            } catch (final IOException ex) {
                if (isRunning()) {
                    log.error("Error accepting connection: {}", ex.getMessage());
                }
            }
        }
    }

    private void receiveLoop(final Client client) {
        while (client.isConnected() && isRunning()) {
            final var message = client.receive();
            if (message == null) {
                break;
            }

            handler.onMessage(client, message);
        }

        disconnectClient(client);
    }

    private void disconnectClient(final Client client) {
        client.disconnect();
        clients.remove(client.getId());
        log.info("Client disconnected: {}", client.getId());

        handler.onDisconnect(client);
    }
}
