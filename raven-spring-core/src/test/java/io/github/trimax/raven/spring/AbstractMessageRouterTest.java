package io.github.trimax.raven.spring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.github.trimax.raven.core.Message;
import lombok.experimental.SuperBuilder;

/**
 * Unit tests for hierarchy-based dispatch and handler caching in {@link AbstractMessageRouter}.
 */
class AbstractMessageRouterTest {

    private static final List<String> CALLS = new CopyOnWriteArrayList<>();

    private final Logger routerLogger = (Logger) LoggerFactory.getLogger(AbstractMessageRouter.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    private TestRouter router;

    @BeforeEach
    void setUp() {
        CALLS.clear();
        router = new TestRouter();
        logAppender.start();
        routerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        routerLogger.detachAppender(logAppender);
        logAppender.stop();
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
        assertEquals(List.of("BaseRequest -> 1 handler(s)"), startupSummary());
    }

    @Test
    void sameLifecycleBeanRegisteredTwiceIsInvokedOnce() {
        final var handler = new ConnectHandler();
        register(handler);
        register(handler);

        router.onConnect();

        assertEquals(List.of("connect"), CALLS);
    }

    @Test
    void distinctBeansOfSameClassAreBothInvoked() {
        register(new BaseHandler());
        register(new BaseHandler());

        dispatch(DeleteRequest.builder().build());

        assertEquals(List.of("base:DeleteRequest", "base:DeleteRequest"), CALLS);
    }

    @Test
    void summaryUsesSimpleNamesWhenUnique() {
        register(new CatchAllHandler());
        register(new BaseHandler());
        register(new SecondBaseHandler());
        register(new CreateHandler());

        assertEquals(List.of(
                "BaseRequest -> 2 handler(s)",
                "CreateRequest -> 1 handler(s)",
                "Message -> 1 handler(s)"), startupSummary());
    }

    @Test
    void summaryUsesQualifiedNamesForCollidingSimpleNames() {
        register(new FirstPingHandler());
        register(new SecondPingHandler());
        register(new CreateHandler());

        assertEquals(List.of(
                "CreateRequest -> 1 handler(s)",
                FirstScope.Ping.class.getName() + " -> 1 handler(s)",
                SecondScope.Ping.class.getName() + " -> 1 handler(s)"), startupSummary());
    }

    @Test
    void bridgeMethodsAreNotRegistered() {
        register(new GenericInterfaceHandler());

        dispatch(CreateRequest.builder().build());

        assertEquals(List.of("generic:CreateRequest"), CALLS);
    }

    @Test
    void resolvedHandlersAreCachedPerMessageClass() throws ReflectiveOperationException {
        register(new BaseHandler());
        register(new CreateHandler());

        dispatch(CreateRequest.builder().build());
        final var first = resolvedHandlersCache().get(CreateRequest.class);
        dispatch(CreateRequest.builder().build());

        // The second dispatch reuses the list resolved by the first one instead of walking the hierarchy again
        assertNotNull(first);
        assertSame(first, resolvedHandlersCache().get(CreateRequest.class));
        assertEquals(List.of("create:CreateRequest", "base:CreateRequest",
                "create:CreateRequest", "base:CreateRequest"), CALLS);
    }

    @Test
    void lateRegistrationInvalidatesCache() {
        register(new BaseHandler());
        dispatch(CreateRequest.builder().build());

        register(new CreateHandler());
        dispatch(CreateRequest.builder().build());

        assertEquals(List.of("base:CreateRequest", "create:CreateRequest", "base:CreateRequest"), CALLS);
    }

    private void register(final Object bean) {
        router.postProcessAfterInitialization(bean, bean.getClass().getSimpleName());
    }

    private void dispatch(final Message message) {
        router.onMessage(message);
    }

    /**
     * Runs the startup hook and returns the logged {@code "Type -> N handler(s)"} lines.
     */
    private List<String> startupSummary() {
        router.afterSingletonsInstantiated();

        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains(" -> "))
                .map(String::strip)
                .toList();
    }

    /**
     * Reads the router's private cache. Caching has no observable effect through the router API,
     * so the only way to verify it without widening visibility is to inspect the field.
     */
    @SuppressWarnings("unchecked")
    private Map<Class<?>, List<?>> resolvedHandlersCache() throws ReflectiveOperationException {
        final var field = AbstractMessageRouter.class.getDeclaredField("resolvedHandlers");
        field.setAccessible(true);
        return (Map<Class<?>, List<?>>) field.get(router);
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

        void onConnect() {
            invokeConnectHandlers(HandlerMethod::invoke);
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

    static final class FirstScope {
        static final class Ping extends Message {
        }
    }

    static final class SecondScope {
        static final class Ping extends Message {
        }
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

    static final class ConnectHandler {
        @OnConnect
        void onConnect() {
            CALLS.add("connect");
        }
    }

    static final class FirstPingHandler {
        @OnMessage(FirstScope.Ping.class)
        void onPing(final FirstScope.Ping message) {
            record("ping1", message);
        }
    }

    static final class SecondPingHandler {
        @OnMessage(SecondScope.Ping.class)
        void onPing(final SecondScope.Ping message) {
            record("ping2", message);
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
