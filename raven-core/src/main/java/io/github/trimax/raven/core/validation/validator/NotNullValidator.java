package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.NotNull;

/**
 * Validator for the {@link NotNull} constraint.
 * Returns a violation when the field value is null.
 */
public final class NotNullValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return NotNull.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return new Violation(fieldName, NotNull.class, "field '" + fieldName + "' must not be null");

        return null;
    }
}
