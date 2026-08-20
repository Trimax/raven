package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Range;

/**
 * Validator for the {@link Range} constraint.
 * Validates that the numeric field value is within the specified inclusive range.
 * Null values are skipped (valid).
 */
public final class RangeValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Range.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Range range = (Range) annotation;
        final long longValue = ((Number) value).longValue();
        final long min = range.min();
        final long max = range.max();

        if (longValue < min || longValue > max)
            return new Violation(fieldName, Range.class,
                "field '" + fieldName + "' must be >= " + min + " and <= " + max + ", got: " + longValue);

        return null;
    }
}
