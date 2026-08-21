package io.github.trimax.raven.server;

import io.github.trimax.raven.core.RavenServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Raven server.
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
    public RavenServer ravenServer(final ServerMessageRouter router) {
        server = new RavenServer(port, router);
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
