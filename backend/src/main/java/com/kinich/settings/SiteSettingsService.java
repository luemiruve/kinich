package com.kinich.settings;

import com.kinich.shared.error.ApiProblemException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identidad y contacto de la instalación. Se aplican al guardar; la versión de la fila evita que dos ediciones
 * simultáneas se pisen (409). Los logotipos tienen su propio flujo (SiteLogoService) sobre la misma fila.
 */
@Slf4j(topic = "kinich.settings")
@Service
@RequiredArgsConstructor
public class SiteSettingsService {

    private final BusinessSettingsRepository settings;

    @Transactional(readOnly = true)
    public BusinessSettings load() {
        return require();
    }

    @Transactional
    public BusinessSettings updateIdentity(String businessName, String tagline, long version, UUID actorId) {
        BusinessSettings current = requireVersion(version);
        current.changeIdentity(SettingsNormalizer.identity(businessName, tagline));
        settings.flush();
        log.info("Identidad de la instalación actualizada (userId={})", actorId);
        return current;
    }

    @Transactional
    public BusinessSettings updateContact(String email, String whatsapp, String phone, String location,
                                          String address, String openingHours, long version, UUID actorId) {
        BusinessSettings current = requireVersion(version);
        current.changeContact(SettingsNormalizer.contact(email, whatsapp, phone, location, address, openingHours));
        settings.flush();
        log.info("Contacto de la instalación actualizado (userId={})", actorId);
        return current;
    }

    BusinessSettings requireVersion(long version) {
        BusinessSettings current = require();
        // Hibernate ignora setVersion en una entidad gestionada: la versión enviada se compara explícitamente.
        if (current.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
        return current;
    }

    private BusinessSettings require() {
        return settings.findFirstBy()
                .orElseThrow(() -> new IllegalStateException("Falta la fila de business_settings (migración V8)"));
    }
}
