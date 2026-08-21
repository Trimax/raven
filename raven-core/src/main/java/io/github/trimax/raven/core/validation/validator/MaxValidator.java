package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Max;

/**
 * Validator for the {@link Max} constraint.
 * Validates that the numeric field value is less than or equal to the specified maximum.
 * Null values are skipped (valid).
 */
public final class MaxValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Max.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Max max = (Max) annotation;
        final long longValue = ((Number) value).longValue();
        final long maxValue = max.value();

        if (longValue > maxValue)
            return new Violation(fieldName, Max.class,
                "field '" + fieldName + "' must be <= " + maxValue + ", got: " + longValue);

        return null;
    }
}
