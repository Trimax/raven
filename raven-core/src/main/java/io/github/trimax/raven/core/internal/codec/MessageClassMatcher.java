package io.github.trimax.raven.core.internal.codec;

import org.apache.commons.io.serialization.ClassNameMatcher;

import lombok.RequiredArgsConstructor;

/**
 * The class name check of a {@code ValidatingObjectInputStream}: accepts a class if the {@link MessageTypePolicy}
 * allows it. Called for every class descriptor of a message, arrays included ({@code [Lcom.example.Unit;},
 * {@code [I}), and for the interfaces of a dynamic proxy, before the stream resolves the class.
 */
@RequiredArgsConstructor
final class MessageClassMatcher implements ClassNameMatcher {

    /**
     * The loader the stream resolves classes with: {@code ObjectInputStream} uses the nearest class loader on the
     * call stack, which here is that of commons-io and Raven, normally the application's. Should the two ever
     * differ, the {@link MessageInputFilter} still checks the class the stream actually resolved.
     */
    private static final ClassLoader LOADER = MessageClassMatcher.class.getClassLoader();

    private final MessageTypePolicy policy;
    private final Rejection rejection;

    @Override
    public boolean matches(final String className) {
        final Class<?> type;
        try {
            // Without initialization, as ObjectInputStream loads classes: a rejected class never runs its static initializer
            type = Class.forName(className, false, LOADER);
        } catch (final ClassNotFoundException | LinkageError ex) {
            rejection.record(RejectedMessageException.unknownClass(className));
            return false;
        }

        if (policy.isAllowed(type))
            return true;

        rejection.record(RejectedMessageException.disallowedClass(type));
        return false;
    }
}
