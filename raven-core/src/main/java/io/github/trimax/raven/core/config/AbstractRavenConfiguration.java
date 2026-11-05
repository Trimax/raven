package io.github.trimax.raven.core.config;

import java.time.Duration;

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
     * Checks the settings shared by server and client, then the subclass's own settings.
     *
     * @throws IllegalArgumentException if a setting is out of range
     */
    public final void validate() {
        if (sendQueueCapacity < 1)
            throw new IllegalArgumentException("sendQueueCapacity must be at least 1, got " + sendQueueCapacity);

        if (sendTimeout.isNegative())
            throw new IllegalArgumentException("sendTimeout must not be negative, got " + sendTimeout);

        validateSpecific();
    }

    /**
     * Checks the settings specific to the server or the client.
     *
     * @throws IllegalArgumentException if a setting is out of range
     */
    abstract void validateSpecific();
}
