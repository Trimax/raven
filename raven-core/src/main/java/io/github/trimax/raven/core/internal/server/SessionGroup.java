package io.github.trimax.raven.core.internal.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.github.trimax.raven.core.internal.session.Session;
import lombok.RequiredArgsConstructor;

/**
 * Holds the sessions of one {@link io.github.trimax.raven.core.RavenServer} run, each from its accept to its end, handshakes included, so that
 * {@link #closeAll()} can close them all.
 */
@RequiredArgsConstructor
final class SessionGroup {

    private final ExecutorService threads;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private volatile boolean closed;

    /**
     * Registers and starts a session; it leaves the group once it has ended. Closed at once if the group is closed.
     */
    void add(final Session session) {
        sessions.put(session.id(), session);
        session.ended().thenRun(() -> sessions.remove(session.id()));

        // closeAll() closes every session it sees; this one may have been added after it looked
        if (closed)
            session.close();

        session.start(threads);
    }

    /**
     * Returns the open session with this ID, or {@code null} if there is none.
     */
    Session get(final UUID id) {
        final var session = sessions.get(id);
        return session != null && session.isOpen() ? session : null;
    }

    /**
     * Returns an immutable snapshot of the IDs of the open sessions: those whose {@code onOpen} is running or has
     * returned {@code true}, until they close.
     */
    Set<UUID> ids() {
        return sessions.values().stream()
                .filter(Session::isOpen)
                .map(Session::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Closes every session, including any added later.
     *
     * @return completes once every session closed here has ended
     */
    CompletableFuture<Void> closeAll() {
        closed = true;
        final var closing = List.copyOf(sessions.values());
        closing.forEach(Session::close);
        return CompletableFuture.allOf(closing.stream().map(Session::ended).toArray(CompletableFuture[]::new));
    }
}
