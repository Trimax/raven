package io.github.trimax.raven.core;

import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.util.HandlingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks the client sessions of a {@link RavenServer} and reports them to the handler. Guarantees that
 * {@code onDisconnect} is reported exactly once per client, and only after its {@code onConnect} has returned,
 * whether the session ends on its own, on {@link #closeAll()} or while both race.
 */
@Slf4j
@RequiredArgsConstructor
final class SessionManager {

    /** Receives {@code onConnect} and {@code onDisconnect}. */
    private final ServerHandler handler;

    /** Whether the owning server is still running. */
    private final BooleanSupplier running;

    private final ClientRegistry clients = new ClientRegistry();

    /**
     * Called on the handshake thread once a connection is established. Registers the client and notifies the
     * handler. Any failure of the handler's {@code onConnect} releases the connection, so the accept path
     * keeps running.
     *
     * @return {@code true} if the session is open and the caller must start its receive loop
     */
    boolean open(final Socket socket, final Client client) {
        // Registered before the running check, so closeAll() either sees this client or this thread sees the stop
        clients.addConnecting(client);
        try {
            if (!register(socket, client))
                return false;
        } finally {
            clients.markConnected(client.getId());
        }

        // closeAll() skipped this client while onConnect ran, so its single disconnect is reported here and the
        // receive loop, which would exit at once, is not started
        if (!running.getAsBoolean()) {
            if (clients.remove(client.getId()) != null)
                disconnectClient(client);
            return false;
        }

        return true;
    }

    /**
     * Called once a client session has ended. Removal from the registry decides who reports the disconnect,
     * so {@code onDisconnect} runs once per client.
     */
    void close(final Client client) {
        if (clients.remove(client.getId()) != null)
            disconnectClient(client);
    }

    /**
     * Disconnects every client. Called once the owning server is no longer running.
     */
    void closeAll() {
        // Clients are removed from the registry before they are disconnected, so exactly one of closeAll() and
        // the client's own receive loop reports its disconnect. Clients still in onConnect are skipped and
        // reported by their registering thread once onConnect returns
        for (final var client : clients.removeAll())
            disconnectClient(client);
    }

    Client get(final UUID id) {
        return clients.get(id);
    }

    /**
     * Returns an immutable snapshot of the connected client IDs.
     */
    Set<UUID> ids() {
        return clients.ids();
    }

    /**
     * Announces the client to the handler.
     *
     * @return {@code false} if the client was dropped without a disconnect being reported: its handshake
     *         completed after the server stopped, or the handler's {@code onConnect} failed
     */
    private boolean register(final Socket socket, final Client client) {
        // A client whose handshake completed after the server stopped is dropped without ever being announced
        if (!running.getAsBoolean()) {
            clients.remove(client.getId());
            client.disconnect();
            return false;
        }

        log.info("Client connected: {} ({})", client.getId(), socket.getRemoteSocketAddress());
        if (!HandlingUtil.callHandler(() -> handler.onConnect(client),
                () -> "on connect for " + socket.getRemoteSocketAddress() + ", closing the connection")) {
            // The receive loop never starts, so the connection must be released here
            clients.remove(client.getId());
            client.disconnect();
            return false;
        }

        return true;
    }

    private void disconnectClient(final Client client) {
        client.disconnect();
        // A failing handler must not keep closeAll() from disconnecting and reporting the remaining clients
        HandlingUtil.callHandler(() -> handler.onDisconnect(client), () -> "on disconnect for " + client.getId());

        log.info("Client disconnected: {}", client.getId());
    }
}
