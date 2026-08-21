package io.github.trimax.raven.core.validation.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the annotated decimal field value is within the specified range (inclusive).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DecimalRange {

    double min() default -Double.MAX_VALUE;

    double max() default Double.MAX_VALUE;
}
