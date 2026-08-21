package io.github.trimax.raven.core.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.exception.MessageValidationRavenException;
import io.github.trimax.raven.core.validation.annotation.Valid;
import io.github.trimax.raven.core.validation.validator.ConstraintValidator;
import io.github.trimax.raven.core.validation.validator.DecimalMaxValidator;
import io.github.trimax.raven.core.validation.validator.DecimalMinValidator;
import io.github.trimax.raven.core.validation.validator.DecimalRangeValidator;
import io.github.trimax.raven.core.validation.validator.EmailValidator;
import io.github.trimax.raven.core.validation.validator.LengthValidator;
import io.github.trimax.raven.core.validation.validator.MatchesValidator;
import io.github.trimax.raven.core.validation.validator.MaxValidator;
import io.github.trimax.raven.core.validation.validator.MinValidator;
import io.github.trimax.raven.core.validation.validator.NotBlankValidator;
import io.github.trimax.raven.core.validation.validator.NotEmptyValidator;
import io.github.trimax.raven.core.validation.validator.NotNullValidator;
import io.github.trimax.raven.core.validation.validator.RangeValidator;
import io.github.trimax.raven.core.validation.validator.SizeValidator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Static utility class that validates {@link Message} instances against constraint annotations.
 * <p>
 * Scans the class hierarchy for fields annotated with validation constraints,
 * caches the metadata, and evaluates validators against field values.
 * Supports recursive validation of nested objects via {@link Valid}.
 * <p>
 * Thread-safe: uses a {@link ConcurrentHashMap} for caching reflection metadata.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageValidator {

    private static final Map<Class<?>, List<FieldMetadata>> CACHE = new ConcurrentHashMap<>();

    private static final List<ConstraintValidator> VALIDATORS = List.of(
        new NotNullValidator(),
        new NotBlankValidator(),
        new NotEmptyValidator(),
        new LengthValidator(),
        new SizeValidator(),
        new MinValidator(),
        new MaxValidator(),
        new RangeValidator(),
        new DecimalMinValidator(),
        new DecimalMaxValidator(),
        new DecimalRangeValidator(),
        new MatchesValidator(),
        new EmailValidator()
    );

    /**
     * Validates the given message and returns a list of constraint violations.
     * Returns an empty list if the message is valid.
     *
     * @param message the message to validate
     * @return list of violations (empty if valid)
     */
    public static List<Violation> validate(final Message message) {
        final List<Violation> violations = new ArrayList<>();
        validateObject(message, "", violations);

        return violations;
    }

    /**
     * Validates the given message and throws {@link MessageValidationRavenException} if any violations are found.
     *
     * @param message the message to validate
     * @throws MessageValidationRavenException if validation fails
     */
    public static void validateOrThrow(final Message message) {
        final var violations = validate(message);
        if (!violations.isEmpty())
            throw new MessageValidationRavenException(message.getClass(), violations);
    }

    private static void validateObject(final Object object, final String prefix, final List<Violation> violations) {
        final var fields = CACHE.computeIfAbsent(object.getClass(), MessageValidator::scanFields);

        for (final var field : fields)
            validateField(object, prefix, violations, field);
    }

    private static void validateField(final Object object, final String prefix, final List<Violation> violations, final FieldMetadata field) {
        final Object value;
        try {
            value = field.field().get(object);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Failed to access field: " + field.field().getName(), e);
        }

        final String fieldPath = prefix.isEmpty() ? field.field().getName() : prefix + "." + field.field().getName();
        for (final var constraint : field.constraints()) {
            final var violation = constraint.validator().validate(fieldPath, value, constraint.annotation());
            if (violation != null)
                violations.add(violation);
        }

        if (field.recursive() && value != null)
            validateRecursive(value, fieldPath, violations);
    }

    private static void validateRecursive(final Object value, final String path, final List<Violation> violations) {
        if (value instanceof Collection<?> collection) {
            validateCollection(path, violations, collection);
            return;
        }

        if (value.getClass().isArray()) {
            validateArray(value, path, violations);
            return;
        }

        validateObject(value, path, violations);
    }

    private static void validateArray(final Object value, final String path, final List<Violation> violations) {
        final int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            final var element = Array.get(value, i);
            if (element != null)
                validateRecursive(element, path + "[" + i + "]", violations);
        }
    }

    private static void validateCollection(final String path, final List<Violation> violations, final Collection<?> collection) {
        int index = 0;
        for (final var element : collection) {
            if (element != null)
                validateRecursive(element, path + "[" + index + "]", violations);
            index++;
        }
    }

    private static List<FieldMetadata> scanFields(final Class<?> clazz) {
        final List<FieldMetadata> result = new ArrayList<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (final Field field : current.getDeclaredFields()) {
                final var constraints = findConstraints(field);
                final var recursive = field.isAnnotationPresent(Valid.class);

                if (!constraints.isEmpty() || recursive) {
                    field.setAccessible(true);
                    result.add(new FieldMetadata(field, constraints, recursive));
                }
            }

            current = current.getSuperclass();
        }

        return result;
    }

    private static List<AnnotatedConstraint> findConstraints(final Field field) {
        final List<AnnotatedConstraint> constraints = new ArrayList<>();

        for (final var validator : VALIDATORS) {
            final var annotation = field.getAnnotation(validator.getAnnotationType());
            if (annotation != null) {
                validateFieldType(field, validator);
                constraints.add(new AnnotatedConstraint(validator, annotation));
            }
        }

        return constraints;
    }

    private static void validateFieldType(final Field field, final ConstraintValidator validator) {
        final var supportedTypes = validator.supportedTypes();
        if (supportedTypes.isEmpty())
            return;

        final var fieldType = field.getType();
        for (final var supportedType : supportedTypes) {
            if (supportedType.isAssignableFrom(fieldType))
                return;

            if (supportedType == Object[].class && fieldType.isArray())
                return;
        }

        throw new IllegalStateException(
            "@" + validator.getAnnotationType().getSimpleName()
                + " on field '" + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                + "' is not compatible with type " + fieldType.getSimpleName()
                + ". Supported types: " + supportedTypes
        );
    }

    private record FieldMetadata(Field field, List<AnnotatedConstraint> constraints, boolean recursive) {
    }

    private record AnnotatedConstraint(ConstraintValidator validator, Annotation annotation) {
    }
}
