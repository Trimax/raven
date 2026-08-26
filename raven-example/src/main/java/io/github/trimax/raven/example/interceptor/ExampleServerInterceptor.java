package io.github.trimax.raven.example.interceptor;

import org.springframework.stereotype.Component;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class ExampleServerInterceptor implements ServerMessageInterceptor {
    @Override
    public boolean intercept(final Client sender, final Message message) {
        log.info("Intercepted message: {} from {}", message, sender);
        return true;
    }
}
