package io.github.trimax.raven.spring;

import io.github.trimax.raven.Message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for a specific {@link Message} type.
 *
 * <p>Server-side signature: {@code void method(Client sender, T message)}
 * <p>Client-side signature: {@code void method(T message)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscribeMessage {

    Class<? extends Message> value();
}
