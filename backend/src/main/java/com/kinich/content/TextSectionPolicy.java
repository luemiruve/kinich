package com.kinich.content;

import java.util.EnumMap;
import java.util.Map;

/** Secciones que admiten bloques de texto y su máximo (null = sin máximo). Decisión D6. */
public final class TextSectionPolicy {

    private static final Map<SectionKey, Integer> MAX_BLOCKS = new EnumMap<>(SectionKey.class);

    static {
        MAX_BLOCKS.put(SectionKey.EDITORIAL_NOTE, 1);
        MAX_BLOCKS.put(SectionKey.VALUES, null);
    }

    private TextSectionPolicy() {
    }

    public static boolean isTextSection(SectionKey key) {
        return MAX_BLOCKS.containsKey(key);
    }

    public static Integer maxBlocks(SectionKey key) {
        return MAX_BLOCKS.get(key);
    }
}
