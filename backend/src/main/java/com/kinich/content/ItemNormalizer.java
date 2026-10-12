package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Misma limpieza que los textos (TextNormalizer.clean) más las reglas de campos de la sección. */
final class ItemNormalizer {

    private static final String REQUIRED = "es obligatorio";
    private static final String NOT_ALLOWED = "no se admite en esta sección";

    private ItemNormalizer() {
    }

    static ItemValues normalize(ItemRules rules, UUID mediaId, String imageAlt, UUID categoryId, String eyebrow,
                                String title, String description) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (mediaId == null) {
            errors.put("mediaId", REQUIRED);
        }
        String alt = TextNormalizer.clean("imageAlt", imageAlt, errors);
        if (alt == null && !errors.containsKey("imageAlt")) {
            errors.put("imageAlt", REQUIRED);
        }
        String cleanEyebrow = field(rules, ItemField.EYEBROW, "eyebrow", TextNormalizer.clean("eyebrow", eyebrow, errors), errors);
        String cleanTitle = field(rules, ItemField.TITLE, "title", TextNormalizer.clean("title", title, errors), errors);
        String cleanDescription = field(rules, ItemField.DESCRIPTION, "description",
                TextNormalizer.clean("description", description, errors), errors);
        UUID category = field(rules, ItemField.CATEGORY, "categoryId", categoryId, errors);
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        return new ItemValues(mediaId, alt, category, cleanEyebrow, cleanTitle, cleanDescription, null);
    }

    private static <T> T field(ItemRules rules, ItemField field, String name, T value, Map<String, String> errors) {
        if (errors.containsKey(name)) {
            return null;
        }
        if (value != null && !rules.allowed().contains(field)) {
            errors.put(name, NOT_ALLOWED);
            return null;
        }
        if (value == null && rules.required().contains(field)) {
            errors.put(name, REQUIRED);
        }
        return value;
    }
}
