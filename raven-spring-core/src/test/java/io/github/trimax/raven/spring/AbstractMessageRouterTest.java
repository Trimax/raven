package io.github.trimax.raven.spring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import lombok.experimental.SuperBuilder;

/**
 * Unit tests for hierarchy-based dispatch and handler caching in {@link AbstractMessageRouter}.
 */
class AbstractMessageRouterTest {

    private static final List<String> CALLS = new CopyOnWriteArrayList<>();

    private TestRouter router;

    @BeforeEach
    void setUp() {
        CALLS.clear();
        router = new TestRouter();
    }

    @Test
    void abstractBaseHandlerReceivesSubclassMessages() {
        register(new BaseHandler());

        dispatch(CreateRequest.builder().build());
        dispatch(DeleteRequest.builder().build());

        assertEquals(List.of("base:CreateRequest", "base:DeleteRequest"), CALLS);
    }

    @Test
    void handlersInvokedFromMostSpecificToMessage() {
        // Registration order is deliberately reversed relative to the expected call order
        register(new CatchAllHandler());
        register(new BaseHandler());
        register(new CreateHandler());

        dispatch(CreateRequest.builder().build());

        assertEquals(List.of("create:CreateRequest", "base:CreateRequest", "all:CreateRequest"), CALLS);
    }

    @Test
    void multiLevelHierarchyIsWalkedInOrder() {
        register(new CatchAllHandler());
        register(new BaseHandler());
        register(new CreateHandler());
        register(new BulkCreateHandler());

        dispatch(BulkCreateRequest.builder().build());

        assertEquals(List.of("bulk:BulkCreateRequest", "create:BulkCreateRequest",
                "base:BulkCreateRequest", "all:BulkCreateRequest"), CALLS);
    }

    @Test
    void catchAllStillReceivesUnrelatedMessages() {
        register(new CatchAllHandler());
        register(new BaseHandler());

        dispatch(PlainMessage.builder().build());

        assertEquals(List.of("all:PlainMessage"), CALLS);
    }

    @Test
    void subclassHandlerDoesNotReceiveSiblingMessages() {
        register(new CreateHandler());

        dispatch(DeleteRequest.builder().build());

        assertTrue(CALLS.isEmpty());
    }

    @Test
    void sameOrderWithinLevelAsRegistration() {
        register(new BaseHandler());
        register(new SecondBaseHandler());

        dispatch(DeleteRequest.builder().build());

        assertEquals(List.of("base:DeleteRequest", "base2:DeleteRequest"), CALLS);
    }

    @Test
    void sameBeanRegisteredTwiceIsInvokedOnce() {
        final var handler = new BaseHandler();
        register(handler);
        register(handler);

        dispatch(DeleteRequest.builder().build());

        assertEquals(List.of("base:DeleteRequest"), CALLS);
    }

    @Test
    void bridgeMethodsAreNotRegistered() {
        register(new GenericInterfaceHandler());

        dispatch(CreateRequest.builder().build());

        assertEquals(List.of("generic:CreateRequest"), CALLS);
    }

    @Test
    void resolvedHandlersAreCachedPerMessageClass() {
        register(new BaseHandler());
        register(new CreateHandler());

        final var first = router.handlersFor(CreateRequest.class);
        dispatch(CreateRequest.builder().build());
        dispatch(CreateRequest.builder().build());

        assertSame(first, router.handlersFor(CreateRequest.class));
        assertEquals(2, first.size());
    }

    @Test
    void lateRegistrationInvalidatesCache() {
        register(new BaseHandler());
        final var before = router.handlersFor(CreateRequest.class);
        dispatch(CreateRequest.builder().build());

        register(new CreateHandler());
        final var after = router.handlersFor(CreateRequest.class);
        dispatch(CreateRequest.builder().build());

        assertNotSame(before, after);
        assertEquals(1, before.size());
        assertEquals(2, after.size());
        assertEquals(List.of("base:CreateRequest", "create:CreateRequest", "base:CreateRequest"), CALLS);
    }

    @Test
    void startupLoggingWithAbstractTypeDoesNotFail() {
        register(new BaseHandler());
        register(new CatchAllHandler());

        assertDoesNotThrow(router::afterSingletonsInstantiated);
    }

    private void register(final Object bean) {
        router.postProcessAfterInitialization(bean, bean.getClass().getSimpleName());
    }

    private void dispatch(final Message message) {
        router.onMessage(message);
    }

    // --- Annotations ---

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface OnMessage {
        Class<? extends Message> value();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface OnConnect {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface OnDisconnect {
    }

    // --- Router ---

    static final class TestRouter extends AbstractMessageRouter {

        @Override
        protected Class<? extends Annotation> messageAnnotation() {
            return OnMessage.class;
        }

        @Override
        protected Class<? extends Annotation> connectAnnotation() {
            return OnConnect.class;
        }

        @Override
        protected Class<? extends Annotation> disconnectAnnotation() {
            return OnDisconnect.class;
        }

        @Override
        protected Class<? extends Message> getMessageType(final Annotation annotation) {
            return ((OnMessage) annotation).value();
        }

        @Override
        protected void validateMessageHandler(final Method method,
                                              final Class<?> beanClass,
                                              final Class<? extends Message> messageType) {
        }

        @Override
        protected void validateLifecycleHandler(final Method method,
                                                final Class<?> beanClass,
                                                final String annotationName) {
        }

        void onMessage(final Message message) {
            invokeMessageHandlers(message, handler -> handler.invoke(message));
        }
    }

    // --- Messages ---

    @SuperBuilder(toBuilder = true)
    abstract static class BaseRequest extends Message {
    }

    @SuperBuilder(toBuilder = true)
    static class CreateRequest extends BaseRequest {
    }

    @SuperBuilder(toBuilder = true)
    static final class BulkCreateRequest extends CreateRequest {
    }

    @SuperBuilder(toBuilder = true)
    static final class DeleteRequest extends BaseRequest {
    }

    @SuperBuilder(toBuilder = true)
    static final class PlainMessage extends Message {
    }

    // --- Handlers ---

    private static void record(final String handler, final Message message) {
        CALLS.add(handler + ":" + message.getClass().getSimpleName());
    }

    static final class CatchAllHandler {
        @OnMessage(Message.class)
        void onAny(final Message message) {
            record("all", message);
        }
    }

    static final class BaseHandler {
        @OnMessage(BaseRequest.class)
        void onBase(final BaseRequest message) {
            record("base", message);
        }
    }

    static final class SecondBaseHandler {
        @OnMessage(BaseRequest.class)
        void onBase(final BaseRequest message) {
            record("base2", message);
        }
    }

    static final class CreateHandler {
        @OnMessage(CreateRequest.class)
        void onCreate(final CreateRequest message) {
            record("create", message);
        }
    }

    static final class BulkCreateHandler {
        @OnMessage(BulkCreateRequest.class)
        void onBulkCreate(final BulkCreateRequest message) {
            record("bulk", message);
        }
    }

    interface TypedHandler<T extends Message> {
        void handle(T message);
    }

    static final class GenericInterfaceHandler implements TypedHandler<CreateRequest> {
        @Override
        @OnMessage(CreateRequest.class)
        public void handle(final CreateRequest message) {
            record("generic", message);
        }
    }
}
