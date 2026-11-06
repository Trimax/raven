package io.github.trimax.raven.core;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads messages from one connection until the session ends, shared by the server and the client.
 * What happens to each message (validation, interceptors, the handler) is up to the caller's {@code deliver}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReceiveLoop {

    /**
     * Runs the reception loop for the connection on the current thread.
     * Ends when the connection closes, {@code running} turns false, or {@code deliver} returns {@code false}.
     * The connection is always disconnected and {@code onEnded} is always invoked afterward.
     *
     * @param connection the connection to read from
     * @param running    whether the owner still wants messages; checked before each receive
     * @param deliver    handles one message; {@code false} ends the session
     * @param onEnded    invoked once the session ends, after the connection has been disconnected
     */
    static void run(final Connection connection, final BooleanSupplier running,
                    final Predicate<Message> deliver, final Runnable onEnded) {
        try {
            while (connection.isConnected() && running.getAsBoolean()) {
                final var message = connection.receive();
                if (message == null || !deliver.test(message))
                    break;
            }
        } finally {
            // Runs on normal exit and on errors, so the socket is never left open
            connection.disconnect();
            onEnded.run();
        }
    }
}
