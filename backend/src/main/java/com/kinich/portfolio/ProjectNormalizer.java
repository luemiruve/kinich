package com.kinich.portfolio;

import com.kinich.content.CropInput;
import com.kinich.content.Crop;
import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.text.PlainText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Limpia y valida la forma de un proyecto con las reglas comunes de {@link PlainText} y reúne todos los errores
 * por campo en una sola respuesta. No consulta la base de datos (medios, categoría y recorte los comprueba el servicio).
 */
final class ProjectNormalizer {

    private static final String REQUIRED = "es obligatorio";

    private ProjectNormalizer() {
    }

    static ProjectValues normalize(ProjectRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.images() != null && request.images().size() > PortfolioLimits.MAX_IMAGES) {
            throw ApiProblemException.maxItemsReached();
        }
        String slug = limited("slug", PlainText.cleanLine("slug", request.slug(), errors), PortfolioLimits.SLUG_MAX, errors);
        if (slug == null && !errors.containsKey("slug")) {
            errors.put("slug", REQUIRED);
        } else if (slug != null && !PortfolioLimits.SLUG_PATTERN.matcher(slug).matches()) {
            errors.put("slug", "solo admite minúsculas, números y guiones");
        }
        String title = limited("title", PlainText.cleanLine("title", request.title(), errors), PortfolioLimits.TITLE_MAX, errors);
        if (title == null && !errors.containsKey("title")) {
            errors.put("title", REQUIRED);
        }
        String eyebrow = limited("eyebrow", PlainText.cleanLine("eyebrow", request.eyebrow(), errors), PortfolioLimits.EYEBROW_MAX, errors);
        String description = limited("description", PlainText.clean("description", request.description(), errors),
                PortfolioLimits.DESCRIPTION_MAX, errors);

        UUID coverMediaId = null;
        String coverAlt = null;
        Crop coverCrop = null;
        CoverRequest cover = request.cover();
        if (cover == null) {
            errors.put("cover", REQUIRED);
        } else {
            coverMediaId = cover.mediaId();
            if (coverMediaId == null) {
                errors.put("cover.mediaId", REQUIRED);
            }
            coverAlt = alt("cover.alt", cover.alt(), errors);
            coverCrop = crop(cover.crop(), errors);
        }

        List<ProjectImageValues> images = new ArrayList<>();
        if (request.images() == null || request.images().isEmpty()) {
            errors.put("images", "debe incluir al menos una imagen");
        } else {
            for (int i = 0; i < request.images().size(); i++) {
                ProjectImageRequest image = request.images().get(i);
                String prefix = "images[" + i + "].";
                if (image == null) {
                    errors.put("images[" + i + "]", REQUIRED);
                    continue;
                }
                if (image.mediaId() == null) {
                    errors.put(prefix + "mediaId", REQUIRED);
                }
                String alt = alt(prefix + "alt", image.alt(), errors);
                String caption = limited(prefix + "caption", PlainText.clean(prefix + "caption", image.caption(), errors),
                        PortfolioLimits.CAPTION_MAX, errors);
                images.add(new ProjectImageValues(image.mediaId(), alt, caption));
            }
        }

        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        return new ProjectValues(slug, title, eyebrow, request.categoryId(), description, coverMediaId, coverAlt,
                coverCrop, List.copyOf(images));
    }

    private static String alt(String field, String value, Map<String, String> errors) {
        String alt = limited(field, PlainText.clean(field, value, errors), PortfolioLimits.ALT_MAX, errors);
        if (alt == null && !errors.containsKey(field)) {
            errors.put(field, REQUIRED);
        }
        return alt;
    }

    private static Crop crop(CropInput input, Map<String, String> errors) {
        if (input == null) {
            return null;
        }
        if (input.x() == null || input.y() == null || input.width() == null || input.height() == null) {
            errors.put("cover.crop", "es inválido");
            return null;
        }
        return input.toCrop();
    }

    private static String limited(String field, String value, int max, Map<String, String> errors) {
        if (value != null && value.length() > max) {
            errors.put(field, "no puede superar " + max + " caracteres");
            return null;
        }
        return value;
    }
}
