package io.github.trimax.raven.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;

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
    void messageHandlerOnAbstractType() {
        assertDoesNotThrow(() ->
                router.postProcessAfterInitialization(new AbstractTypeHandler(), "valid"));
    }

    @Test
    void messageHandlerWithSupertypeParameter() {
        assertDoesNotThrow(() ->
                router.postProcessAfterInitialization(new SupertypeParamHandler(), "valid"));
    }

    @Test
    void messageHandlerWithSubtypeParameterRejected() {
        final var ex = assertThrows(IllegalStateException.class, () ->
                router.postProcessAfterInitialization(new SubtypeParamHandler(), "bad"));
        assertTrue(ex.getMessage().contains("does not match parameter type"));
        assertTrue(ex.getMessage().contains("parameter must be BaseMsg or its supertype"));
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
    abstract static class BaseMsg extends Message {}
    static class DerivedMsg extends BaseMsg {}

    // --- Valid handlers ---

    static class ValidMessageHandler {
        @SubscribeMessage(TestMsg.class)
        public void handle(final Client sender, final TestMsg msg) {}
    }

    static class AbstractTypeHandler {
        @SubscribeMessage(BaseMsg.class)
        public void handle(final Client sender, final BaseMsg msg) {}
    }

    static class SupertypeParamHandler {
        @SubscribeMessage(BaseMsg.class)
        public void handle(final Client sender, final Message msg) {}
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

    static class SubtypeParamHandler {
        @SubscribeMessage(BaseMsg.class)
        public void handle(final Client sender, final DerivedMsg msg) {}
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
