package com.kinich.auth;

import java.time.Duration;
import lombok.Getter;

@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Demasiados intentos de inicio de sesión");
        this.retryAfter = retryAfter;
    }
}
