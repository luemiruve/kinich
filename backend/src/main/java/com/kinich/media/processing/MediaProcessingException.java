package com.kinich.media.processing;

/** Fallo técnico del procesamiento (herramienta, tiempo, disco). Mensaje fijo y sin causa: nunca rutas. */
public class MediaProcessingException extends RuntimeException {

    public MediaProcessingException() {
        super("No se pudo procesar la imagen.", null, false, false);
    }
}
