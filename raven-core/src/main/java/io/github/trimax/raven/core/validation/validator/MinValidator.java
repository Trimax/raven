package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Min;

/**
 * Validator for the {@link Min} constraint.
 * Validates that the numeric field value is greater than or equal to the specified minimum.
 * Null values are skipped (valid).
 */
public final class MinValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Min.class;
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class);
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Min min = (Min) annotation;
        final long longValue = ((Number) value).longValue();
        final long minValue = min.value();

        if (longValue < minValue)
            return new Violation(fieldName, Min.class,
                "field '" + fieldName + "' must be >= " + minValue + ", got: " + longValue);

        return null;
    }
}
