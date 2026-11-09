package io.github.trimax.raven.core.internal.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import io.github.trimax.raven.core.internal.codec.RejectedMessageException;
import io.github.trimax.raven.core.internal.util.ThreadUtil;
import io.github.trimax.raven.core.util.TryUtil;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * One connection, accepted by a server or dialed by a client. A single session thread owns its whole life cycle:
 * connecting, the hello exchange, reading frames, and every call to its {@link SessionListener}. Outgoing payloads
 * are written by its {@link Transmitter}.
 *
 * <p>{@link #close()} may be called from any thread at any time; it closes the socket, which aborts whatever the
 * session thread is blocked in, and the thread then finishes the session.
 *
 * <p>Internal: not part of the API.
 */
@Slf4j
@Accessors(fluent = true)
public final class Session {

    enum State { CONNECTING, OPEN, CLOSED }

    @Getter
    private final UUID id = UUID.randomUUID();

    private final Socket socket;

    /** Where a dialing session connects to; {@code null} for an accepted one. */
    private final InetSocketAddress target;

    @Getter
    private final SessionSettings settings;
    private final SessionListener listener;
    private final Transmitter transmitter;

    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);

    /** Completes once {@code onOpen} has returned, or the session ended without it. */
    @Getter
    private final CompletableFuture<Void> opened = new CompletableFuture<>();

    /** Completes once the session is over: {@code onClose} has returned, or was never due. */
    @Getter
    private final CompletableFuture<Void> ended = new CompletableFuture<>();

    private Session(final Socket socket, final InetSocketAddress target, final SessionSettings settings,
                    final SessionListener listener) {
        this.socket = socket;
        this.target = target;
        this.settings = settings;
        this.listener = listener;
        this.transmitter = new Transmitter(settings.sendQueueCapacity(), settings.sendTimeout(), this::close);
    }

    /**
     * A session on a socket a server has accepted.
     */
    public static Session accepted(final Socket socket, final SessionSettings settings, final SessionListener listener) {
        return new Session(socket, null, settings, listener);
    }

    /**
     * A session that connects to {@code host:port} once started. The host is resolved on the session thread, so a
     * slow DNS lookup blocks no caller.
     */
    public static Session dialing(final String host, final int port, final SessionSettings settings, final SessionListener listener) {
        return new Session(new Socket(), InetSocketAddress.createUnresolved(host, port), settings, listener);
    }

    /**
     * Starts the session thread. Called once. A session closed before it was started still runs and ends at once,
     * without any callback.
     */
    public void start(final ExecutorService threads) {
        // Server-side sessions are named after the client they serve, the client's after its old receive thread
        final var name = target == null ? "raven-client-" + id : "raven-receiver";
        try {
            ThreadUtil.execute(threads, name, () -> ThreadUtil.runCallbacks(() -> run(threads)));
        } catch (final RejectedExecutionException ex) {
            // The owner is shutting down: the session ends before it began
            close();
            opened.complete(null);
            ended.complete(null);
        }
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    /**
     * Returns the peer's address, for the log.
     */
    public String remoteAddress() {
        final var connected = socket.getRemoteSocketAddress();
        // A dialing session that never connected has only its unresolved target
        return connected != null ? connected.toString() : target.getHostString() + ":" + target.getPort();
    }

    /**
     * Queues a payload for the peer and returns once it is queued; write errors close the session instead of being
     * thrown. If the queue is full, waits up to the send timeout for space, then treats the peer as stalled and
     * closes the session. Dropped if the session is not open.
     */
    public void send(final byte[] payload) {
        // A session closed while the sender waited is not a stalled peer, so it is not reported as one
        if (isOpen() && !transmitter.offer(payload) && isOpen()) {
            log.warn("Send queue full for {}, disconnecting stalled peer", settings.sendTimeout());
            close();
        }
    }

    /**
     * Closes the session. Safe to call more than once and from any thread, including the session thread itself.
     */
    public void close() {
        if (state.getAndSet(State.CLOSED) == State.CLOSED)
            return;

        TryUtil.execute(socket::close);
        transmitter.stop();
    }

    private void run(final ExecutorService threads) {
        var announced = false;
        try {
            final var input = handshake(threads);
            // close() may have come first; the session then ends here without any callback
            if (!state.compareAndSet(State.CONNECTING, State.OPEN) || !listener.onOpen(this))
                return;

            announced = true;
            opened.complete(null);
            receive(input);
        } catch (final SocketTimeoutException ex) {
            if (announced)
                log.info("Closing idle connection to {}: nothing received within the idle timeout", remoteAddress());
            else
                logFailure(false, ex);
        } catch (final IOException ex) {
            logFailure(announced, ex);
        } catch (final RuntimeException ex) {
            // Listeners isolate user callbacks, so this is a codec or validator failing on a peer's message
            log.warn("Closing connection to {} after an unexpected failure", remoteAddress(), ex);
        } finally {
            close();
            opened.complete(null);
            try {
                if (announced)
                    listener.onClose(this);
            } finally {
                ended.complete(null);
            }
        }
    }

    /**
     * Connects if dialing, then exchanges hellos and starts the transmitter.
     *
     * @return the input stream to read frames from
     */
    private DataInputStream handshake(final ExecutorService threads) throws IOException {
        if (target != null)
            socket.connect(new InetSocketAddress(target.getHostString(), target.getPort()), millis(settings.connectTimeout()));

        socket.setSoTimeout(millis(settings.handshakeTimeout()));
        final var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        final var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        // Both sides write their hello before reading the peer's, so neither waits for the other
        WireFormat.writeHello(output);
        WireFormat.readHello(input);

        // From now on the socket timeout is the idle timeout, 0 (wait forever) unless configured
        socket.setSoTimeout(millis(settings.idleTimeout()));
        // Started before onOpen, so the listener can already send
        transmitter.start(output, threads);
        return input;
    }

    private void receive(final DataInputStream input) throws IOException {
        while (isOpen()) {
            final var message = settings.codec().decode(WireFormat.readFrame(input, settings.maxMessageSize()));
            // An allowed object that is not a message breaks the protocol just like a rejected one
            if (message == null || !listener.onMessage(this, message))
                return;
        }
    }

    private void logFailure(final boolean announced, final IOException ex) {
        // close() aborted the blocked I/O: an ordinary end, already decided by whoever closed
        if (isClosed())
            log.debug("Connection to {} closed: {}", remoteAddress(), ex.getMessage());
        else if (ex instanceof RejectedMessageException rejected)
            log.warn("Rejected {} from {}, closing the connection: {}", rejected.subject(), remoteAddress(), rejected.reason());
        else if (ex instanceof ProtocolException || ex instanceof ObjectStreamException)
            log.warn("Closing connection to {}, protocol violation: {}", remoteAddress(), ex.getMessage());
        else if (!announced)
            log.warn("Failed to set up connection with {}: {}", remoteAddress(), ex.getMessage());
        else
            log.debug("Connection to {} ended: {}", remoteAddress(), ex.toString());
    }

    private static int millis(final Duration duration) {
        // Already validated to fit into an int
        return (int) duration.toMillis();
    }
}
