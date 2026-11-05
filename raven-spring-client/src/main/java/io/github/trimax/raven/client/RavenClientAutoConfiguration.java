package io.github.trimax.raven.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autoconfiguration for the Raven client.
 * Creates a {@link RavenClient} bean configured from properties.
 * Connection is established after all beans are initialized to ensure handlers are registered.
 *
 * <p>Required properties: {@code raven.client.host}, {@code raven.client.port}.
 * Optional: {@code raven.client.connect-timeout} and {@code raven.client.send-timeout}, e.g. {@code 5s} or {@code PT5S},
 * and {@code raven.client.send-queue-capacity} (all default to {@link RavenClientConfiguration}'s values).
 */
@Slf4j
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

    private RavenClient client;

    @Bean
    public RavenClient ravenClient(final ClientMessageRouter router,
                                   final ObjectProvider<ClientMessageInterceptor> interceptors) {
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
