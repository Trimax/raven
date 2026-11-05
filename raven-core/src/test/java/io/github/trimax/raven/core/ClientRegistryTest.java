package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClientRegistry}.
 */
class ClientRegistryTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final List<Closeable> resources = new ArrayList<>();

    @AfterEach
    void closeResources() throws IOException {
        for (final var resource : resources)
            resource.close();
    }

    @Test
    void addedClientCanBeLookedUpById() throws IOException {
        final var client = newClient();

        registry.add(client);

        assertSame(client, registry.get(client.getId()));
        assertEquals(Set.of(client.getId()), registry.ids());
    }

    @Test
    void removeHandsOutClientOnlyOnce() throws IOException {
        final var client = newClient();
        registry.add(client);

        assertSame(client, registry.remove(client.getId()));
        assertNull(registry.remove(client.getId()), "second removal must not hand out the client again");
        assertNull(registry.get(client.getId()));
    }

    @Test
    void idsReturnsSnapshotNotLiveView() throws IOException {
        final var first = newClient();
        final var second = newClient();
        registry.add(first);

        final var snapshot = registry.ids();
        registry.add(second);

        assertEquals(Set.of(first.getId()), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second.getId()));
    }

    @Test
    void removeAllReturnsEveryClientAndEmptiesRegistry() throws IOException {
        final var first = newClient();
        final var second = newClient();
        registry.add(first);
        registry.add(second);

        final var removed = registry.removeAll();

        assertEquals(Set.of(first, second), Set.copyOf(removed));
        assertTrue(registry.ids().isEmpty());
        assertTrue(registry.removeAll().isEmpty());
    }

    @Test
    void removeAllSkipsConnectingClientUntilMarkedConnected() throws IOException {
        final var connecting = newClient();
        final var connected = newClient();
        registry.addConnecting(connecting);
        registry.add(connected);

        assertEquals(List.of(connected), registry.removeAll());
        assertSame(connecting, registry.get(connecting.getId()));

        registry.markConnected(connecting.getId());

        assertEquals(List.of(connecting), registry.removeAll());
        assertTrue(registry.ids().isEmpty());
    }

    @Test
    void removeHandsOutConnectingClient() throws IOException {
        final var client = newClient();
        registry.addConnecting(client);

        assertSame(client, registry.remove(client.getId()));
        assertNull(registry.remove(client.getId()));
    }

    /**
     * Creates a connected client over loopback. The peer side writes the stream header the client expects.
     */
    private Client newClient() throws IOException {
        final var server = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        resources.add(server);
        final var socket = new Socket("localhost", server.getLocalPort());
        resources.add(socket);
        final var accepted = server.accept();
        resources.add(accepted);

        new ObjectOutputStream(accepted.getOutputStream()).flush();
        return new Client(TestConnections.open(socket));
    }
}
