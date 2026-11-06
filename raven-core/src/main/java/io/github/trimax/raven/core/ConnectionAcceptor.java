package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.TryUtil;
import io.github.trimax.raven.core.util.retry.ExponentialRetryStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Accepts TCP connections and performs the handshake for each one on its own virtual thread,
 * so a slow or silent peer never delays the accept loop or other peers.
 *
 * <p>The handshake reads the peer's stream header and is bounded by the handshake timeout.
 * A connection that does not complete the handshake in time is closed.
 *
 * <p>At most {@code maxConnections} connections are served at once, counting both handshakes and established
 * sessions. A connection accepted beyond the limit is closed at once. Each accepted connection holds one permit,
 * released exactly once: by the handshake if it fails, otherwise when its {@link Connection} is disconnected.
 */
@Slf4j
final class ConnectionAcceptor {

    /** While the connection limit stays reached, at most one drop per interval is logged at WARN. */
    private static final Duration LIMIT_WARNING_INTERVAL = Duration.ofSeconds(10);

    /** Pause after each failed accept(), so a persistent failure such as running out of file descriptors does not spin. */
    private static final ExponentialRetryStrategy ACCEPT_BACKOFF = new ExponentialRetryStrategy(10, 1000);

    /** Already validated by the owning server: the handshake timeout fits into an {@code int} of milliseconds. */
    private final RavenServerConfiguration configuration;

    /** Whether the owning server is still running. */
    private final BooleanSupplier running;

    /** Invoked on the handshake thread with the raw socket and the established client. */
    private final BiConsumer<Socket, Client> onAccepted;

    // Sockets still in the handshake, closed by closeHandshakes() so they do not linger until the handshake timeout
    private final Set<Socket> handshakes = ConcurrentHashMap.newKeySet();

    /** One permit per connection in the handshake or in a session. */
    private final Semaphore permits;

    /** {@link System#nanoTime()} from which the next dropped connection is logged at WARN. */
    private final AtomicLong nextLimitWarning = new AtomicLong(System.nanoTime());

    ConnectionAcceptor(final RavenServerConfiguration configuration, final BooleanSupplier running,
                       final BiConsumer<Socket, Client> onAccepted) {
        this.configuration = configuration;
        this.running = running;
        this.onAccepted = onAccepted;
        this.permits = new Semaphore(configuration.getMaxConnections());
    }

    /**
     * Starts the accept loop on an already bound server socket. The loop ends once that socket is closed,
     * even if the owning server has been started again on a new socket.
     *
     * @return the accept thread
     */
    Thread start(final ServerSocket serverSocket) {
        return Thread.ofVirtual().name("raven-accept").start(() -> acceptLoop(serverSocket));
    }

    /**
     * Closes every socket still in the handshake. Called once the owning server is no longer running,
     * so a handshake that starts afterward sees that and closes its own socket.
     */
    void closeHandshakes() {
        handshakes.forEach(handshake -> TryUtil.execute(handshake::close));
    }

    private void acceptLoop(final ServerSocket serverSocket) {
        // Consecutive failed accept() calls; reset by the next successful one
        int failures = 0;

        // running alone is not enough: after stop() and start() it is true again for the new socket,
        // and accept() on this closed socket would fail at once, forever
        while (running.getAsBoolean() && !serverSocket.isClosed()) {
            final Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (final IOException ex) {
                if (serverSocket.isClosed() || !running.getAsBoolean())
                    break;

                failures++;
                if (failures == 1)
                    log.error("Error accepting connection, backing off: {}", ex.getMessage());
                else
                    log.debug("Error accepting connection ({} in a row): {}", failures, ex.getMessage());

                if (!backOff(failures))
                    break;
                continue;
            }

            if (failures > 0) {
                log.info("Accepting connections again after {} failures", failures);
                failures = 0;
            }

            if (!permits.tryAcquire()) {
                drop(clientSocket);
                continue;
            }

            // The handshake blocks on the peer's stream header, so it must not run on the accept thread
            final var releasePermit = releaseOnce();
            Thread.ofVirtual().name("raven-handshake").start(() -> handshake(clientSocket, releasePermit));
        }
    }

    /**
     * Sleeps before the next accept() attempt. Closing the socket does not wake the sleep, but the loop checks
     * the socket right after it, so a stop ends the loop within one delay (at most a second).
     *
     * @return {@code false} if the accept thread was interrupted and must end
     */
    private static boolean backOff(final int failures) {
        try {
            Thread.sleep(ACCEPT_BACKOFF.getDelay(failures));
            return true;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Closes a connection accepted beyond the limit. Logged at WARN at most once per interval, so a flood of
     * connections cannot flood the log.
     */
    private void drop(final Socket clientSocket) {
        TryUtil.execute(clientSocket::close);

        final var now = System.nanoTime();
        final var next = nextLimitWarning.get();
        if (now - next >= 0 && nextLimitWarning.compareAndSet(next, now + LIMIT_WARNING_INTERVAL.toNanos()))
            log.warn("Connection limit {} reached, dropping connections from {}; further drops within {}s are logged at DEBUG",
                    configuration.getMaxConnections(), clientSocket.getRemoteSocketAddress(),
                    LIMIT_WARNING_INTERVAL.toSeconds());
        else
            log.debug("Connection limit {} reached, dropped connection from {}",
                    configuration.getMaxConnections(), clientSocket.getRemoteSocketAddress());
    }

    /**
     * Returns a callback that releases one permit on its first call and does nothing afterward.
     */
    private Runnable releaseOnce() {
        final var released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true))
                permits.release();
        };
    }

    /**
     * Returns the number of connections that can still be accepted. Package-private for tests only.
     */
    int availablePermits() {
        return permits.availablePermits();
    }

    /**
     * Performs the handshake. The permit passes to the connection once it is open; until then the handshake
     * releases it on failure.
     */
    private void handshake(final Socket clientSocket, final Runnable releasePermit) {
        final Connection connection;
        handshakes.add(clientSocket);
        try {
            // closeHandshakes() runs after the server is marked stopped, so this re-check covers a late add
            if (!running.getAsBoolean())
                throw new IOException("server stopped");

            connection = Connection.open(clientSocket, configuration.getHandshakeTimeout(), configuration.getIdleTimeout(),
                    configuration.getSendQueueCapacity(), configuration.getSendTimeout(), releasePermit);
        } catch (final IOException ex) {
            if (running.getAsBoolean())
                log.error("Error setting up connection from {}: {}",
                        clientSocket.getRemoteSocketAddress(), ex.getMessage());
            TryUtil.execute(clientSocket::close);
            releasePermit.run();
            return;
        } catch (final RuntimeException ex) {
            // Not expected, but the permit must not leak with the socket
            TryUtil.execute(clientSocket::close);
            releasePermit.run();
            throw ex;
        } finally {
            handshakes.remove(clientSocket);
        }

        onAccepted.accept(clientSocket, new Client(connection));
    }
}
