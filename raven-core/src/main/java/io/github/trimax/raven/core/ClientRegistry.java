package io.github.trimax.raven.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of the clients currently connected to a {@link RavenServer}.
 *
 * <p>{@link #remove(UUID)} hands a client out only to the first caller. {@link SessionManager} relies on this
 * to report each client's disconnect exactly once, whether it is triggered by the client's own receive
 * loop or by {@link RavenServer#stop()}.
 *
 * <p>A client registered with {@link #addConnecting(Client)} stays out of {@link #removeAll()} until
 * {@link #markConnected(UUID)}, so a disconnect is never reported for a client whose {@code onConnect}
 * has not returned yet.
 */
final class ClientRegistry {

    private final Map<UUID, Client> clients = new ConcurrentHashMap<>();

    // Registered clients whose onConnect has not returned yet; removeAll() leaves them to the registering thread
    private final Set<UUID> connecting = ConcurrentHashMap.newKeySet();

    void add(final Client client) {
        clients.put(client.getId(), client);
    }

    /**
     * Registers a client whose {@code onConnect} is about to run. It is visible to {@link #get(UUID)},
     * {@link #ids()} and {@link #remove(UUID)}, but skipped by {@link #removeAll()} until
     * {@link #markConnected(UUID)} is called.
     */
    void addConnecting(final Client client) {
        connecting.add(client.getId());
        clients.put(client.getId(), client);
    }

    /**
     * Marks a client registered with {@link #addConnecting(Client)} as connected, making it eligible
     * for {@link #removeAll()}.
     */
    void markConnected(final UUID id) {
        connecting.remove(id);
    }

    Client get(final UUID id) {
        return clients.get(id);
    }

    /**
     * Removes the client with the given ID.
     *
     * @return the removed client, or {@code null} if it was not registered (or already removed)
     */
    Client remove(final UUID id) {
        return clients.remove(id);
    }

    /**
     * Returns an immutable snapshot of the registered client IDs.
     */
    Set<UUID> ids() {
        return Set.copyOf(clients.keySet());
    }

    /**
     * Removes every registered client except those still connecting, and returns them.
     */
    List<Client> removeAll() {
        final var removed = new ArrayList<Client>();
        for (final var id : clients.keySet()) {
            if (connecting.contains(id))
                continue;

            final var client = clients.remove(id);
            if (client != null)
                removed.add(client);
        }
        return removed;
    }
}
