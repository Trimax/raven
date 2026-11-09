package io.github.trimax.raven.core.internal.codec;

import java.io.InvalidClassException;

/**
 * Remembers why one message was rejected. The stream only reports that a class or the filter rejected something,
 * without saying which class or which limit, so the checks record it here for the log.
 *
 * <p>Internal: not part of the API.
 */
public final class Rejection {

    private RejectedMessageException first;

    void record(final RejectedMessageException rejection) {
        // The first rejection aborts the read; anything later is a consequence of it
        if (first == null)
            first = rejection;
    }

    /**
     * The recorded rejection, or the stream's own exception if the checks rejected nothing.
     */
    public InvalidClassException explain(final InvalidClassException thrown) {
        return first != null ? first : thrown;
    }
}
