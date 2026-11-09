package io.github.trimax.raven.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import io.github.trimax.raven.core.type.DataSize;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Autoconfiguration for the Raven client.
 * Creates a {@link RavenClient} bean configured from properties.
 * Connection is established after all beans are initialized to ensure handlers are registered.
 *
 * <p>Required properties: {@code raven.client.host}, {@code raven.client.port}.
 * Optional: {@code raven.client.connect-timeout} and {@code raven.client.send-timeout}, e.g. {@code 5s} or {@code PT5S},
 * {@code raven.client.send-queue-capacity}, {@code raven.client.max-message-size} (e.g. {@code 16MB}), and
 * {@code raven.client.idle-timeout}, after which a connection on which the server sent nothing is closed; there is no
 * heartbeat, so this also closes a healthy but silent connection (all default to {@link RavenClientConfiguration}'s
 * values; the idle timeout is off by default). A {@link MessageCodec} bean, if present, replaces the default Java
 * serialization.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.github.trimax.raven.client")
@RequiredArgsConstructor
public final class RavenClientAutoConfiguration implements SmartInitializingSingleton {

    @Value("${raven.client.host}")
    private final String host;

    @Value("${raven.client.port}")
    private final int port;

    /**
     * Parsed here rather than injected as a {@link java.time.Duration}: plain Spring, unlike Spring Boot,
     * has no String-to-Duration conversion for {@code @Value}.
     */
    @Value("${raven.client.connect-timeout:#{null}}")
    private final String connectTimeout;

    @Value("${raven.client.send-queue-capacity:#{null}}")
    private final Integer sendQueueCapacity;

    @Value("${raven.client.send-timeout:#{null}}")
    private final String sendTimeout;

    @Value("${raven.client.idle-timeout:#{null}}")
    private final String idleTimeout;

    @Value("${raven.client.max-message-size:#{null}}")
    private final String maxMessageSize;

    /** Whether the default codec deserializes records; ignored when a {@link MessageCodec} bean is present. */
    @Value("${raven.client.allow-records:true}")
    private final boolean allowRecords;

    private RavenClient client;

    @Bean
    public RavenClient ravenClient(final ClientMessageRouter router,
                                   final ObjectProvider<ClientMessageInterceptor> interceptors,
                                   final ObjectProvider<MessageCodec> codec) {
        final var builder = RavenClientConfiguration.builder()
                .host(host)
                .port(port)
                .handler(router)
                .interceptors(interceptors.orderedStream().toList());

        // Unset keeps the core default instead of duplicating it here
        if (connectTimeout != null)
            builder.connectTimeout(DurationFormatterUtils.detectAndParse(connectTimeout));
        if (sendQueueCapacity != null)
            builder.sendQueueCapacity(sendQueueCapacity);
        if (sendTimeout != null)
            builder.sendTimeout(DurationFormatterUtils.detectAndParse(sendTimeout));
        if (idleTimeout != null)
            builder.idleTimeout(DurationFormatterUtils.detectAndParse(idleTimeout));
        if (maxMessageSize != null)
            builder.maxMessageSize(DataSize.parse(maxMessageSize));

        // A MessageCodec bean replaces the default Java serialization
        final var customCodec = codec.getIfAvailable();
        if (customCodec != null)
            builder.codec(customCodec);
        else if (!allowRecords)
            builder.codec(JavaSerializationCodec.withoutRecords());

        client = new RavenClient(builder.build());
        return client;
    }

    @Override
    public void afterSingletonsInstantiated() {
        client.connect();
    }

    @PreDestroy
    void shutdown() {
        if (client != null)
            client.disconnect();
    }
}
