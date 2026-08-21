package io.github.trimax.raven.server;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ServerMessageRouter} handler signature validation.
 */
class ServerMessageRouterValidationTest {

    private ServerMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new ServerMessageRouter();
    }

    @Test
    void validMessageHandler() {
        assertDoesNotThrow(() ->
                router.postProcessAfterInitialization(new ValidMessageHandler(), "valid"));
    }

    @Test
    void validConnectHandler() {
        assertDoesNotThrow(() ->
                router.postProcessAfterInitialization(new ValidConnectHandler(), "valid"));
    }

    @Test
    void validDisconnectHandler() {
        assertDoesNotThrow(() ->
                router.postProcessAfterInitialization(new ValidDisconnectHandler(), "valid"));
    }

    @Test
    void messageHandlerWrongParamCount() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new MessageHandlerOneParam(), "bad"));
        assertTrue(ex.getMessage().contains("expected 2 parameters"));
    }

    @Test
    void messageHandlerFirstParamNotClient() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new MessageHandlerWrongFirstParam(), "bad"));
        assertTrue(ex.getMessage().contains("first parameter must be Client"));
    }

    @Test
    void messageHandlerTypeMismatch() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new MessageHandlerTypeMismatch(), "bad"));
        assertTrue(ex.getMessage().contains("does not match parameter type"));
    }

    @Test
    void connectHandlerWrongParamCount() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new ConnectHandlerNoParams(), "bad"));
        assertTrue(ex.getMessage().contains("expected 1 parameter"));
    }

    @Test
    void connectHandlerParamNotClient() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new ConnectHandlerWrongParam(), "bad"));
        assertTrue(ex.getMessage().contains("parameter must be Client"));
    }

    @Test
    void disconnectHandlerWrongParamCount() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new DisconnectHandlerTwoParams(), "bad"));
        assertTrue(ex.getMessage().contains("expected 1 parameter"));
    }

    // --- Test messages ---

    static class TestMsg extends Message {}
    static class OtherMsg extends Message {}

    // --- Valid handlers ---

    static class ValidMessageHandler {
        @SubscribeMessage(TestMsg.class)
        public void handle(final Client sender, final TestMsg msg) {}
    }

    static class ValidConnectHandler {
        @SubscribeConnect
        public void handle(final Client client) {}
    }

    static class ValidDisconnectHandler {
        @SubscribeDisconnect
        public void handle(final Client client) {}
    }

    // --- Invalid handlers ---

    static class MessageHandlerOneParam {
        @SubscribeMessage(TestMsg.class)
        public void handle(final TestMsg msg) {}
    }

    static class MessageHandlerWrongFirstParam {
        @SubscribeMessage(TestMsg.class)
        public void handle(final String notClient, final TestMsg msg) {}
    }

    static class MessageHandlerTypeMismatch {
        @SubscribeMessage(TestMsg.class)
        public void handle(final Client sender, final OtherMsg msg) {}
    }

    static class ConnectHandlerNoParams {
        @SubscribeConnect
        public void handle() {}
    }

    static class ConnectHandlerWrongParam {
        @SubscribeConnect
        public void handle(final String notClient) {}
    }

    static class DisconnectHandlerTwoParams {
        @SubscribeDisconnect
        public void handle(final Client client, final String extra) {}
    }
}
