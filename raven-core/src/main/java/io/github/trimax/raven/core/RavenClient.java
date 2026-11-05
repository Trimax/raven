package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.InterceptorUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.util.TryUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ClientHandler}.
 *
 * <p>Uses a virtual thread for the receiver loop.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
public final class RavenClient {

    @NonNull
    private final RavenClientConfiguration configuration;

    /**
     * Current connection. Volatile because it is read without the monitor by {@link #send(Message)}.
     */
    private volatile Connection connection;
    private Thread receiverThread;

    /**
     * Socket of the connection attempt in progress, or {@code null}. Guarded by the monitor.
     */
    private Socket pendingSocket;

    /**
     * Incremented by {@link #disconnect()} so that an in-flight {@link #connect()} discards its result. Guarded by the monitor.
     */
    private long connectGeneration;

    public RavenClient(@NonNull final RavenClientConfiguration configuration) {
        configuration.validate();

        this.configuration = configuration;
    }

    /**
     * Connects to the server. Does nothing if already connected or if another connection attempt is in progress.
     * <p>
     * Network I/O runs outside the client's monitor, so {@link #isConnected()}, {@link #send(Message)} and
     * {@link #disconnect()} remain responsive while a connection attempt is blocked.
     * {@link #disconnect()} aborts an attempt in progress.
     */
    public void connect() {
        final Socket socket;
        final long generation;
        synchronized (this) {
            if (isConnected() || pendingSocket != null)
                return;

            socket = new Socket();
            pendingSocket = socket;
            generation = connectGeneration;
        }

        final Connection newConnection;
        try {
            newConnection = openConnection(socket);
        } catch (final IOException ex) {
            synchronized (this) {
                releasePendingSocket(socket);
                if (generation != connectGeneration) {
                    log.info("RavenClient connection attempt to {}:{} cancelled by disconnect", configuration.getHost(), configuration.getPort());
                    return;
                }
            }

            log.warn("RavenClient failed to connect to {}:{} - {}", configuration.getHost(), configuration.getPort(), ex.getMessage());
            return;
        } catch (final RuntimeException ex) {
            // Otherwise every later connect() would see an attempt in progress and return
            releasePendingSocket(socket);
            throw ex;
        }

        synchronized (this) {
            releasePendingSocket(socket);
            if (generation != connectGeneration) {
                // disconnect() was called while the connection was being opened
                newConnection.disconnect();
                log.info("RavenClient connection attempt to {}:{} cancelled by disconnect", configuration.getHost(), configuration.getPort());
                return;
            }

            connection = newConnection;
            log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());
            if (!HandlingUtil.callHandler(configuration.getHandler()::onConnect, () -> "on connect")) {
                // Without this the client would look connected while no receiver is running
                final var failed = connection;
                connection = null;
                failed.disconnect();
                return;
            }

            receiverThread = Thread.ofVirtual().name("raven-receiver").start(() ->
                    ReceiveLoop.run(newConnection, () -> true, this::deliver, () -> onSessionEnded(newConnection)));
        }
    }

    private synchronized void releasePendingSocket(final Socket socket) {
        // disconnect() may have cleared the field already and a newer attempt may own it now
        if (pendingSocket == socket)
            pendingSocket = null;
    }

    private Connection openConnection(final Socket socket) throws IOException {
        final var connectTimeout = configuration.getConnectTimeout();
        return HandlingUtil.closeOnFailure(socket, () -> {
            socket.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()), (int) connectTimeout.toMillis());
            // The connect timeout also bounds the stream-header exchange with the server
            return Connection.open(socket, connectTimeout, configuration.getSendQueueCapacity(), configuration.getSendTimeout());
        });
    }

    /**
     * Returns whether the client is currently connected to the server.
     */
    public synchronized boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    /**
     * Disconnects from the server, aborting a connection attempt in progress. Safe to call multiple times.
     */
    public synchronized void disconnect() {
        connectGeneration++;
        if (pendingSocket != null) {
            // Unblocks the connecting thread instead of letting it wait for the connect timeout
            TryUtil.execute(pendingSocket::close);
            pendingSocket = null;
        }

        closeConnection();
    }

    private void closeConnection() {
        if (connection == null)
            return;

        connection.disconnect();
        connection = null;

        if (receiverThread != null) {
            receiverThread.interrupt();
            receiverThread = null;
        }

        log.info("RavenClient disconnected");
        configuration.getHandler().onDisconnect();
    }

    /**
     * Sends a message to the server. Thread-safe.
     *
     * <p>Delivery is asynchronous: the call returns once the message is queued for the connection's writer.
     * If that queue is full, it blocks for up to the configured {@code sendTimeout} (5 seconds by default) and then disconnects the stalled peer.
     * Write errors are logged and close the connection rather than being thrown; messages still queued
     * when a connection is closed are dropped.
     *
     * @param message the message to send
     */
    public void send(final Message message) {
        // Capture the field into a method parameter to prevent NPE if disconnect() nulls it concurrently
        send(connection, message);
    }

    private void send(final Connection currentConnection, final Message message) {
        if (currentConnection == null || !currentConnection.isConnected()) {
            log.warn("Cannot send message: not connected");
            return;
        }

        MessageValidator.validateOrThrow(message);
        MeasurementUtil.measure(() -> currentConnection.send(message),
                duration -> log.debug("Message queued in {}ms", duration.toMillis()));
    }

    /**
     * Validates a received message, passes it through the interceptor chain and dispatches it to the handler.
     *
     * @return {@code false} if the handler failed and the session must end
     */
    private boolean deliver(final Message message) {
        final var violations = MessageValidator.validate(message);
        if (!violations.isEmpty()) {
            log.warn("Received invalid {}: {}", message.getClass().getSimpleName(), violations);
            return true;
        }

        if (!InterceptorUtil.shouldProceed(configuration.getInterceptors(), message))
            return true;

        // A failing handler ends this session; the receive loop then releases the connection
        return HandlingUtil.callHandler(() -> configuration.getHandler().onMessage(message), () -> "for received message");
    }

    /**
     * Called once the receive loop of {@code activeConnection} has ended and disconnected it.
     */
    private synchronized void onSessionEnded(final Connection activeConnection) {
        // Not disconnect(): that would also abort a reconnect attempt started after this connection dropped
        if (connection == activeConnection)
            closeConnection();
    }
}
