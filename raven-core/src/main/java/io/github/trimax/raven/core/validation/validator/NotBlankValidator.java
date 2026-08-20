package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;

import io.github.trimax.raven.core.util.StringUtil;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.NotBlank;

/**
 * Validator for the {@link NotBlank} constraint.
 * Returns a violation when the string value is null or blank (empty or whitespace only).
 */
public final class NotBlankValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return NotBlank.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (StringUtil.isBlank((String) value))
            return new Violation(fieldName, NotBlank.class, "field '" + fieldName + "' must not be blank");

        return null;
    }
}
