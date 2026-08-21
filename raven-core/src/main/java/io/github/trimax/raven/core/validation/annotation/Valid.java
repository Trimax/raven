package io.github.trimax.raven.core.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for recursive validation.
 * When present, the nested object's fields are validated using the same constraint annotations.
 * If the field value is null, nested validation is skipped (use {@link NotNull} to enforce non-null).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Valid {
}
