package com.kinich.shared.text;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Limpieza común de textos introducidos en la administración: recorta, unifica saltos de línea, convierte los
 * vacíos en null y rechaza caracteres de control (salvo salto de línea y tabulador en textos multilínea). Los
 * mensajes de error nunca repiten el valor recibido.
 */
public final class PlainText {

    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F]");
    private static final Pattern LINE_BREAK = Pattern.compile("[\\n\\t]");
    public static final String NOT_ALLOWED = "contiene caracteres no permitidos";

    private PlainText() {
    }

    /** Texto que admite varias líneas. */
    public static String clean(String field, String value, Map<String, String> errors) {
        if (value == null) {
            return null;
        }
        String unified = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (CONTROL.matcher(unified).find()) {
            errors.put(field, NOT_ALLOWED);
            return null;
        }
        return unified.isEmpty() ? null : unified;
    }

    /** Texto de una sola línea (nombres, lemas): además rechaza saltos de línea y tabuladores. */
    public static String cleanLine(String field, String value, Map<String, String> errors) {
        String cleaned = clean(field, value, errors);
        if (cleaned != null && LINE_BREAK.matcher(cleaned).find()) {
            errors.put(field, "debe ocupar una sola línea");
            return null;
        }
        return cleaned;
    }
}
