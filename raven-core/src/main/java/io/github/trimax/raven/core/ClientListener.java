package io.github.trimax.raven.core;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports one session of a {@link RavenClient} to its handler. Used only by that session's thread.
 */
@Slf4j
@RequiredArgsConstructor
final class ClientListener implements SessionListener {

    private final RavenClientConfiguration configuration;

    private InboundPipeline inbound;

    @Override
    public boolean onOpen(final Session session) {
        inbound = InboundPipeline.of(configuration);

        log.info("RavenClient connected to {}:{}", configuration.getHost(), configuration.getPort());
        return HandlingUtil.callHandler(configuration.getHandler()::onConnect, () -> "on connect, closing the connection");
    }

    @Override
    public boolean onMessage(final Session session, final Message message) {
        return inbound.deliver(message);
    }

    @Override
    public void onClose(final Session session) {
        inbound.summarize();
        log.info("RavenClient disconnected");
        HandlingUtil.callHandler(configuration.getHandler()::onDisconnect, () -> "on disconnect");
    }
}
