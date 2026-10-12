package com.kinich.content;

import jakarta.validation.constraints.NotNull;

/** Rectángulo recibido del panel (0–1). Rango y proporción se validan en el servicio contra el original. */
public record CropInput(@NotNull Double x, @NotNull Double y, @NotNull Double width, @NotNull Double height) {

    Crop toCrop() {
        return new Crop(x, y, width, height);
    }
}
