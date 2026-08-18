package io.github.trimax.raven.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Encapsulates a TCP socket connection with object stream I/O.
 * Provides thread-safe send, blocking receive, and clean disconnect.
 */
@Slf4j
final class Connection {

    private final Socket socket;
    private final ObjectOutputStream outputStream;
    private final ObjectInputStream inputStream;

    @Getter
    private volatile boolean connected;

    Connection(final Socket socket) throws IOException {
        this.socket = socket;
        this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        this.inputStream = new ObjectInputStream(socket.getInputStream());
        this.connected = true;
    }

    /**
     * Sends a message over this connection. Thread-safe.
     */
    synchronized void send(final Message message) {
        if (!connected)
            return;

        try {
            outputStream.writeObject(message);
            outputStream.flush();
            outputStream.reset();
        } catch (final IOException ex) {
            log.warn("Failed to send message: {}", ex.getMessage());
            disconnect();
        }
    }

    /**
     * Reads the next message from the input stream. Blocks until available.
     *
     * @return the received message, or null if the connection is closed or an error occurs
     */
    Message receive() {
        try {
            final var object = inputStream.readObject();
            if (object instanceof Message message)
                return message;

            log.warn("Received non-RavenMessage object: {}", object.getClass().getName());
            return null;
        } catch (final IOException | ClassNotFoundException ex) {
            connected = false;
            return null;
        }
    }

    /**
     * Closes the connection, releasing all resources.
     * Closing the socket also closes the associated streams.
     */
    void disconnect() {
        if (!connected)
            return;

        connected = false;

        try {
            socket.close();
        } catch (final IOException ex) {
            log.debug("Error closing socket: {}", ex.getMessage());
        }
    }
}
