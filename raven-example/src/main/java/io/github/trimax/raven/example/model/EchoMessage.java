package io.github.trimax.raven.example.model;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class EchoMessage extends Message {

    @NotBlank
    private final String text;
}
