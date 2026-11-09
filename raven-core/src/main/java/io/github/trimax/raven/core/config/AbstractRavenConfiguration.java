package io.github.trimax.raven.core.config;

import java.time.Duration;

import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.codec.MessageCodec;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Base configuration shared by both Raven server and client.
 */
@Getter
@SuperBuilder
abstract class AbstractRavenConfiguration {

    private final int port;

    /**
     * Maximum number of outgoing messages queued per connection before {@code send()} applies back-pressure.
     * Must be at least 1.
     */
    @Builder.Default
    private final int sendQueueCapacity = 1024;

    /**
     * How long {@code send()} waits for space in a full queue before the peer is considered stalled and disconnected.
     * Must not be negative; zero disconnects as soon as the queue is full.
     */
    @NonNull
    @Builder.Default
    private final Duration sendTimeout = Duration.ofSeconds(5);

    /**
     * How long an established connection may go without receiving anything before it is closed.
     * {@link Duration#ZERO} (the default) disables the timeout. Otherwise, it must be at least one millisecond and
     * no larger than {@link Integer#MAX_VALUE} milliseconds (about 24.8 days).
     *
     * <p>There is no heartbeat: a peer that is healthy but sends nothing for this long is disconnected too.
     */
    @NonNull
    @Builder.Default
    private final Duration idleTimeout = Duration.ZERO;

    /**
     * Largest serialized message that may be sent or received. {@code send()} rejects a larger message, and a peer
     * that announces a larger frame is disconnected. Between 1 byte and {@link Integer#MAX_VALUE} bytes; 16 MB by
     * default.
     */
    @NonNull
    @Builder.Default
    private final DataSize maxMessageSize = DataSize.ofMegabytes(16);

    /**
     * Turns messages into frame payloads and back. Both sides of a connection must use compatible codecs.
     * Defaults to Java serialization with a deserialization filter, see {@link JavaSerializationCodec}.
     */
    @NonNull
    @Builder.Default
    private final MessageCodec codec = new JavaSerializationCodec();

    /**
     * Checks the settings shared by server and client, then the subclass's own settings.
     *
     * @throws IllegalArgumentException if a setting is out of range
     */
    public final void validate() {
        if (sendQueueCapacity < 1)
            throw new IllegalArgumentException("sendQueueCapacity must be at least 1, got " + sendQueueCapacity);

        if (sendTimeout.isNegative())
            throw new IllegalArgumentException("sendTimeout must not be negative, got " + sendTimeout);

        // Used as a socket timeout, where 0 means "wait forever": a positive value that truncates to 0 ms would
        // silently disable it
        if (idleTimeout.isNegative())
            throw new IllegalArgumentException("idleTimeout must not be negative, got " + idleTimeout);

        if (idleTimeout.isPositive() && idleTimeout.toMillis() < 1)
            throw new IllegalArgumentException("idleTimeout must be zero or at least 1ms, got " + idleTimeout);

        if (idleTimeout.toMillis() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("idleTimeout is too large: " + idleTimeout);

        // A frame length is an int on the wire
        if (maxMessageSize.toBytes() < 1 || maxMessageSize.toBytes() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("maxMessageSize must be between 1 byte and " + Integer.MAX_VALUE
                    + " bytes, got " + maxMessageSize);

        validateSpecific();
    }

    /**
     * Checks the settings specific to the server or the client.
     *
     * @throws IllegalArgumentException if a setting is out of range
     */
    abstract void validateSpecific();
}
