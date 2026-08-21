package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.github.trimax.raven.core.validation.Violation;

/**
 * Common interface for all constraint validators.
 * Each implementation handles a single annotation type and validates field values against its constraint.
 */
public interface ConstraintValidator {

    /**
     * Returns the annotation class this validator handles.
     *
     * @return the annotation type
     */
    Class<? extends Annotation> getAnnotationType();

    /**
     * Returns the set of field types this validator supports.
     * If the set is empty, the validator supports any type.
     * Used at scan time to verify that annotations are placed on compatible fields.
     *
     * @return supported field types, or empty set for any type
     */
    default Set<Class<?>> supportedTypes() {
        return Set.of();
    }

    /**
     * Validates the field value against the constraint.
     *
     * @param fieldName  the field name (for error messages)
     * @param value      the field value (may be null)
     * @param annotation the annotation instance (for reading attributes)
     * @return a {@link Violation} if invalid, or {@code null} if valid
     */
    Violation validate(String fieldName, Object value, Annotation annotation);
}
