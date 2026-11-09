package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.ThreadUtil;
import io.github.trimax.raven.core.util.TryUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * One run of a {@link RavenServer}, from {@link #bind} to {@link #stop()}: its server socket, its threads and its
 * sessions. A restarted server gets a new runtime, so nothing of an earlier run can leak into a later one.
 *
 * <p>As the listener of all its sessions it reports them to the handler: {@code onConnect} once the hello exchange
 * has succeeded, {@code onDisconnect} once the session has ended, both on the session's own thread.
 */
@Slf4j
final class ServerRuntime implements SessionListener {

    /**
     * An announced client and the pipeline for its messages.
     */
    private record Peer(Client client, InboundPipeline inbound) {
    }

    private final RavenServerConfiguration configuration;
    private final SessionSettings settings;
    private final ServerSocket serverSocket;
    private final ExecutorService threads = ThreadUtil.newExecutor();
    private final Acceptor acceptor;

    /** Every session from its accept to its end, handshakes included, so that stop() can close them all. */
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    /** The clients whose onConnect is running or has returned, until their onDisconnect. */
    private final Map<UUID, Peer> clients = new ConcurrentHashMap<>();

    private volatile boolean stopped;

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
     * Stops accepting, closes every session and waits until each has ended, so every {@code onDisconnect} has
     * returned. Called from a callback of this server, it does not wait: that callback's own session cannot end
     * before it returns.
     */
    void stop() {
        stopped = true;
        // Release the port first, so nothing that follows can leave it accepting connections
        TryUtil.execute(serverSocket::close);
        sessions.forEach(Session::close);

        if (ThreadUtil.callbackOwner() == this)
            threads.shutdown();
        else
            threads.close();
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
        return Set.copyOf(clients.keySet());
    }

    /**
     * Queues one encoded frame for each recipient; the same bytes are shared by all of them.
     */
    void send(final byte[] frame, final Set<UUID> recipients) {
        for (final var recipientId : recipients) {
            final var peer = clients.get(recipientId);
            if (peer == null)
                log.warn("Cannot send message: client {} not found", recipientId);
            else
                peer.client().send(frame);
        }
    }

    /**
     * Called on the accept thread for every connection within the limit.
     */
    private void onAccepted(final Socket socket) {
        final var session = Session.accepted(socket, settings, this);
        sessions.add(session);
        session.ended().thenRun(() -> {
            sessions.remove(session);
            acceptor.release();
        });

        // stop() closes every session it sees; this one may have been added after it looked
        if (stopped)
            session.close();

        session.start(threads);
    }

    @Override
    public boolean onOpen(final Session session) {
        final var client = new Client(session);
        // Registered before onConnect, so the handler can already send to the client by its ID
        clients.put(client.getId(), new Peer(client, InboundPipeline.of(configuration, client)));

        log.info("Client connected: {} ({})", client.getId(), session.remoteAddress());
        if (HandlingUtil.callHandler(() -> configuration.getHandler().onConnect(client),
                () -> "on connect for " + session.remoteAddress() + ", closing the connection"))
            return true;

        clients.remove(client.getId());
        return false;
    }

    @Override
    public boolean onMessage(final Session session, final Message message) {
        return clients.get(session.id()).inbound().deliver(message);
    }

    @Override
    public void onClose(final Session session) {
        final var peer = clients.remove(session.id());
        peer.inbound().summarize();
        // A failing handler ends nothing more than this session, which is over already
        HandlingUtil.callHandler(() -> configuration.getHandler().onDisconnect(peer.client()),
                () -> "on disconnect for " + session.id());

        log.info("Client disconnected: {}", session.id());
    }
}
