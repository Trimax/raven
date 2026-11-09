package io.github.trimax.raven.core.internal.codec;

import java.io.ObjectInputFilter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;

import org.apache.commons.io.serialization.ClassNameMatcher;

import lombok.NonNull;

/**
 * Which classes a message may contain: the {@link AllowedTypes} plus the classes of the {@link TrustedPackages}.
 *
 * <p>Every message is read with two checks that share this one rule: a {@link MessageClassMatcher} judges each class
 * by name before the stream resolves it, and a {@link MessageInputFilter} enforces the structural limits and judges
 * the class the stream actually resolved, including dynamic proxy classes, which have no name to match.
 *
 * <p>Internal: not part of the API.
 */
public final class MessageTypePolicy {

    private final TrustedPackages trustedPackages;

    /**
     * @param trustedPackages packages whose classes are allowed on top of the built-in types, or {@code "*"}
     * @throws IllegalArgumentException if an entry is not a package name
     */
    public MessageTypePolicy(@NonNull final Set<String> trustedPackages) {
        this.trustedPackages = new TrustedPackages(trustedPackages);
    }

    /**
     * The class name check of one message.
     */
    public ClassNameMatcher matcher(@NonNull final Rejection rejection) {
        return new MessageClassMatcher(this, rejection);
    }

    /**
     * The structural limits and resolved class check of one message; its array budget is that message's.
     */
    public ObjectInputFilter filter(@NonNull final Rejection rejection) {
        return new MessageInputFilter(this, rejection);
    }

    boolean isAllowed(final Class<?> type) {
        final var component = AllowedTypes.component(type);
        // A proxy forwards every call to its handler, which is checked on its own; what it can be cast to and
        // passed as are its interfaces, so it is only as trusted as those are
        if (Proxy.isProxyClass(component))
            return Arrays.stream(component.getInterfaces()).allMatch(this::isAllowed);

        return AllowedTypes.isAllowed(type) || trustedPackages.trusts(component.getPackageName());
    }
}
