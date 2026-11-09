package io.github.trimax.raven.core.internal.session;

import io.github.trimax.raven.core.Message;

/**
 * A session listener that accepts everything and reports nothing, for the far end of a test connection.
 */
public final class SilentListener implements SessionListener {

    @Override
    public boolean onOpen(final Session session) {
        return true;
    }

    @Override
    public boolean onMessage(final Session session, final Message message) {
        return true;
    }

    @Override
    public void onClose(final Session session) {
    }
}
