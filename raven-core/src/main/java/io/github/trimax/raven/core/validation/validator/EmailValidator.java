package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Email;

/**
 * Validator for the {@link Email} constraint.
 * Validates that the string field value is a valid email address using a simplified regex.
 * Null values are skipped (valid). The violation message gives the length of the value, never the value itself,
 * since it may come from an untrusted peer and end up in a log.
 */
public final class EmailValidator implements ConstraintValidator {

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Email.class;
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(String.class);
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final String string = (String) value;
        if (!string.matches(EMAIL_REGEX))
            return new Violation(fieldName, Email.class,
                "field '" + fieldName + "' must be a valid email address, got a string of length " + string.length());

        return null;
    }
}
