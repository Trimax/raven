package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.DecimalMax;

class DecimalMaxValidatorTest {

    private final DecimalMaxValidator validator = new DecimalMaxValidator();

    @DecimalMax(99.9)
    @SuppressWarnings("unused")
    private static double decimalMaxField;

    @Test
    void validValueBelowMaximum() {
        final var result = validator.validate("rate", 50.0, getAnnotation());
        assertNull(result);
    }

    @Test
    void validValueAtMaximum() {
        final var result = validator.validate("rate", 99.9, getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidValueAboveMaximum() {
        final var result = validator.validate("rate", 100.0, getAnnotation());
        assertNotNull(result);
        assertEquals("DecimalMax", result.constraint());
        assertEquals("rate", result.fieldName());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("rate", null, getAnnotation()));
    }

    private DecimalMax getAnnotation() {
        try {
            return DecimalMaxValidatorTest.class.getDeclaredField("decimalMaxField").getAnnotation(DecimalMax.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
