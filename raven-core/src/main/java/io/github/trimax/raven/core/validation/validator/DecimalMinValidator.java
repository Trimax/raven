package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.DecimalMin;

/**
 * Validator for the {@link DecimalMin} constraint.
 * Validates that the decimal field value is greater than or equal to the specified minimum.
 * Null values are skipped (valid).
 */
public final class DecimalMinValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return DecimalMin.class;
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(float.class, Float.class, double.class, Double.class);
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final DecimalMin decimalMin = (DecimalMin) annotation;
        final double doubleValue = ((Number) value).doubleValue();
        final double minValue = decimalMin.value();

        if (doubleValue < minValue)
            return new Violation(fieldName, DecimalMin.class,
                "field '" + fieldName + "' must be >= " + minValue + ", got: " + doubleValue);

        return null;
    }
}
