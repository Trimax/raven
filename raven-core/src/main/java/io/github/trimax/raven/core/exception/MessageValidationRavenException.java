package io.github.trimax.raven.core.exception;

import java.util.List;
import java.util.stream.Collectors;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.validation.Violation;
import lombok.Getter;

/**
 * Thrown when a message fails validation on the sending path.
 * Contains the message type and the list of constraint violations.
 */
@Getter
public final class MessageValidationRavenException extends AbstractRavenException {
    /**
     * -- GETTER --
     *  Returns the message class that failed validation.
     */
    private final Class<? extends Message> messageType;
    /**
     * -- GETTER --
     *  Returns an unmodifiable list of constraint violations.
     */
    private final List<Violation> violations;

    /**
     * Creates a new validation exception with a descriptive message built from the violations.
     *
     * @param messageType the class of the message that failed validation
     * @param violations  the list of constraint violations (must not be empty)
     */
    public MessageValidationRavenException(final Class<? extends Message> messageType,
                                      final List<Violation> violations) {
        super(buildMessage(messageType, violations));
        this.messageType = messageType;
        this.violations = List.copyOf(violations);
    }

    private static String buildMessage(final Class<? extends Message> messageType,
                                       final List<Violation> violations) {
        return "Validation failed for " + messageType.getSimpleName() + ": " + violations.stream()
                .map(Violation::message)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
