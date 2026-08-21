package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.DecimalMax;

/**
 * Validator for the {@link DecimalMax} constraint.
 * Validates that the decimal field value is less than or equal to the specified maximum.
 * Null values are skipped (valid).
 */
public final class DecimalMaxValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return DecimalMax.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final DecimalMax decimalMax = (DecimalMax) annotation;
        final double doubleValue = ((Number) value).doubleValue();
        final double maxValue = decimalMax.value();

        if (doubleValue > maxValue)
            return new Violation(fieldName, DecimalMax.class,
                "field '" + fieldName + "' must be <= " + maxValue + ", got: " + doubleValue);

        return null;
    }
}
