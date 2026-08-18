package io.github.trimax.raven;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Base class for all messages transmitted over the Raven network framework.
 * Every application message must extend this class.
 */
@Getter
public abstract class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id = UUID.randomUUID();
    private final long timestamp = System.currentTimeMillis();
}
