package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.DecimalRange;

/**
 * Validator for the {@link DecimalRange} constraint.
 * Validates that the decimal field value is within the specified inclusive range.
 * Null values are skipped (valid).
 */
public final class DecimalRangeValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return DecimalRange.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final DecimalRange decimalRange = (DecimalRange) annotation;
        final double doubleValue = ((Number) value).doubleValue();
        final double min = decimalRange.min();
        final double max = decimalRange.max();

        if (doubleValue < min || doubleValue > max)
            return new Violation(fieldName, DecimalRange.class,
                "field '" + fieldName + "' must be >= " + min + " and <= " + max + ", got: " + doubleValue);

        return null;
    }
}
