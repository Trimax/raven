package io.github.trimax.raven.core.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.exception.MessageValidationRavenException;
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
 * Scans the class hierarchy (up to but excluding {@code Message.class}) for fields annotated with
 * validation constraints, caches the metadata, and evaluates validators against field values.
 * <p>
 * Thread-safe: uses a {@link ConcurrentHashMap} for caching reflection metadata.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageValidator {

    private static final Map<Class<?>, List<FieldConstraints>> CACHE = new ConcurrentHashMap<>();

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
        final List<FieldConstraints> fieldConstraints = CACHE.computeIfAbsent(
            message.getClass(), MessageValidator::scanFields
        );

        final List<Violation> violations = new ArrayList<>();

        for (final FieldConstraints fc : fieldConstraints) {
            final Object value;
            try {
                value = fc.field().get(message);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException("Failed to access field: " + fc.field().getName(), e);
            }

            for (final AnnotatedConstraint ac : fc.constraints()) {
                final Violation violation = ac.validator().validate(fc.field().getName(), value, ac.annotation());
                if (violation != null)
                    violations.add(violation);
            }
        }

        return violations;
    }

    /**
     * Validates the given message and throws {@link MessageValidationRavenException} if any violations are found.
     *
     * @param message the message to validate
     * @throws MessageValidationRavenException if validation fails
     */
    public static void validateOrThrow(final Message message) {
        final List<Violation> violations = validate(message);
        if (!violations.isEmpty())
            throw new MessageValidationRavenException(message.getClass(), violations);
    }

    private static List<FieldConstraints> scanFields(final Class<?> clazz) {
        final List<FieldConstraints> result = new ArrayList<>();

        Class<?> current = clazz;
        while (current != null && current != Message.class) {
            for (final Field field : current.getDeclaredFields()) {
                final List<AnnotatedConstraint> constraints = findConstraints(field);
                if (!constraints.isEmpty()) {
                    field.setAccessible(true);
                    result.add(new FieldConstraints(field, constraints));
                }
            }
            current = current.getSuperclass();
        }

        return result;
    }

    private static List<AnnotatedConstraint> findConstraints(final Field field) {
        final List<AnnotatedConstraint> constraints = new ArrayList<>();

        for (final ConstraintValidator validator : VALIDATORS) {
            final Annotation annotation = field.getAnnotation(validator.getAnnotationType());
            if (annotation != null)
                constraints.add(new AnnotatedConstraint(validator, annotation));
        }

        return constraints;
    }

    private record FieldConstraints(Field field, List<AnnotatedConstraint> constraints) {
    }

    private record AnnotatedConstraint(ConstraintValidator validator, Annotation annotation) {
    }
}
