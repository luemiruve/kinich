package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.text.PlainText;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normaliza los textos antes de guardarlos con las reglas comunes de {@link PlainText}; el cuerpo es obligatorio. */
final class TextNormalizer {

    private TextNormalizer() {
    }

    static TextValues normalize(String eyebrow, String title, String body) {
        Map<String, String> errors = new LinkedHashMap<>();
        String cleanEyebrow = clean("eyebrow", eyebrow, errors);
        String cleanTitle = clean("title", title, errors);
        String cleanBody = clean("body", body, errors);
        if (cleanBody == null && !errors.containsKey("body")) {
            errors.put("body", "es obligatorio");
        }
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        return new TextValues(cleanEyebrow, cleanTitle, cleanBody);
    }

    static String clean(String field, String value, Map<String, String> errors) {
        return PlainText.clean(field, value, errors);
    }
}
