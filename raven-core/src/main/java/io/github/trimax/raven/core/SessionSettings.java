package io.github.trimax.raven.core;

import java.io.IOException;
import java.time.Duration;

import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;

/**
 * The settings of one {@link Session}, taken from an already validated configuration.
 *
 * @param handshakeTimeout bounds the hello exchange
 * @param idleTimeout      how long an open session may receive nothing; {@link Duration#ZERO} waits forever
 * @param connectTimeout   bounds the TCP connect of a dialing session; unused by accepted sessions
 */
record SessionSettings(MessageCodec codec, Duration handshakeTimeout, Duration idleTimeout, int sendQueueCapacity,
                       Duration sendTimeout, int maxMessageSize, Duration connectTimeout) {

    static SessionSettings of(final RavenServerConfiguration configuration) {
        return new SessionSettings(configuration.getCodec(), configuration.getHandshakeTimeout(),
                configuration.getIdleTimeout(), configuration.getSendQueueCapacity(), configuration.getSendTimeout(),
                (int) configuration.getMaxMessageSize().toBytes(), Duration.ZERO);
    }

    static SessionSettings of(final RavenClientConfiguration configuration) {
        // The connect timeout also bounds the hello exchange with the server
        return new SessionSettings(configuration.getCodec(), configuration.getConnectTimeout(),
                configuration.getIdleTimeout(), configuration.getSendQueueCapacity(), configuration.getSendTimeout(),
                (int) configuration.getMaxMessageSize().toBytes(), configuration.getConnectTimeout());
    }

    /**
     * Encodes a message into a payload on the calling thread, so the payload is a snapshot of the message as it is now.
     *
     * @throws IllegalArgumentException if the message cannot be serialized or is larger than {@code maxMessageSize}
     */
    byte[] encode(final Message message) {
        final byte[] payload;
        try {
            payload = codec.encode(message);
        } catch (final IOException ex) {
            // The codec writes to memory, so a failure is about the message itself, e.g. a non-serializable field
            throw new IllegalArgumentException("Cannot serialize " + message.getClass().getName() + ": " + ex, ex);
        }

        if (payload.length > maxMessageSize)
            throw new IllegalArgumentException(message.getClass().getName() + " is " + payload.length
                    + " bytes serialized, more than maxMessageSize " + maxMessageSize);

        return payload;
    }
}
