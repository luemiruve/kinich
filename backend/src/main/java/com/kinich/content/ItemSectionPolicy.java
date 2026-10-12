package com.kinich.content;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Secciones que admiten elementos audiovisuales. Cualquier otra sección → 404. */
public final class ItemSectionPolicy {

    private static final Set<ItemField> CARD =
            Set.of(ItemField.CATEGORY, ItemField.EYEBROW, ItemField.TITLE, ItemField.DESCRIPTION);
    private static final Map<SectionKey, ItemRules> RULES = new EnumMap<>(SectionKey.class);

    static {
        RULES.put(SectionKey.HERO, new ItemRules(1, Set.of(ItemField.EYEBROW), Set.of(), ItemFraming.FOCAL_POINT));
        RULES.put(SectionKey.DISCIPLINES, new ItemRules(4, CARD, Set.of(ItemField.TITLE), ItemFraming.CROP_4_5));
        RULES.put(SectionKey.FEATURED_SELECTION, new ItemRules(null, CARD, Set.of(ItemField.TITLE), ItemFraming.CROP_4_5));
        RULES.put(SectionKey.CLOSING_CTA, new ItemRules(1, Set.of(), Set.of(), ItemFraming.FOCAL_POINT));
    }

    private ItemSectionPolicy() {
    }

    public static boolean isItemSection(SectionKey key) {
        return RULES.containsKey(key);
    }

    public static ItemRules rules(SectionKey key) {
        return RULES.get(key);
    }
}
