package io.github.trimax.raven.core.internal.session;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.TestMessage;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.NotBlank;

/**
 * Verifies that {@link InvalidMessageLog} caps the WARN lines per connection and summarizes the rest.
 */
class InvalidMessageLogTest {

    private static final List<Violation> VIOLATIONS = List.of(new Violation("content", "NotBlank", "field 'content' must not be blank"));

    private final Logger logger = (Logger) LoggerFactory.getLogger(InvalidMessageLog.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @Test
    void logsFirstInvalidMessagesAtWarnAndTheRestAtDebug() {
        final var invalidMessages = new InvalidMessageLog("peer-1");

        for (int i = 0; i < InvalidMessageLog.WARN_LIMIT + 5; i++)
            invalidMessages.log(new TestMessage(""), VIOLATIONS);

        assertEquals(InvalidMessageLog.WARN_LIMIT, count(Level.WARN));
        assertEquals(5, count(Level.DEBUG));
        assertEquals(5, invalidMessages.skipped());
        assertTrue(appender.list.getFirst().getFormattedMessage().contains("peer-1"));
    }

    @Test
    void summaryReportsSkippedMessages() {
        final var invalidMessages = new InvalidMessageLog("peer-1");
        for (int i = 0; i < InvalidMessageLog.WARN_LIMIT + 3; i++)
            invalidMessages.log(new TestMessage(""), VIOLATIONS);
        appender.list.clear();

        invalidMessages.summarize();

        assertEquals(1, appender.list.size());
        assertEquals(Level.WARN, appender.list.getFirst().getLevel());
        assertEquals("Skipped 3 further invalid messages from peer-1 (logged at DEBUG only)",
                appender.list.getFirst().getFormattedMessage());
    }

    @Test
    void noSummaryWithinTheLimit() {
        final var invalidMessages = new InvalidMessageLog("peer-1");
        for (int i = 0; i < InvalidMessageLog.WARN_LIMIT; i++)
            invalidMessages.log(new TestMessage(""), VIOLATIONS);
        appender.list.clear();

        invalidMessages.summarize();

        assertTrue(appender.list.isEmpty(), "nothing was skipped, so there is nothing to summarize");
    }

    @Test
    void serverSummarizesSkippedMessagesWhenSessionEnds() throws Exception {
        final var connected = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        connected.add(client);
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                    }
                })
                .build());
        server.start();
        try {
            // A raw socket bypasses the send-side validation of RavenClient
            try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
                final var output = socket.getOutputStream();
                RawPeer.writeHello(output);
                for (int i = 0; i < InvalidMessageLog.WARN_LIMIT + 2; i++)
                    RawPeer.writeObject(output, new BlankMessage());

                await().atMost(2, TimeUnit.SECONDS).until(() -> appender.list.size() == InvalidMessageLog.WARN_LIMIT + 2);
            }

            final var expected = "Skipped 2 further invalid messages from " + connected.getFirst().getId() + " (logged at DEBUG only)";
            await().atMost(2, TimeUnit.SECONDS).until(() -> appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().equals(expected)));
            assertEquals(InvalidMessageLog.WARN_LIMIT + 1, count(Level.WARN));
        } finally {
            server.stop();
        }
    }

    private long count(final Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).count();
    }

    /** Always invalid: its {@code @NotBlank} field is never set. */
    static final class BlankMessage extends Message {
        @NotBlank
        @SuppressWarnings("unused")
        private String content;
    }
}
