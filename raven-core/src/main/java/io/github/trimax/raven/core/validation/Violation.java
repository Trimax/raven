package io.github.trimax.raven.core.validation;

import java.lang.annotation.Annotation;

import lombok.NonNull;

/**
 * Represents a single constraint violation detected during message validation.
 *
 * @param fieldName  the name of the field that failed validation
 * @param constraint the simple name of the constraint annotation (e.g., "NotBlank")
 * @param message    a human-readable description of the violation
 */
public record Violation(
    String fieldName,
    String constraint,
    String message
) {

    public Violation(final String fieldName, final Class<? extends Annotation> validationClass, final String message) {
        this(fieldName, validationClass.getSimpleName(), message);
    }

    /**
     * Returns the human-readable violation message for clean log output.
     */
    @NonNull
    @Override
    public String toString() {
        return message;
    }
}
