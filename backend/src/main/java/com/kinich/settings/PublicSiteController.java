package com.kinich.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Identidad, contacto y redes visibles de la instalación (cadena pública: solo GET, sin cookies). */
@RestController
@RequestMapping("/api/public/site")
@RequiredArgsConstructor
class PublicSiteController {

    private final SiteSettingsReader reader;

    @GetMapping
    PublicSiteResponse site() {
        return reader.forPublic();
    }
}
