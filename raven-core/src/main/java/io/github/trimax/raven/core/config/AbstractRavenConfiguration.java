package io.github.trimax.raven.core.config;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Base configuration shared by both Raven server and client.
 */
@Getter
@SuperBuilder
abstract class AbstractRavenConfiguration {

    private final int port;
}
