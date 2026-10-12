package com.kinich.content;

import jakarta.validation.constraints.NotNull;

/** Punto focal (mediaPosition) recibido del panel (0–1). */
public record FocalPointInput(@NotNull Double x, @NotNull Double y) {

    FocalPoint toFocalPoint() {
        return new FocalPoint(x, y);
    }
}
