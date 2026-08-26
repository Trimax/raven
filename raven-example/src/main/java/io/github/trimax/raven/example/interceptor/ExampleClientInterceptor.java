package io.github.trimax.raven.example.interceptor;

import org.springframework.stereotype.Component;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class ExampleClientInterceptor implements ClientMessageInterceptor {
    @Override
    public boolean preHandle(final Message message) {
        log.info("Intercepted message: {} from server", message);
        return true;
    }
}
