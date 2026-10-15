package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import java.util.Map;

/**
 * Tipo de encuadre por sección. CROP_4_5: rectángulo con proporción 4:5 sobre el original (Disciplinas y
 * Selección, huecos de proporción fija). FOCAL_POINT: punto focal (Hero y Cierre, huecos de proporción variable).
 * El navegador recorta con CSS: cambiar el encuadre nunca regenera archivos. Sin encuadre explícito se usa el
 * centrado (el mayor rectángulo 4:5 o el centro).
 */
public enum ItemFraming {

    CROP_4_5 {
        @Override
        public Framing resolve(Crop crop, FocalPoint mediaPosition, int width, int height) {
            if (mediaPosition != null) {
                throw invalid("mediaPosition", NOT_ALLOWED);
            }
            if (crop == null) {
                return new Framing(centered(width, height), null);
            }
            if (!inBounds(crop)) {
                throw invalid("crop", OUT_OF_BOUNDS);
            }
            crop = snap(crop);
            double ratio = (crop.width() * width) / (crop.height() * height);
            if (Math.abs(ratio / RATIO - 1) > RATIO_TOLERANCE) {
                throw invalid("crop", "debe tener proporción 4:5");
            }
            return new Framing(crop, null);
        }
    },

    FOCAL_POINT {
        @Override
        public Framing resolve(Crop crop, FocalPoint mediaPosition, int width, int height) {
            if (crop != null) {
                throw invalid("crop", NOT_ALLOWED);
            }
            if (mediaPosition == null) {
                return new Framing(null, new FocalPoint(0.5, 0.5));
            }
            if (!unit(mediaPosition.x()) || !unit(mediaPosition.y())) {
                throw invalid("mediaPosition", OUT_OF_BOUNDS);
            }
            return new Framing(null, mediaPosition);
        }
    };

    static final double RATIO = 4.0 / 5.0;
    /** Margen por redondeo a píxeles en el editor (0,5 %). */
    static final double RATIO_TOLERANCE = 0.005;
    private static final double EPSILON = 1e-6;
    private static final String NOT_ALLOWED = "no se admite en esta sección";
    private static final String OUT_OF_BOUNDS = "está fuera de la imagen";

    /** Valida el encuadre recibido contra las dimensiones del original o devuelve el centrado. */
    public abstract Framing resolve(Crop crop, FocalPoint mediaPosition, int width, int height);

    /** El lado limitante vale exactamente 1 y su origen 0 (sin errores de redondeo en los bordes). */
    private static Crop centered(int width, int height) {
        if (width <= height * RATIO) {
            double h = width / (height * RATIO);
            return new Crop(0, (1 - h) / 2, 1, h);
        }
        double w = height * RATIO / width;
        return new Crop((1 - w) / 2, 0, w, 1);
    }

    private static boolean inBounds(Crop c) {
        return c.x() >= -EPSILON && c.y() >= -EPSILON && c.width() > 0 && c.height() > 0
                && c.x() + c.width() <= 1 + EPSILON && c.y() + c.height() <= 1 + EPSILON;
    }

    /** Absorbe desbordes de redondeo (≤ 1e-6) del editor para que el rectángulo quede dentro de [0, 1]. */
    private static Crop snap(Crop c) {
        double width = Math.min(c.width(), 1);
        double height = Math.min(c.height(), 1);
        double x = Math.min(Math.max(c.x(), 0), 1 - width);
        double y = Math.min(Math.max(c.y(), 0), 1 - height);
        return new Crop(x, y, width, height);
    }

    private static boolean unit(double value) {
        return value >= 0 && value <= 1;
    }

    private static ApiProblemException invalid(String field, String message) {
        return ApiProblemException.validation(Map.of(field, message));
    }
}
