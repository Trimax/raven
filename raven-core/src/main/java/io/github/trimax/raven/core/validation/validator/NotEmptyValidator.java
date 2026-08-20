package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.util.Collection;

import io.github.trimax.raven.core.util.CollectionUtil;
import io.github.trimax.raven.core.util.StringUtil;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.NotEmpty;

/**
 * Validator for the {@link NotEmpty} constraint.
 * Supports String and Collection types. Returns a violation when the value is null or empty.
 */
public final class NotEmptyValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public Violation validate(final String fieldName, final Object value, final Annotation annotation) {
        switch (value) {
            case String string -> {
                if (StringUtil.isEmpty(string))
                    return new Violation(fieldName, NotEmpty.class, "field '" + fieldName + "' must not be empty");
            }
            case Collection<?> collection -> {
                if (CollectionUtil.isEmpty(collection))
                    return new Violation(fieldName, NotEmpty.class, "field '" + fieldName + "' must not be empty");
            }
            case null -> {
                return new Violation(fieldName, NotEmpty.class, "field '" + fieldName + "' must not be empty");
            }
            default -> {
            }
        }

        return null;
    }
}
