package io.github.trimax.raven.core;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Base class for all messages transmitted over the Raven network framework.
 * Every application message must extend this class.
 */
@Getter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id = UUID.randomUUID();
    private final long timestamp = System.currentTimeMillis();
}
