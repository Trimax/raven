package io.github.trimax.raven.client;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClientMessageRouter} handler signature validation.
 */
class ClientMessageRouterValidationTest {

    private ClientMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new ClientMessageRouter();
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
                router.postProcessAfterInitialization(new MessageHandlerTwoParams(), "bad"));
        assertTrue(ex.getMessage().contains("expected 1 parameter"));
    }

    @Test
    void messageHandlerTypeMismatch() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new MessageHandlerTypeMismatch(), "bad"));
        assertTrue(ex.getMessage().contains("does not match parameter type"));
    }

    @Test
    void connectHandlerWithParams() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new ConnectHandlerWithParam(), "bad"));
        assertTrue(ex.getMessage().contains("expected 0 parameters"));
    }

    @Test
    void disconnectHandlerWithParams() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new DisconnectHandlerWithParam(), "bad"));
        assertTrue(ex.getMessage().contains("expected 0 parameters"));
    }

    // --- Test messages ---

    static class TestMsg extends Message {}
    static class OtherMsg extends Message {}

    // --- Valid handlers ---

    static class ValidMessageHandler {
        @SubscribeMessage(TestMsg.class)
        public void handle(final TestMsg msg) {}
    }

    static class ValidConnectHandler {
        @SubscribeConnect
        public void handle() {}
    }

    static class ValidDisconnectHandler {
        @SubscribeDisconnect
        public void handle() {}
    }

    // --- Invalid handlers ---

    static class MessageHandlerTwoParams {
        @SubscribeMessage(TestMsg.class)
        public void handle(final Client sender, final TestMsg msg) {}
    }

    static class MessageHandlerTypeMismatch {
        @SubscribeMessage(TestMsg.class)
        public void handle(final OtherMsg msg) {}
    }

    static class ConnectHandlerWithParam {
        @SubscribeConnect
        public void handle(final Client client) {}
    }

    static class DisconnectHandlerWithParam {
        @SubscribeDisconnect
        public void handle(final Client client) {}
    }
}
