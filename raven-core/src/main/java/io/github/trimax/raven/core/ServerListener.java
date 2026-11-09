package io.github.trimax.raven.core;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.util.HandlingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports one session of a {@link RavenServer} to its handler as a {@link Client}. Used only by that session's thread.
 */
@Slf4j
@RequiredArgsConstructor
final class ServerListener implements SessionListener {

    private final RavenServerConfiguration configuration;

    private Client client;
    private InboundPipeline inbound;

    @Override
    public boolean onOpen(final Session session) {
        client = new ClientSession(session);
        inbound = InboundPipeline.of(configuration, client);

        log.info("Client connected: {} ({})", client.getId(), session.remoteAddress());
        return HandlingUtil.callHandler(() -> configuration.getHandler().onConnect(client),
                () -> "on connect for " + session.remoteAddress() + ", closing the connection");
    }

    @Override
    public boolean onMessage(final Session session, final Message message) {
        return inbound.deliver(message);
    }

    @Override
    public void onClose(final Session session) {
        inbound.summarize();
        // A failing handler ends nothing more than this session, which is over already
        HandlingUtil.callHandler(() -> configuration.getHandler().onDisconnect(client), () -> "on disconnect for " + session.id());

        log.info("Client disconnected: {}", session.id());
    }
}
