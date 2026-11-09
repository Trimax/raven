package io.github.trimax.raven.core.codec;

import java.io.IOException;

import io.github.trimax.raven.core.Message;

/**
 * Turns a message into the payload of one frame and back. Implementations must be stateless and thread-safe:
 * one instance serves every connection of a server or client, and {@link #encode(Message)} runs on the threads
 * that call {@code send()}.
 */
public interface MessageCodec {

    /**
     * Serializes one message.
     *
     * @param message the message to send
     * @return the frame payload
     * @throws IOException if the message cannot be serialized
     */
    byte[] encode(Message message) throws IOException;

    /**
     * Deserializes the payload of one frame received from an untrusted peer.
     *
     * @param frame the frame payload
     * @return the message, or {@code null} if the payload holds an allowed object that is not a {@link Message}
     * @throws IOException if the payload is corrupt or holds a rejected class; the connection is then closed
     */
    Message decode(byte[] frame) throws IOException;
}
