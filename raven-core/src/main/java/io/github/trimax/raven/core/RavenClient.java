package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.Socket;

import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.util.MeasurementUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure TCP client. Connects to a {@link RavenServer}, sends messages,
 * and delegates events to a {@link ClientHandler}.
 *
 * <p>Uses a virtual thread for the receiver loop.
 * This class has no Spring dependencies — it is a plain Java transport layer.
 */
@Slf4j
@RequiredArgsConstructor
public final class RavenClient {

    @NonNull
    private final String host;
    private final int port;

    @NonNull
    private final ClientHandler handler;

    private Connection connection;
    private Thread receiverThread;

    /**
     * Connects to the server. Does nothing if already connected.
     */
    public synchronized void connect() {
        if (isConnected())
            return;

        try {
            final var socket = new Socket(host, port);
            connection = new Connection(socket);

            log.info("RavenClient connected to {}:{}", host, port);
            handler.onConnect();

            final var currentConnection = connection;
            receiverThread = Thread.ofVirtual().name("raven-receiver").start(() -> receiveLoop(currentConnection));
        } catch (final IOException ex) {
            log.warn("RavenClient failed to connect to {}:{} - {}", host, port, ex.getMessage());
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
        handler.onDisconnect();
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
        while (activeConnection.isConnected()) {
            final var message = activeConnection.receive();
            if (message == null)
                break;

            final var violations = MessageValidator.validate(message);
            if (!violations.isEmpty()) {
                log.warn("Received invalid {}: {}", message.getClass().getSimpleName(), violations);
                continue;
            }

            handler.onMessage(message);
        }

        synchronized (this) {
            if (connection == activeConnection) {
                disconnect();
            }
        }
    }
}
