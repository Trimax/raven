package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.util.InterceptorUtil;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link io.github.trimax.raven.core.handler.ClientHandler}.
 *
 * <p>Uses a virtual thread for the receiver loop.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
@RequiredArgsConstructor
public final class RavenClient {

    /** Maximum time to establish the TCP connection and complete the stream handshake. */
    static final int CONNECT_TIMEOUT_MS = 10_000;

    @NonNull
    private final RavenClientConfiguration configuration;

    private Connection connection;
    private Thread receiverThread;

    /**
     * Connects to the server. Does nothing if already connected.
     * <p>
     * Network I/O runs outside the client's monitor, so {@link #isConnected()}, {@link #send(Message)} and
     * {@link #disconnect()} remain responsive while a connection attempt is blocked.
     */
    public void connect() {
        synchronized (this) {
            if (isConnected())
                return;
        }

        final Connection newConnection;
        try {
            newConnection = openConnection();
        } catch (final IOException ex) {
            log.warn("RavenClient failed to connect to {}:{} - {}", configuration.getHost(), configuration.getPort(), ex.getMessage());
            return;
        }

        synchronized (this) {
            if (isConnected()) {
                // Another thread connected while this one was in progress
                newConnection.disconnect();
                return;
            }

            connection = newConnection;
            log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());
            configuration.getHandler().onConnect();

            receiverThread = Thread.ofVirtual().name("raven-receiver").start(() -> receiveLoop(newConnection));
        }
    }

    private Connection openConnection() throws IOException {
        final var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(configuration.getHost(), configuration.getPort()), CONNECT_TIMEOUT_MS);
            // Bound the stream-header exchange, which otherwise blocks until the server writes its header
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            final var newConnection = new Connection(socket);
            socket.setSoTimeout(0);
            return newConnection;
        } catch (final IOException | RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }

    /**
     * Returns whether the client is currently connected to the server.
     */
    public synchronized boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    /**
     * Disconnects from the server. Safe to call multiple times.
     */
    public synchronized void disconnect() {
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
                duration -> log.debug("Message sent in {}ms", duration.toMillis()));
    }

    private void receiveLoop(final Connection activeConnection) {
        try {
            while (activeConnection.isConnected()) {
                final var message = activeConnection.receive();
                if (message == null)
                    break;

                final var violations = MessageValidator.validate(message);
                if (!violations.isEmpty()) {
                    log.warn("Received invalid {}: {}", message.getClass().getSimpleName(), violations);
                    continue;
                }

                if (!InterceptorUtil.shouldProceed(configuration.getInterceptors(), message))
                    continue;

                try {
                    configuration.getHandler().onMessage(message);
                } catch (final RuntimeException ex) {
                    // A failing handler ends this session; finally below releases the connection
                    log.error("Handler failed for received message, closing connection: {}", ex.getMessage(), ex);
                    return;
                }
            }
        } finally {
            // Runs on normal exit and on errors, so the socket is never left open
            activeConnection.disconnect();
            synchronized (this) {
                if (connection == activeConnection)
                    disconnect();
            }
        }
    }
}
