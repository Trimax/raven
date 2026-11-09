package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.ThreadUtil;
import io.github.trimax.raven.core.util.TryUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * One run of a {@link RavenServer}, from {@link #bind} to {@link #stop()}: its server socket, its acceptor and its
 * sessions. A restarted server gets a new runtime, so nothing of an earlier run can leak into a later one.
 */
@Slf4j
final class ServerRuntime {

    private final RavenServerConfiguration configuration;
    private final SessionSettings settings;
    private final ServerSocket serverSocket;
    private final Acceptor acceptor;

    /** Runs the accept, session and writer threads. Never closed: its virtual threads end with their tasks. */
    private final ExecutorService threads = ThreadUtil.newExecutor();

    private final SessionGroup sessions = new SessionGroup(threads);

    private ServerRuntime(final RavenServerConfiguration configuration, final ServerSocket serverSocket) {
        this.configuration = configuration;
        this.settings = SessionSettings.of(configuration);
        this.serverSocket = serverSocket;
        this.acceptor = new Acceptor(serverSocket, configuration.getMaxConnections(), this::onAccepted);
    }

    /**
     * Binds the server socket; nothing is accepted before {@link #start()}.
     *
     * @throws IOException if the socket cannot be bound
     */
    static ServerRuntime bind(final RavenServerConfiguration configuration) throws IOException {
        // Backlog 0 keeps the JDK default; a null bind address listens on all local addresses
        return new ServerRuntime(configuration, new ServerSocket(configuration.getPort(), 0, configuration.getBindAddress()));
    }

    void start() {
        acceptor.start(threads);
    }

    /**
     * Stops accepting and closes every session.
     *
     * @return completes once every session has ended, so every {@code onDisconnect} has returned
     */
    CompletableFuture<Void> stop() {
        // Release the port first, so nothing that follows can leave it accepting connections
        TryUtil.execute(serverSocket::close);
        return sessions.closeAll();
    }

    String host() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    int availableConnections() {
        return acceptor.availablePermits();
    }

    /**
     * Returns an immutable snapshot of the connected client IDs.
     */
    Set<UUID> ids() {
        return sessions.ids();
    }

    /**
     * Queues the encoded payload for each recipient; the same bytes are shared by all of them.
     */
    void send(final byte[] payload, final Set<UUID> recipients) {
        for (final var recipientId : recipients) {
            final var session = sessions.get(recipientId);
            if (session == null)
                log.warn("Cannot send message: client {} not found", recipientId);
            else
                session.send(payload);
        }
    }

    /**
     * Called on the accept thread for every connection within the limit.
     */
    private void onAccepted(final Socket socket) {
        final var session = Session.accepted(socket, settings, new ServerListener(configuration));
        session.ended().thenRun(acceptor::release);
        sessions.add(session);
    }
}
