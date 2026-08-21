package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

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
     * Validates the field value against the constraint.
     *
     * @param fieldName  the field name (for error messages)
     * @param value      the field value (may be null)
     * @param annotation the annotation instance (for reading attributes)
     * @return a {@link Violation} if invalid, or {@code null} if valid
     */
    Violation validate(String fieldName, Object value, Annotation annotation);
}
