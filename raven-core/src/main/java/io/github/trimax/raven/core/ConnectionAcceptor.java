package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.TryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accepts TCP connections and performs the handshake for each one on its own virtual thread,
 * so a slow or silent peer never delays the accept loop or other peers.
 *
 * <p>The handshake reads the peer's stream header and is bounded by the handshake timeout.
 * A connection that does not complete the handshake in time is closed.
 */
@Slf4j
@RequiredArgsConstructor
final class ConnectionAcceptor {

    /** Already validated by the owning server: the handshake timeout fits into an {@code int} of milliseconds. */
    private final RavenServerConfiguration configuration;

    /** Whether the owning server is still running. */
    private final BooleanSupplier running;

    /** Invoked on the handshake thread with the raw socket and the established client. */
    private final BiConsumer<Socket, Client> onAccepted;

    // Sockets still in the handshake, closed by closeHandshakes() so they do not linger until the handshake timeout
    private final Set<Socket> handshakes = ConcurrentHashMap.newKeySet();

    /**
     * Starts the accept loop on an already bound server socket. The loop ends once that socket is closed,
     * even if the owning server has been started again on a new socket.
     *
     * @return the accept thread
     */
    Thread start(final ServerSocket serverSocket) {
        return Thread.ofVirtual().name("raven-accept").start(() -> acceptLoop(serverSocket));
    }

    /**
     * Closes every socket still in the handshake. Called once the owning server is no longer running,
     * so a handshake that starts afterward sees that and closes its own socket.
     */
    void closeHandshakes() {
        handshakes.forEach(handshake -> TryUtil.execute(handshake::close));
    }

    private void acceptLoop(final ServerSocket serverSocket) {
        // running alone is not enough: after stop() and start() it is true again for the new socket,
        // and accept() on this closed socket would fail at once, forever
        while (running.getAsBoolean() && !serverSocket.isClosed()) {
            final Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (final IOException ex) {
                if (serverSocket.isClosed())
                    break;
                if (running.getAsBoolean())
                    log.error("Error accepting connection: {}", ex.getMessage());
                continue;
            }

            // The handshake blocks on the peer's stream header, so it must not run on the accept thread
            Thread.ofVirtual().name("raven-handshake").start(() -> handshake(clientSocket));
        }
    }

    private void handshake(final Socket clientSocket) {
        final Client client;
        handshakes.add(clientSocket);
        try {
            // closeHandshakes() runs after the server is marked stopped, so this re-check covers a late add
            if (!running.getAsBoolean())
                throw new IOException("server stopped");

            clientSocket.setSoTimeout((int) configuration.getHandshakeTimeout().toMillis());
            client = new Client(clientSocket, configuration.getSendQueueCapacity(), configuration.getSendTimeout());
            clientSocket.setSoTimeout(0);
        } catch (final IOException ex) {
            if (running.getAsBoolean())
                log.error("Error setting up connection from {}: {}",
                        clientSocket.getRemoteSocketAddress(), ex.getMessage());
            TryUtil.execute(clientSocket::close);
            return;
        } finally {
            handshakes.remove(clientSocket);
        }

        onAccepted.accept(clientSocket, client);
    }
}
