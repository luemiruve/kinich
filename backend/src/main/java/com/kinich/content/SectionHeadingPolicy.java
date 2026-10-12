package com.kinich.content;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Campos de cabecera que admite cada sección de Inicio (CMS controlado: el código define la estructura y el
 * administrador solo su contenido).
 * El Hero solo tiene título (su antetítulo es el del elemento); la Nota editorial y Valores solo un título para
 * lectores de pantalla (el diseño no tiene encabezado visible); el Cierre añade un texto breve.
 */
public final class SectionHeadingPolicy {

    private static final Map<SectionKey, Set<HeadingField>> FIELDS = new EnumMap<>(SectionKey.class);

    static {
        FIELDS.put(SectionKey.HERO, EnumSet.of(HeadingField.TITLE));
        FIELDS.put(SectionKey.EDITORIAL_NOTE, EnumSet.of(HeadingField.TITLE));
        FIELDS.put(SectionKey.DISCIPLINES, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE));
        FIELDS.put(SectionKey.VALUES, EnumSet.of(HeadingField.TITLE));
        FIELDS.put(SectionKey.FEATURED_SELECTION, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE));
        FIELDS.put(SectionKey.CLOSING_CTA, EnumSet.of(HeadingField.EYEBROW, HeadingField.TITLE, HeadingField.BODY));
    }

    private SectionHeadingPolicy() {
    }

    public static Set<HeadingField> fields(SectionKey key) {
        return FIELDS.getOrDefault(key, Set.of());
    }

    /** Nombres JSON en orden fijo (eyebrow, title, body). */
    public static List<String> fieldNames(SectionKey key) {
        Set<HeadingField> fields = fields(key);
        return List.of(HeadingField.values()).stream().filter(fields::contains)
                .map(field -> field.name().toLowerCase()).toList();
    }
}
