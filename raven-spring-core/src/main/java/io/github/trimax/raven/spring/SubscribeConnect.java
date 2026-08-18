package io.github.trimax.raven.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for connection events.
 *
 * <p>Server-side signature: {@code void method(Client client)}
 * <p>Client-side signature: {@code void method()}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscribeConnect {
}
