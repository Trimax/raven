package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Matches;

/**
 * Validator for the {@link Matches} constraint.
 * Validates that the string field value matches the specified regular expression pattern.
 * Null values are skipped (valid).
 */
public final class MatchesValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Matches.class;
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(String.class);
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        if (value == null)
            return null;

        final Matches matches = (Matches) annotation;
        final String string = (String) value;
        final String pattern = matches.value();

        if (!string.matches(pattern))
            return new Violation(fieldName, Matches.class,
                "field '" + fieldName + "' must match pattern '" + pattern + "', got: '" + string + "'");

        return null;
    }
}
