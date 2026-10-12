package com.kinich.settings;

import com.kinich.brand.BrandAsset;
import com.kinich.brand.BrandAssetService;
import com.kinich.shared.error.ApiProblemException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logotipos de la instalación (recursos de marca SVG). Reemplazar: validar y sanitizar, guardar el recurso nuevo,
 * cambiar la referencia con control de versión, confirmar y, solo entonces, borrar el anterior. Si algo falla antes
 * de confirmar, el archivo nuevo se elimina (no quedan huérfanos). Se aplica al guardar, sin publicación.
 */
@Slf4j(topic = "kinich.settings")
@Service
@RequiredArgsConstructor
public class SiteLogoService {

    private final SiteSettingsService settings;
    private final BusinessSettingsRepository repository;
    private final BrandAssetService brandAssets;

    @Transactional
    public BusinessSettings replace(LogoVariant variant, byte[] svg, long version, UUID actorId) {
        BusinessSettings current = settings.requireVersion(version);
        if (variant == LogoVariant.ON_DARK && current.getLogoAssetId() == null) {
            throw ApiProblemException.validation(Map.of("logo", "primero configura el logotipo principal"));
        }
        BrandAsset asset = brandAssets.store(svg, actorId);
        UUID previous = current.changeLogo(variant, asset.getId());
        // El flush incrementa la versión: una edición simultánea que ya la cambió provoca 409 y se revierte todo.
        repository.flush();
        if (previous != null) {
            brandAssets.discard(previous);
        }
        log.info("Logotipo de la instalación reemplazado (variant={}, userId={})", variant, actorId);
        return current;
    }

    @Transactional
    public BusinessSettings remove(LogoVariant variant, long version, UUID actorId) {
        BusinessSettings current = settings.requireVersion(version);
        if (variant == LogoVariant.PRIMARY && current.getLogoOnDarkAssetId() != null) {
            throw ApiProblemException.validation(Map.of("logo", "primero quita el logotipo para fondo oscuro"));
        }
        UUID previous = current.changeLogo(variant, null);
        repository.flush();
        if (previous != null) {
            brandAssets.discard(previous);
            log.info("Logotipo de la instalación retirado (variant={}, userId={})", variant, actorId);
        }
        return current;
    }
}
