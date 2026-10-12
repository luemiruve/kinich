package com.kinich.brand;

import com.kinich.media.storage.MediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Borra el archivo de un recurso reemplazado o retirado solo cuando la transacción se confirmó: si se revierte, la
 * configuración sigue apuntando al anterior y su archivo debe seguir existiendo.
 */
@Slf4j(topic = "kinich.brand")
final class DeleteAfterCommit implements TransactionSynchronization {

    private final MediaStorage storage;
    private final String storageKey;

    DeleteAfterCommit(MediaStorage storage, String storageKey) {
        this.storage = storage;
        this.storageKey = storageKey;
    }

    @Override
    public void afterCommit() {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar el archivo de un logotipo reemplazado (queda un huérfano físico)");
        }
    }
}
