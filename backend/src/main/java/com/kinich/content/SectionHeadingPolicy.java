package com.kinich.content;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Campos de cabecera que admite cada sección de cada página (CMS controlado: el código define la estructura y el
 * administrador solo su contenido).
 * Inicio: el Hero solo tiene título (su antetítulo es el del elemento); la Nota editorial y Valores solo un título
 * para lectores de pantalla (el diseño no tiene encabezado visible); el Cierre añade un texto breve.
 * Portafolio: la cabecera de página y el Cierre tienen antetítulo, título y texto breve (solo cabeceras: los
 * proyectos viven en su propio módulo).
 */
public final class SectionHeadingPolicy {

    private static final Map<PageKey, Map<SectionKey, Set<HeadingField>>> FIELDS = new EnumMap<>(PageKey.class);

    static {
        Map<SectionKey, Set<HeadingField>> home = new EnumMap<>(SectionKey.class);
        home.put(SectionKey.HERO, EnumSet.of(HeadingField.TITLE));
        home.put(SectionKey.EDITORIAL_NOTE, EnumSet.of(HeadingField.TITLE));
        home.put(SectionKey.DISCIPLINES, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE));
        home.put(SectionKey.VALUES, EnumSet.of(HeadingField.TITLE));
        home.put(SectionKey.FEATURED_SELECTION, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE));
        home.put(SectionKey.CLOSING_CTA, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE, HeadingField.BODY));
        FIELDS.put(PageKey.HOME, home);

        Map<SectionKey, Set<HeadingField>> portfolio = new EnumMap<>(SectionKey.class);
        portfolio.put(SectionKey.PAGE_HEADER, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE, HeadingField.BODY));
        portfolio.put(SectionKey.CLOSING_CTA, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE, HeadingField.BODY));
        FIELDS.put(PageKey.PORTFOLIO, portfolio);
    }

    private SectionHeadingPolicy() {
    }

    public static Set<HeadingField> fields(PageKey page, SectionKey key) {
        return FIELDS.getOrDefault(page, Map.of()).getOrDefault(key, Set.of());
    }

    /** Nombres JSON en orden fijo (eyebrow, title, body). */
    public static List<String> fieldNames(PageKey page, SectionKey key) {
        Set<HeadingField> fields = fields(page, key);
        return List.of(HeadingField.values()).stream().filter(fields::contains)
                .map(field -> field.name().toLowerCase()).toList();
    }
}
