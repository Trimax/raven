package io.github.trimax.raven.core.exception;

public abstract class AbstractRavenException extends RuntimeException {
    AbstractRavenException(final String message) {
        this(message, null);
    }

    AbstractRavenException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
