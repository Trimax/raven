package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.github.trimax.raven.core.util.ThreadUtil;
import io.github.trimax.raven.core.util.TryUtil;
import io.github.trimax.raven.core.util.retry.ExponentialRetryStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the accept loop of one server socket and enforces the connection limit. Each accepted socket takes one of
 * {@code maxConnections} permits and is handed to {@code onAccepted}, whose session must call {@link #release()}
 * once it has ended. A connection accepted beyond the limit is closed at once.
 */
@Slf4j
final class Acceptor {

    /** While the connection limit stays reached, at most one drop per interval is logged at WARN. */
    private static final Duration LIMIT_WARNING_INTERVAL = Duration.ofSeconds(10);

    /** Pause after each failed accept(), so a persistent failure such as running out of file descriptors does not spin. */
    private static final ExponentialRetryStrategy ACCEPT_BACKOFF = new ExponentialRetryStrategy(10, 1000);

    private final ServerSocket serverSocket;
    private final int maxConnections;

    /** Invoked on the accept thread with every socket that got a permit; must not block. */
    private final Consumer<Socket> onAccepted;

    private final Semaphore permits;

    /** {@link System#nanoTime()} from which the next dropped connection is logged at WARN. */
    private final AtomicLong nextLimitWarning = new AtomicLong(System.nanoTime());

    Acceptor(final ServerSocket serverSocket, final int maxConnections, final Consumer<Socket> onAccepted) {
        this.serverSocket = serverSocket;
        this.maxConnections = maxConnections;
        this.onAccepted = onAccepted;
        this.permits = new Semaphore(maxConnections);
    }

    /**
     * Starts the accept loop, which ends once the server socket is closed.
     */
    void start(final ExecutorService threads) {
        ThreadUtil.execute(threads, "raven-accept", this::acceptLoop);
    }

    /**
     * Gives back the permit of one accepted connection. Called once per connection handed to {@code onAccepted}.
     */
    void release() {
        permits.release();
    }

    /**
     * Returns the number of connections that can still be accepted.
     */
    int availablePermits() {
        return permits.availablePermits();
    }

    private void acceptLoop() {
        // Consecutive failed accept() calls; reset by the next successful one
        int failures = 0;

        while (!serverSocket.isClosed()) {
            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (final IOException ex) {
                if (serverSocket.isClosed())
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

            if (permits.tryAcquire())
                onAccepted.accept(socket);
            else
                drop(socket);
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
    private void drop(final Socket socket) {
        TryUtil.execute(socket::close);

        final var now = System.nanoTime();
        final var next = nextLimitWarning.get();
        if (now - next >= 0 && nextLimitWarning.compareAndSet(next, now + LIMIT_WARNING_INTERVAL.toNanos()))
            log.warn("Connection limit {} reached, dropping connections from {}; further drops within {}s are logged at DEBUG",
                    maxConnections, socket.getRemoteSocketAddress(), LIMIT_WARNING_INTERVAL.toSeconds());
        else
            log.debug("Connection limit {} reached, dropped connection from {}", maxConnections, socket.getRemoteSocketAddress());
    }
}
