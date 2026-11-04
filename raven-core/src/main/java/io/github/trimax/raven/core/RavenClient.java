package io.github.trimax.raven.core;

import java.io.IOException;
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
    @NonNull
    private final RavenClientConfiguration configuration;

    /**
     * Current connection. Volatile because it is read without the monitor by {@link #send(Message)}.
     */
    private volatile Connection connection;
    private Thread receiverThread;

    /**
     * Connects to the server. Does nothing if already connected.
     */
    public synchronized void connect() {
        if (isConnected())
            return;

        try {
            final var socket = new Socket(configuration.getHost(), configuration.getPort());
            connection = new Connection(socket);

            log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());
            try {
                configuration.getHandler().onConnect();
            } catch (final RuntimeException ex) {
                // Without this the client would look connected while no receiver is running
                log.error("Handler failed on connect, closing connection: {}", ex.getMessage(), ex);
                final var failed = connection;
                connection = null;
                failed.disconnect();
                return;
            }

            final var currentConnection = connection;
            receiverThread = Thread.ofVirtual().name("raven-receiver").start(() -> receiveLoop(currentConnection));
        } catch (final IOException ex) {
            log.warn("RavenClient failed to connect to {}:{} - {}", configuration.getHost(), configuration.getPort(), ex.getMessage());
            connection = null;
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
