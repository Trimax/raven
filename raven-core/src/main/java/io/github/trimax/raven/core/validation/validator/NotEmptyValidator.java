package io.github.trimax.raven.core.validation.validator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Set;

import io.github.trimax.raven.core.util.CollectionUtil;
import io.github.trimax.raven.core.util.StringUtil;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.NotEmpty;

/**
 * Validator for the {@link NotEmpty} constraint.
 * Supports String, Collection, and array types. Returns a violation when the value is null or empty.
 */
public final class NotEmptyValidator implements ConstraintValidator {

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return NotEmpty.class;
    }

    @Override
    public Set<Class<?>> supportedTypes() {
        return Set.of(String.class, Collection.class, Object[].class);
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
                if (value.getClass().isArray() && Array.getLength(value) == 0)
                    return new Violation(fieldName, NotEmpty.class, "field '" + fieldName + "' must not be empty");
            }
        }

        return null;
    }
}
