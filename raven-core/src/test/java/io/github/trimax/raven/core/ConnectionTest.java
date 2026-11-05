package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

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
                    TestConnections.open(accepted);
                    latch.countDown();
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = TestConnections.open(socket);

            final var ignored = latch.await(2, TimeUnit.SECONDS);
            assertTrue(connection.isConnected());

            connection.disconnect();
            assertFalse(connection.isConnected());
        }
    }

    @Test
    void disconnectClosesConnection() throws Exception {
        // The peer is accepted on the test thread and kept open until the end, so setup cannot race
        try (final var serverSocket = new ServerSocket(0);
             final var socket = new Socket("localhost", serverSocket.getLocalPort());
             final var peer = serverSocket.accept()) {
            new ObjectOutputStream(peer.getOutputStream()).flush();
            final var connection = TestConnections.open(socket);

            connection.disconnect();

            assertFalse(connection.isConnected());
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void doubleDisconnectIsSafe() throws Exception {
        // The peer is accepted on the test thread and kept open until the end, so setup cannot race
        try (final var serverSocket = new ServerSocket(0);
             final var socket = new Socket("localhost", serverSocket.getLocalPort());
             final var peer = serverSocket.accept()) {
            new ObjectOutputStream(peer.getOutputStream()).flush();
            final var connection = TestConnections.open(socket);

            connection.disconnect();
            assertDoesNotThrow(connection::disconnect);
        }
    }

    @Test
    void sendAfterDisconnectDoesNotThrow() throws Exception {
        // The peer is accepted on the test thread and kept open until the end, so setup cannot race
        try (final var serverSocket = new ServerSocket(0);
             final var socket = new Socket("localhost", serverSocket.getLocalPort());
             final var peer = serverSocket.accept()) {
            new ObjectOutputStream(peer.getOutputStream()).flush();
            final var connection = TestConnections.open(socket);

            connection.disconnect();
            assertDoesNotThrow(() -> connection.send(new TestMessage("should not throw")));
        }
    }

    @Test
    void peerCloseReleasesSocket() throws Exception {
        try (final var serverSocket = new ServerSocket(0);
             final var socket = new Socket("localhost", serverSocket.getLocalPort())) {
            final var peer = serverSocket.accept();
            new ObjectOutputStream(peer.getOutputStream()).flush();
            final var connection = TestConnections.open(socket);

            peer.close();

            assertNull(connection.receive());
            assertFalse(connection.isConnected());
            assertTrue(socket.isClosed(), "socket must be closed once the peer has closed the connection");
        }
    }

    @Test
    void peerCloseStopsWriterThread() throws Exception {
        try (final var serverSocket = new ServerSocket(0);
             final var socket = new Socket("localhost", serverSocket.getLocalPort())) {
            final var peer = serverSocket.accept();
            new ObjectOutputStream(peer.getOutputStream()).flush();
            final var connection = TestConnections.open(socket);

            peer.close();

            assertNull(connection.receive());
            assertTrue(connection.writer.join(Duration.ofSeconds(5)), "writer thread must terminate once the peer has closed");
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void receiveReturnsNullForNonMessageObject() throws Exception {
        try (final var serverSocket = new ServerSocket(0)) {
            final var port = serverSocket.getLocalPort();
            final var latch = new CountDownLatch(1);

            Thread.ofVirtual().start(() -> {
                try {
                    final var accepted = serverSocket.accept();
                    final var oos = new ObjectOutputStream(accepted.getOutputStream());
                    // Send a non-Message object (plain String)
                    oos.writeObject("I am not a Message");
                    oos.flush();
                    latch.countDown();
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var connection = TestConnections.open(socket);

            latch.await(2, TimeUnit.SECONDS);

            final var received = connection.receive();
            assertNull(received, "Non-Message objects should result in null");

            connection.disconnect();
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
                    serverConnection.set(TestConnections.open(accepted));
                    latch.countDown();
                } catch (final IOException ignored) {
                }
            });

            final var socket = new Socket("localhost", port);
            final var clientConnection = TestConnections.open(socket);

            final var ignored = latch.await(2, TimeUnit.SECONDS);

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
