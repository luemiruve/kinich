package com.kinich.brand;

/** El SVG supera el tamaño máximo (413, igual que una imagen demasiado grande). */
public class SvgTooLargeException extends RuntimeException {

    public SvgTooLargeException() {
        super("El SVG supera el tamaño máximo", null, false, false);
    }
}
