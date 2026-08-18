package io.github.trimax.raven.client;

import io.github.trimax.raven.core.Message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a client-side handler for a specific {@link Message} type.
 *
 * <p>Expected signature: {@code void method(T message)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscribeMessage {

    Class<? extends Message> value();
}
