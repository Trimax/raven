package io.github.trimax.raven.core.internal.codec;

import java.io.InvalidClassException;
import java.io.Serial;
import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A message that broke the deserialization rules: which class or limit it was, and why, with a hint how to fix it
 * where the receiving side can.
 *
 * <p>Internal: not part of the API.
 */
@Getter
@Accessors(fluent = true)
public final class RejectedMessageException extends InvalidClassException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What was rejected, e.g. {@code class com.example.Unit in a message}. */
    private final String subject;

    /** Why it was rejected. */
    private final String reason;

    private RejectedMessageException(final String subject, final String reason) {
        super("Rejected " + subject + ": " + reason);
        this.subject = subject;
        this.reason = reason;
    }

    static RejectedMessageException disallowedClass(final Class<?> type) {
        final var subject = "class " + type.getTypeName() + " in a message";
        final var component = AllowedTypes.component(type);
        // Trusting a JDK package would admit far more than the one class, so point to the allowed types instead
        final var loader = component.getClassLoader();
        if (loader == null || loader == ClassLoader.getPlatformClassLoader())
            return new RejectedMessageException(subject, "not one of the allowed JDK types, e.g. send an ArrayList or "
                    + "List.of(...) instead of Arrays.asList(...) or Collections.unmodifiableList(...)");

        final var packageName = component.getPackageName();
        final var example = packageName.isEmpty() ? "com.example" : packageName;
        return new RejectedMessageException(subject,
                "not a Message, record, enum or allowed JDK type. If the class is yours, trust its package: "
                        + "raven.server.trusted-packages / raven.client.trusted-packages=" + example
                        + " or new JavaSerializationCodec(Set.of(\"" + example + "\"))");
    }

    static RejectedMessageException unknownClass(final String className) {
        return new RejectedMessageException("class " + className + " in a message",
                "no such class on this side, e.g. a message type only the peer has");
    }

    static RejectedMessageException disallowedProxy(final Class<?> proxy) {
        final var interfaces = Arrays.stream(AllowedTypes.component(proxy).getInterfaces())
                .map(Class::getName)
                .collect(Collectors.joining(", "));
        return new RejectedMessageException("a dynamic proxy of " + interfaces + " in a message",
                "proxies are only allowed for interfaces of trusted packages");
    }

    static RejectedMessageException exceededLimit(final String limit) {
        return new RejectedMessageException("a message", "it exceeds the limit of " + limit + " per message");
    }
}
