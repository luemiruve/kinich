package com.kinich.brand;

/** El SVG no cumple la política: el mensaje es seguro para el usuario (nunca repite contenido del archivo). */
public class InvalidSvgException extends RuntimeException {

    public InvalidSvgException(String message) {
        super(message, null, false, false);
    }
}
