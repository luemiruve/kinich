package com.kinich.settings;

import com.kinich.shared.error.ApiProblemException;
import java.util.Arrays;

/**
 * Variantes explícitas del logotipo. PRIMARY se usa en todos los temas (un SVG preparado con prefers-color-scheme
 * cubre ambos); ON_DARK, opcional, lo sustituye solo con fondo oscuro y exige que exista el principal.
 */
public enum LogoVariant {
    PRIMARY("primary"), ON_DARK("on-dark");

    private final String slug;

    LogoVariant(String slug) {
        this.slug = slug;
    }

    static LogoVariant fromSlug(String slug) {
        return Arrays.stream(values()).filter(variant -> variant.slug.equals(slug)).findFirst()
                .orElseThrow(ApiProblemException::notFound);
    }
}
