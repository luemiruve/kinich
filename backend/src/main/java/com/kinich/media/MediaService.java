package com.kinich.media;

import com.kinich.media.image.ImageInspection;
import com.kinich.media.image.ImageInspector;
import com.kinich.media.image.InvalidImageException;
import com.kinich.media.image.MediaFormat;
import com.kinich.media.processing.ImageRenditionProcessor;
import com.kinich.media.processing.RenditionFile;
import com.kinich.media.storage.MediaStorage;
import com.kinich.media.storage.MediaStorageException;
import com.kinich.shared.error.ApiProblemException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Flujo de medios: original privado → validación → libvips → representaciones web.
 * La subida procesa en la misma petición. Cada archivo escrito registra su compensación: si la transacción se
 * revierte, se borran todos. Logs con ids, tipo, tamaño, usuario y duración; nunca rutas, claves ni nombres.
 */
@Slf4j(topic = "kinich.media")
@Service
@RequiredArgsConstructor
public class MediaService {

    public static final long MAX_BYTES = 10_485_760L;

    private final ImageInspector inspector;
    private final ImageRenditionProcessor processor;
    private final MediaStorage storage;
    private final MediaAssetRepository assets;
    private final MediaFileRepository files;

    @Transactional
    public MediaAsset upload(byte[] data, UUID actorId) {
        if (data.length == 0) {
            log.warn("Subida rechazada (motivo=vacía, userId={})", actorId);
            throw ApiProblemException.validation(Map.of("file", "es obligatorio"));
        }
        if (data.length > MAX_BYTES) {
            log.warn("Subida rechazada (motivo=tamaño, userId={})", actorId);
            throw ApiProblemException.payloadTooLarge();
        }
        long started = System.nanoTime();
        // Barrera previa a decodificar: formato real, estructura, dimensiones, animación y orientación.
        ImageInspection image = validate(data, actorId);
        List<RenditionFile> renditions;
        try {
            renditions = processor.render(data, image.format(), image.width());
        } catch (InvalidImageException e) {
            log.warn("Subida rechazada (motivo=decodificación, userId={})", actorId);
            throw ApiProblemException.validation(Map.of("file", e.getMessage()));
        }
        MediaAsset asset = assets.saveAndFlush(MediaAsset.readyImage(image.format(), data.length, image.width(),
                image.height(), sha256(data)));
        List<MediaFile> stored = new ArrayList<>();
        stored.add(new MediaFile(asset.getId(), MediaFileRole.ORIGINAL, image.format().contentType(), image.width(),
                image.height(), data.length, store(data)));
        for (RenditionFile rendition : renditions) {
            stored.add(new MediaFile(asset.getId(), MediaFileRole.WEB, rendition.format().contentType(),
                    rendition.width(), rendition.height(), rendition.content().length, store(rendition.content())));
        }
        files.saveAllAndFlush(stored);
        log.info("Imagen subida (mediaId={}, type={}, bytes={}, renditions={}, ms={}, userId={})", asset.getId(),
                asset.getContentType(), asset.getSizeBytes(), renditions.size(),
                (System.nanoTime() - started) / 1_000_000, actorId);
        return asset;
    }

    private ImageInspection validate(byte[] data, UUID actorId) {
        try {
            return inspector.inspect(data);
        } catch (InvalidImageException e) {
            log.warn("Subida rechazada (motivo=contenido, userId={})", actorId);
            throw ApiProblemException.validation(Map.of("file", e.getMessage()));
        }
    }

    /** Escribe un archivo y registra su compensación antes de seguir (un fallo posterior lo borra). */
    private String store(byte[] content) {
        String key = storage.store(content);
        TransactionSynchronizationManager.registerSynchronization(new CompensateOnRollback(storage, key));
        return key;
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> list() {
        return assets.findAllByOrderByCreatedAtDesc();
    }


    @Transactional(readOnly = true)
    public Map<UUID, MediaAsset> findAssets(Collection<UUID> ids) {
        return assets.findAllById(ids).stream().collect(Collectors.toMap(MediaAsset::getId, Function.identity()));
    }

    /** Representaciones web por medio, en lote (sin N+1). El original nunca aparece. */
    @Transactional(readOnly = true)
    public Map<UUID, MediaRenditions> renditions(Collection<UUID> assetIds, MediaAudience audience) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return files.findByMediaAssetIdInAndRole(assetIds, MediaFileRole.WEB).stream()
                .collect(Collectors.groupingBy(MediaFile::getMediaAssetId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toRenditions(entry.getValue(), audience)));
    }

    /** AVIF como fuente prioritaria; respaldo JPEG o, si la imagen tiene transparencia, PNG (nunca ambos). */
    private static MediaRenditions toRenditions(List<MediaFile> web, MediaAudience audience) {
        ImageSource avif = source(web, MediaFormat.AVIF, audience);
        ImageSource png = source(web, MediaFormat.PNG, audience);
        ImageSource fallback = png.candidates().isEmpty() ? source(web, MediaFormat.JPEG, audience) : png;
        return new MediaRenditions(avif.candidates().isEmpty() ? List.of() : List.of(avif), fallback);
    }

    private static ImageSource source(List<MediaFile> web, MediaFormat format, MediaAudience audience) {
        return new ImageSource(format.contentType(), web.stream()
                .filter(file -> file.getContentType().equals(format.contentType()))
                .sorted(Comparator.comparingInt(MediaFile::getWidth))
                .map(file -> new ImageCandidate(audience.url(file.getId()), file.getWidth()))
                .toList());
    }

    @Transactional(readOnly = true)
    public StoredMedia openWebFileForAdmin(UUID fileId) {
        return open(files.findByIdAndRole(fileId, MediaFileRole.WEB).orElseThrow(ApiProblemException::notFound));
    }

    @Transactional(readOnly = true)
    public StoredMedia openPublishedWebFile(UUID fileId) {
        return open(files.findPublishedWebFile(fileId).orElseThrow(ApiProblemException::notFound));
    }

    private StoredMedia open(MediaFile found) {
        try {
            return new StoredMedia(found.getId(), found.getContentType(), found.getSizeBytes(),
                    storage.open(found.getStorageKey()));
        } catch (MediaStorageException e) {
            log.error("Falta el archivo de un medio (mediaId={}, fileId={})", found.getMediaAssetId(), found.getId());
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
