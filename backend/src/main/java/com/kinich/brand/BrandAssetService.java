package com.kinich.brand;

import com.kinich.media.CompensateOnRollback;
import com.kinich.media.storage.MediaStorage;
import com.kinich.media.storage.MediaStorageException;
import com.kinich.shared.error.ApiProblemException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Recursos de identidad visual. Solo los usa la configuración de la instalación (no hay biblioteca de logotipos):
 * cada recurso nace al subirlo para una variante y se elimina al reemplazarlo o retirarlo.
 */
@Slf4j(topic = "kinich.brand")
@Service
@RequiredArgsConstructor
public class BrandAssetService {

    private final SvgSanitizer sanitizer;
    private final MediaStorage storage;
    private final BrandAssetRepository assets;

    /**
     * Valida, sanitiza y guarda el SVG reescrito. Debe ejecutarse dentro de la transacción que lo referencia: si esta
     * se revierte (conflicto de versión, error posterior), el archivo nuevo se borra y no queda huérfano.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BrandAsset store(byte[] data, UUID actorId) {
        SanitizedSvg svg;
        try {
            svg = sanitizer.sanitize(data);
        } catch (SvgTooLargeException e) {
            log.warn("Logotipo rechazado (motivo=tamaño, userId={})", actorId);
            throw ApiProblemException.payloadTooLarge();
        } catch (InvalidSvgException e) {
            log.warn("Logotipo rechazado (motivo=contenido, userId={})", actorId);
            throw ApiProblemException.validation(Map.of("file", e.getMessage()));
        }
        String key = storage.store(svg.content());
        TransactionSynchronizationManager.registerSynchronization(new CompensateOnRollback(storage, key));
        BrandAsset asset = assets.saveAndFlush(BrandAsset.svg(key, svg, sha256(svg.content())));
        log.info("Logotipo guardado (brandAssetId={}, bytes={}, userId={})", asset.getId(), asset.getSizeBytes(), actorId);
        return asset;
    }

    /** Elimina un recurso que ya no referencia la configuración; su archivo se borra tras confirmar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void discard(UUID id) {
        assets.findById(id).ifPresent(asset -> {
            assets.delete(asset);
            assets.flush();
            TransactionSynchronizationManager.registerSynchronization(new DeleteAfterCommit(storage, asset.getStorageKey()));
        });
    }

    @Transactional(readOnly = true)
    public Map<UUID, BrandAsset> find(Collection<UUID> ids) {
        return assets.findAllById(ids).stream().collect(Collectors.toMap(BrandAsset::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public StoredBrandAsset openReferenced(UUID id) {
        BrandAsset asset = assets.findReferenced(id).orElseThrow(ApiProblemException::notFound);
        try {
            return new StoredBrandAsset(asset.getContentType(), asset.getSizeBytes(), asset.getSha256(),
                    storage.open(asset.getStorageKey()));
        } catch (MediaStorageException e) {
            log.error("Falta el archivo de un logotipo (brandAssetId={})", asset.getId());
            throw ApiProblemException.notFound();
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible");
        }
    }
}
