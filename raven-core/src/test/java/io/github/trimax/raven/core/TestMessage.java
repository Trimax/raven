package io.github.trimax.raven.core;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Simple message used in transport layer tests.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestMessage extends Message {

    private String content;
}
