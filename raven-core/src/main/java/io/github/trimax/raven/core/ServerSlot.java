package io.github.trimax.raven.core;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The current run of a {@link RavenServer}, or none: the server-side counterpart of {@link SessionSlot}, and the only
 * place with the server's start/stop concurrency. Every start gets a fresh {@link ServerRuntime}, so runs never mix.
 */
@Slf4j
@RequiredArgsConstructor
final class ServerSlot {

    private final RavenServerConfiguration configuration;

    /** The running instance, or {@code null}. Written under the lock; volatile because the readers go without it. */
    private volatile ServerRuntime current;

    /**
     * Binds and starts a new run, unless one is running. A failure to bind is logged and leaves the server stopped.
     */
    synchronized void open() {
        if (current != null)
            return;

        final ServerRuntime started;
        try {
            started = ServerRuntime.bind(configuration);
        } catch (final IOException ex) {
            log.error("Failed to start RavenServer on port {}: {}", configuration.getPort(), ex.getMessage());
            return;
        }

        current = started;
        log.info("RavenServer started on {}:{}", started.host(), started.port());
        started.start();
    }

    /**
     * Stops the current run, if any.
     *
     * @return completes once every session of that run has ended
     */
    synchronized CompletableFuture<Void> close() {
        final var stopping = current;
        if (stopping == null)
            return CompletableFuture.completedFuture(null);

        current = null;
        log.info("RavenServer stopping...");
        return stopping.stop().thenRun(() -> log.info("RavenServer stopped"));
    }

    boolean isOpen() {
        return current != null;
    }

    /**
     * Returns the bound port, or -1 if the server is not running.
     */
    int port() {
        final var run = current;
        return run == null ? -1 : run.port();
    }

    /**
     * Returns the IDs of the connected clients; empty if the server is not running.
     */
    Set<UUID> ids() {
        final var run = current;
        return run == null ? Set.of() : run.ids();
    }

    /**
     * Queues a frame for the given clients.
     *
     * @return {@code false} if the server is not running
     */
    boolean send(final byte[] frame, final Set<UUID> recipients) {
        final var run = current;
        if (run == null)
            return false;

        run.send(frame, recipients);
        return true;
    }

    /**
     * For tests: free connection slots of the current run, or the full limit if the server is not running.
     */
    int availableConnections() {
        final var run = current;
        return run == null ? configuration.getMaxConnections() : run.availableConnections();
    }
}
