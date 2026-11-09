package io.github.trimax.raven.core;

/**
 * Receives the life cycle of a {@link Session}. Every method is called on the session's own thread, in order:
 * {@link #onOpen}, then {@link #onMessage} per received message, then {@link #onClose} if {@code onOpen} returned
 * {@code true}.
 */
interface SessionListener {

    /**
     * Called once the hello exchange has succeeded.
     *
     * @return {@code false} to close the session; {@link #onClose} is then not called
     */
    boolean onOpen(Session session);

    /**
     * Called for each received message.
     *
     * @return {@code false} to close the session
     */
    boolean onMessage(Session session, Message message);

    /**
     * Called once after the session has closed, if {@link #onOpen} returned {@code true}.
     */
    void onClose(Session session);
}
