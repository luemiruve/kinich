package com.kinich.media;

/**
 * Estado del procesamiento. Hoy las imágenes se procesan durante la subida y solo se escribe READY;
 * PROCESSING y FAILED quedan previstos para el procesamiento en segundo plano futuro.
 */
public enum MediaAssetStatus {
    PROCESSING, READY, FAILED
}
