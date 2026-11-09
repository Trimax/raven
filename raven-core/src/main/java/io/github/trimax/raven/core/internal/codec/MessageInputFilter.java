package io.github.trimax.raven.core.internal.codec;

import java.io.ObjectInputFilter;
import java.lang.reflect.Proxy;

import lombok.RequiredArgsConstructor;

/**
 * Deserialization filter applied to every message read from the network, next to the {@link MessageClassMatcher}.
 *
 * <p>Structural limits protect against resource-exhaustion payloads. The class check repeats the matcher's rule on the
 * class the stream actually resolved: that covers dynamic proxy classes, which the matcher never sees (only their
 * interfaces), and a class loader that resolves a name differently from the matcher's.
 *
 * <p>One instance serves one message: every message is read from its own stream, so the stream's counters and
 * this filter's array budget are the totals of that message. Used only by the thread reading it.
 */
@RequiredArgsConstructor
final class MessageInputFilter implements ObjectInputFilter {

    /** Maximum nesting depth of one message's object graph. */
    static final long MAX_DEPTH = 64;

    /** Maximum number of object references in one message. */
    static final long MAX_REFERENCES = 100_000;

    /**
     * Maximum total number of array elements one message may declare, across all its arrays. Arrays are
     * allocated as soon as their length is read, before any element arrives, so this bounds the memory a peer
     * can make us allocate per message (a few megabytes) however the arrays are nested.
     */
    static final long MAX_ARRAY_ELEMENTS = 1 << 20;

    private final MessageTypePolicy policy;
    private final Rejection rejection;

    /** Array elements declared so far by the message. */
    private long arrayElements;

    @Override
    public Status checkInput(final FilterInfo info) {
        if (info.depth() > MAX_DEPTH)
            return reject(RejectedMessageException.exceededLimit(MAX_DEPTH + " levels of nesting"));
        if (info.references() > MAX_REFERENCES)
            return reject(RejectedMessageException.exceededLimit(MAX_REFERENCES + " object references"));

        if (info.arrayLength() > 0) {
            arrayElements += info.arrayLength();
            if (arrayElements > MAX_ARRAY_ELEMENTS)
                return reject(RejectedMessageException.exceededLimit(MAX_ARRAY_ELEMENTS + " array elements in total"));
        }

        final var type = info.serialClass();
        if (type == null)
            return Status.UNDECIDED;

        if (policy.isAllowed(type))
            return Status.ALLOWED;

        return reject(Proxy.isProxyClass(AllowedTypes.component(type))
                ? RejectedMessageException.disallowedProxy(type)
                : RejectedMessageException.disallowedClass(type));
    }

    private Status reject(final RejectedMessageException reason) {
        rejection.record(reason);
        return Status.REJECTED;
    }
}
