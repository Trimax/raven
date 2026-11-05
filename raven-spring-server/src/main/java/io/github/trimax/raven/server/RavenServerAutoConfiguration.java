package io.github.trimax.raven.server;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autoconfiguration for the Raven server.
 * Creates a {@link RavenServer} bean configured from properties.
 * Server is started after all beans are initialized to ensure handlers are registered.
 *
 * <p>Required property: {@code raven.server.port}.
 * Optional: {@code raven.server.send-queue-capacity} and {@code raven.server.send-timeout}, e.g. {@code 5s} or {@code PT5S}
 * (both default to {@link RavenServerConfiguration}'s values).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.github.trimax.raven.server")
@RequiredArgsConstructor
public final class RavenServerAutoConfiguration implements SmartInitializingSingleton {

    @Value("${raven.server.port}")
    private final int port;

    @Value("${raven.server.send-queue-capacity:#{null}}")
    private final Integer sendQueueCapacity;

    /**
     * Parsed here rather than injected as a {@link java.time.Duration}: plain Spring, unlike Spring Boot,
     * has no String-to-Duration conversion for {@code @Value}.
     */
    @Value("${raven.server.send-timeout:#{null}}")
    private final String sendTimeout;

    private RavenServer server;

    @Bean
    public RavenServer ravenServer(final ServerMessageRouter router,
                                   final ObjectProvider<ServerMessageInterceptor> interceptors) {
        final var builder = RavenServerConfiguration.builder()
                .port(port)
                .handler(router)
                .interceptors(interceptors.orderedStream().toList());
        // Unset keeps the core default instead of duplicating it here
        if (sendQueueCapacity != null)
            builder.sendQueueCapacity(sendQueueCapacity);
        if (sendTimeout != null)
            builder.sendTimeout(DurationFormatterUtils.detectAndParse(sendTimeout));

        server = new RavenServer(builder.build());
        return server;
    }

    @Override
    public void afterSingletonsInstantiated() {
        server.start();
    }

    @PreDestroy
    void shutdown() {
        if (server != null)
            server.stop();
    }
}
