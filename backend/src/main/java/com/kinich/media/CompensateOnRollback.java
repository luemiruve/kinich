package com.kinich.media;

import com.kinich.media.storage.MediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Compensación de huérfanos (también la usan los logotipos SVG): borra el archivo SOLO si la transacción se revirtió. Con resultado incierto
 * (STATUS_UNKNOWN) lo conserva: un huérfano físico es preferible a una fila sin archivo.
 */
@Slf4j(topic = "kinich.media")
public final class CompensateOnRollback implements TransactionSynchronization {

    private final MediaStorage storage;
    private final String storageKey;

    public CompensateOnRollback(MediaStorage storage, String storageKey) {
        this.storage = storage;
        this.storageKey = storageKey;
    }

    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
            try {
                storage.delete(storageKey);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar el archivo de una subida revertida (queda un huérfano físico)");
            }
        } else if (status == STATUS_UNKNOWN) {
            log.warn("Resultado incierto al confirmar una subida: se conserva el archivo");
        }
    }
}
