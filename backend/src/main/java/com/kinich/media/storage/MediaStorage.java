package com.kinich.media.storage;

import java.io.InputStream;

/** Puerto de almacenamiento. La lógica de negocio solo conoce esto; el NAS u objeto/nube serán otro adaptador. */
public interface MediaStorage {

    /** Guarda el contenido y devuelve una clave opaca generada por el adaptador. */
    String store(byte[] content);

    InputStream open(String storageKey);

    /** Idempotente: borrar una clave inexistente no es un error. */
    void delete(String storageKey);
}
