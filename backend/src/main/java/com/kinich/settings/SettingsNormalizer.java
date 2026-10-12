package com.kinich.settings;

import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.text.PlainText;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normaliza y valida la configuración con las reglas de texto comunes (PlainText). Los mensajes nunca repiten
 * el valor recibido. Las longitudes máximas coinciden con las columnas de V8.
 */
final class SettingsNormalizer {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // E.164: «+», un dígito de 1 a 9 y de 6 a 14 dígitos más (7–15 en total).
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    // Separadores habituales al escribir un teléfono; se eliminan antes de validar.
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s().-]");
    private static final String TOO_LONG = "supera la longitud máxima";

    private SettingsNormalizer() {
    }

    static IdentityValues identity(String businessName, String tagline) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = max("businessName", PlainText.cleanLine("businessName", businessName, errors), 120, errors);
        if (name == null && !errors.containsKey("businessName")) {
            errors.put("businessName", "es obligatorio");
        }
        String cleanTagline = max("tagline", PlainText.cleanLine("tagline", tagline, errors), 200, errors);
        failIfAny(errors);
        return new IdentityValues(name, cleanTagline);
    }

    static ContactValues contact(String email, String whatsapp, String phone, String location, String address,
                                 String openingHours) {
        Map<String, String> errors = new LinkedHashMap<>();
        String cleanEmail = max("email", PlainText.cleanLine("email", email, errors), 254, errors);
        if (cleanEmail != null && !EMAIL.matcher(cleanEmail).matches()) {
            errors.put("email", "no es un correo válido");
        }
        ContactValues values = new ContactValues(cleanEmail, phone("whatsapp", whatsapp, errors),
                phone("phone", phone, errors),
                max("location", PlainText.cleanLine("location", location, errors), 120, errors),
                max("address", PlainText.clean("address", address, errors), 300, errors),
                max("openingHours", PlainText.clean("openingHours", openingHours, errors), 500, errors));
        failIfAny(errors);
        return values;
    }

    static SocialLinkValues socialLink(SocialPlatform platform, String label, String url, boolean visible) {
        Map<String, String> errors = new LinkedHashMap<>();
        String cleanLabel = max("label", PlainText.cleanLine("label", label, errors), 60, errors);
        if (platform == SocialPlatform.OTHER && cleanLabel == null && !errors.containsKey("label")) {
            errors.put("label", "es obligatorio para otra plataforma");
        }
        String cleanUrl = httpsUrl(url, errors);
        failIfAny(errors);
        return new SocialLinkValues(platform, cleanLabel, cleanUrl, visible);
    }

    private static String phone(String field, String value, Map<String, String> errors) {
        String cleaned = PlainText.cleanLine(field, value, errors);
        if (cleaned == null) {
            return null;
        }
        String compact = PHONE_SEPARATORS.matcher(cleaned).replaceAll("");
        if (!E164.matcher(compact).matches()) {
            errors.put(field, "debe tener formato internacional, p. ej. +52 y el número");
            return null;
        }
        return compact;
    }

    /** Solo enlaces externos HTTPS absolutos, con host y sin credenciales incrustadas. */
    private static String httpsUrl(String value, Map<String, String> errors) {
        String cleaned = PlainText.cleanLine("url", value, errors);
        if (cleaned == null) {
            errors.putIfAbsent("url", "es obligatoria");
            return null;
        }
        if (cleaned.length() > 2048) {
            errors.put("url", TOO_LONG);
            return null;
        }
        try {
            URI uri = new URI(cleaned);
            if (uri.getScheme() == null || !uri.getScheme().toLowerCase(Locale.ROOT).equals("https")
                    || uri.getHost() == null || uri.getRawUserInfo() != null) {
                errors.put("url", "debe ser un enlace https:// válido");
                return null;
            }
            return "https" + cleaned.substring(uri.getScheme().length());
        } catch (URISyntaxException e) {
            errors.put("url", "debe ser un enlace https:// válido");
            return null;
        }
    }

    private static String max(String field, String value, int length, Map<String, String> errors) {
        if (value != null && value.length() > length) {
            errors.put(field, TOO_LONG);
            return null;
        }
        return value;
    }

    private static void failIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
    }
}
