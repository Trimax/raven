package io.github.trimax.raven;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Connection}.
 */
class ConnectionTest {

    @Test
    void newConnectionIsConnected() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();
            final var latch = new CountDownLatch(1);

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    new Connection(accepted);
                    latch.countDown();
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = new Connection(socket);

            latch.await(2, TimeUnit.SECONDS);
            assertTrue(connection.isConnected());

            connection.disconnect();
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void disconnectClosesConnection() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    new ObjectOutputStream(accepted.getOutputStream());
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = new Connection(socket);

            connection.disconnect();

            assertFalse(connection.isConnected());
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void doubleDisconnectIsSafe() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    new ObjectOutputStream(accepted.getOutputStream());
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = new Connection(socket);

            connection.disconnect();
            assertDoesNotThrow(connection::disconnect);
        }
    }

    @Test
    void sendAfterDisconnectDoesNotThrow() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    new ObjectOutputStream(accepted.getOutputStream());
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = new Connection(socket);

            connection.disconnect();
            assertDoesNotThrow(() -> connection.send(new TestMessage("should not throw")));
        }
    }

    @Test
    void sendAndReceiveMessage() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();
            final var serverConnection = new AtomicReference<Connection>();
            final var latch = new CountDownLatch(1);

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    serverConnection.set(new Connection(accepted));
                    latch.countDown();
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var clientConnection = new Connection(socket);

            latch.await(2, TimeUnit.SECONDS);

            clientConnection.send(new TestMessage("ping"));
            final var received = serverConnection.get().receive();

            assertNotNull(received);
            assertInstanceOf(TestMessage.class, received);
            assertEquals("ping", ((TestMessage) received).getContent());

            clientConnection.disconnect();
            serverConnection.get().disconnect();
        }
    }
}
