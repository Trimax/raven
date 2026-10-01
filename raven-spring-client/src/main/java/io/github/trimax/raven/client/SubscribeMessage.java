package io.github.trimax.raven.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.trimax.raven.core.Message;

/**
 * Marks a method as a client-side handler for a specific {@link Message} type.
 *
 * <p>Expected signature: {@code void method(T message)}, where {@code T} is the annotated type
 * or one of its supertypes.
 *
 * <p>The annotated type may be abstract. The handler receives messages of the annotated type and of
 * all its subclasses. For a given message, handlers are invoked from the most specific registered
 * type up the superclass chain, ending with {@link Message}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscribeMessage {

    Class<? extends Message> value();
}
