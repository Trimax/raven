package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Email;

class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @Email
    @SuppressWarnings("unused")
    private static String emailField;

    @Test
    void validSimpleEmail() {
        final var result = validator.validate("email", "user@example.com", getAnnotation());
        assertNull(result);
    }

    @Test
    void validEmailWithDots() {
        final var result = validator.validate("email", "first.last@domain.org", getAnnotation());
        assertNull(result);
    }

    @Test
    void validEmailWithPlus() {
        final var result = validator.validate("email", "user+tag@example.com", getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidEmailNoAtSign() {
        final var result = validator.validate("email", "userexample.com", getAnnotation());
        assertNotNull(result);
        assertEquals("Email", result.constraint());
        assertEquals("email", result.fieldName());
    }

    @Test
    void invalidEmailNoDomain() {
        final var result = validator.validate("email", "user@", getAnnotation());
        assertNotNull(result);
        assertEquals("Email", result.constraint());
    }

    @Test
    void invalidEmailNoTld() {
        final var result = validator.validate("email", "user@domain", getAnnotation());
        assertNotNull(result);
        assertEquals("Email", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("email", null, getAnnotation()));
    }

    private Email getAnnotation() {
        try {
            return EmailValidatorTest.class.getDeclaredField("emailField").getAnnotation(Email.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
