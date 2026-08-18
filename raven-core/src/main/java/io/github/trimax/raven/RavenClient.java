package io.github.trimax.raven;

import io.github.trimax.raven.handler.ClientHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;

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

    /**
     * Connects to the server. Does nothing if already connected.
     */
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        try {
            final var socket = new Socket(host, port);
            connection = new Connection(socket);

            log.info("RavenClient connected to {}:{}", host, port);
            handler.onConnect();

            Thread.ofVirtual().name("raven-receiver").start(this::receiveLoop);
        } catch (final IOException ex) {
            log.warn("RavenClient failed to connect to {}:{} - {}", host, port, ex.getMessage());
            connection = null;
        }
    }

    /**
     * Disconnects from the server. Safe to call multiple times.
     */
    public synchronized void disconnect() {
        if (connection == null) {
            return;
        }

        connection.disconnect();
        connection = null;

        log.info("RavenClient disconnected");
        handler.onDisconnect();
    }

    /**
     * Sends a message to the server. Thread-safe.
     *
     * @param message the message to send
     */
    public void send(final Message message) {
        if (!isConnected()) {
            log.warn("Cannot send message: not connected");
            return;
        }

        connection.send(message);
    }

    /**
     * Returns whether the client is currently connected to the server.
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    private void receiveLoop() {
        while (isConnected()) {
            final var message = connection.receive();
            if (message == null) {
                break;
            }

            handler.onMessage(message);
        }

        disconnect();
    }
}
