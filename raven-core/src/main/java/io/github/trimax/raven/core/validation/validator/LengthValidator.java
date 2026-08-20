package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.util.StringUtil;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Length;

/**
 * Validator for the {@link Length} constraint.
 * Validates that the string length is within the specified range. Null values are skipped (valid).
 */
public final class LengthValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Length.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Length length = (Length) annotation;
        final String string = (String) value;
        final int min = (int) length.min();
        final int max = (int) length.max();

        if (!StringUtil.hasLengthBetween(string, min, max))
            return new Violation(fieldName, Length.class,
                "field '" + fieldName + "' length must be between " + min + " and " + max
                    + ", got: " + string.length());

        return null;
    }
}
