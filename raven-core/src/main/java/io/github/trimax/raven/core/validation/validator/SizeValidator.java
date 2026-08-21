package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Collection;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Size;

/**
 * Validator for the {@link Size} constraint.
 * Validates that the collection size is within the specified range. Null values are skipped (valid).
 */
public final class SizeValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Size.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Size size = (Size) annotation;
        final Collection<?> collection = (Collection<?>) value;
        final long min = size.min();
        final long max = size.max();
        final int actualSize = collection.size();

        if (actualSize < min || actualSize > max)
            return new Violation(fieldName, Size.class,
                "field '" + fieldName + "' size must be between " + min + " and " + max
                    + ", got: " + actualSize);

        return null;
    }
}
