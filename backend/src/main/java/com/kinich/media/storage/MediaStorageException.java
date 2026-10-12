package com.kinich.media.storage;

/** Error de almacenamiento. Mensaje fijo y sin causa: así ni rutas ni nombres llegan a logs o respuestas. */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String operation) {
        super("No se pudo " + operation + " el archivo.", null, false, false);
    }
}
