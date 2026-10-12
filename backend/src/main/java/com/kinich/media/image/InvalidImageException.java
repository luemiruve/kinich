package com.kinich.media.image;

/** Imagen rechazada. El mensaje se muestra al fotógrafo: nunca contiene rutas, nombres ni detalles del analizador. */
public final class InvalidImageException extends RuntimeException {

    private InvalidImageException(String message) {
        super(message, null, false, false);
    }

    public static InvalidImageException unsupported() {
        return new InvalidImageException("formato no permitido: usa JPEG, PNG, WebP o AVIF");
    }

    public static InvalidImageException malformed() {
        return new InvalidImageException("la imagen está dañada o no es válida");
    }

    public static InvalidImageException tooLarge() {
        return new InvalidImageException("supera el máximo de 8000 × 8000 píxeles");
    }

    public static InvalidImageException rotated() {
        return new InvalidImageException("la imagen tiene una rotación pendiente: expórtala con la rotación aplicada");
    }

    public static InvalidImageException animated() {
        return new InvalidImageException("no se admiten imágenes animadas");
    }

    public static InvalidImageException metadata() {
        return new InvalidImageException("la imagen contiene metadatos o miniaturas que no se pueden eliminar: expórtala sin ellos");
    }
}
