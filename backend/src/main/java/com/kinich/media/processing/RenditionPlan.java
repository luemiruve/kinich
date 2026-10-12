package com.kinich.media.processing;

import com.kinich.media.image.MediaFormat;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Representaciones web: cinco anchos candidatos para srcset, sin ampliar nunca el original, en AVIF (prioritario)
 * y un respaldo compatible: JPEG, o PNG si la imagen tiene transparencia real (así no se aplana sobre blanco).
 * WebP no se genera.
 */
public final class RenditionPlan {

    public static final List<Integer> CANDIDATE_WIDTHS = List.of(480, 768, 1280, 1920, 2560);
    private static final List<MediaFormat> OPAQUE_FORMATS = List.of(MediaFormat.AVIF, MediaFormat.JPEG);
    private static final List<MediaFormat> TRANSPARENT_FORMATS = List.of(MediaFormat.AVIF, MediaFormat.PNG);

    private RenditionPlan() {
    }

    /** Anchos ≤ al del original; si el original es más estrecho que el menor candidato, su propio ancho. */
    public static List<Integer> widthsFor(int originalWidth) {
        List<Integer> widths = CANDIDATE_WIDTHS.stream().filter(width -> width <= originalWidth).toList();
        return widths.isEmpty() ? IntStream.of(originalWidth).boxed().toList() : widths;
    }

    /** AVIF primero; el respaldo es PNG solo si algún píxel no es opaco. */
    public static List<MediaFormat> formatsFor(boolean transparent) {
        return transparent ? TRANSPARENT_FORMATS : OPAQUE_FORMATS;
    }
}
