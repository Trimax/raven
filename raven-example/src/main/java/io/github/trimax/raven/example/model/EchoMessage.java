package io.github.trimax.raven.example.model;

import io.github.trimax.raven.core.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public final class EchoMessage extends Message {

    private String text;
}
