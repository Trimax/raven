package io.github.trimax.raven.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import io.github.trimax.raven.core.filter.MessageInputFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the incoming messages of one {@link Connection}. The peer is untrusted, so only {@link Message} subclasses
 * and a fixed set of safe JDK value types may be deserialized.
 */
@Slf4j
final class MessageReader {

    private final ObjectInputStream inputStream;

    /**
     * Kept as a field because {@link #read()} calls {@link MessageInputFilter#startMessage()} before every
     * {@code readObject}, restarting the per-message limits. Its limits are tracked per message, so each stream
     * needs its own filter.
     */
    private final MessageInputFilter filter = new MessageInputFilter();

    /**
     * Wraps the stream in an {@link ObjectInputStream}, which blocks until the peer's stream header arrives.
     *
     * @param input the raw input stream of the connection
     * @throws IOException if the peer's stream header cannot be read
     */
    MessageReader(final InputStream input) throws IOException {
        this.inputStream = new ObjectInputStream(input);
        this.inputStream.setObjectInputFilter(filter);
    }

    /**
     * Reads the next message. Blocks until one arrives.
     *
     * @return the message, or {@code null} if the peer sent an object that is not a {@link Message}
     * @throws IOException            if the connection fails or the peer sent a class the filter rejects
     * @throws ClassNotFoundException if the peer sent a class that is not on the classpath
     */
    Message read() throws IOException, ClassNotFoundException {
        filter.startMessage();
        final var object = inputStream.readObject();
        if (object instanceof Message message)
            return message;

        log.warn("Received an object that is not a Message: {}", object.getClass().getName());
        return null;
    }
}
