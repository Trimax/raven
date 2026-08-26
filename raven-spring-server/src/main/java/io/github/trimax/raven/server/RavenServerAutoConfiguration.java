package io.github.trimax.raven.server;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
 * <p>Required property: {@code raven.server.port}
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.github.trimax.raven.server")
@RequiredArgsConstructor
public final class RavenServerAutoConfiguration implements SmartInitializingSingleton {

    @Value("${raven.server.port}")
    private final int port;

    private RavenServer server;

    @Bean
    public RavenServer ravenServer(final ServerMessageRouter router,
                                   final ObjectProvider<ServerMessageInterceptor> interceptors) {
        server = new RavenServer(RavenServerConfiguration.builder()
                .port(port)
                .handler(router)
                .interceptors(interceptors.orderedStream().toList())
                .build());
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
