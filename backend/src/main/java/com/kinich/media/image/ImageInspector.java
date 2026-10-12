package com.kinich.media.image;

import org.springframework.stereotype.Component;

/** Fachada: formato real por magic bytes → análisis y saneamiento del formato → límite de 8000 px. */
@Component
public class ImageInspector {

    public static final int MAX_DIMENSION = 8000;

    public ImageInspection inspect(byte[] data) {
        MediaFormat format = ImageFormatDetector.detect(data).orElseThrow(InvalidImageException::unsupported);
        ImageInspection result = switch (format) {
            case JPEG -> new JpegProcessor().process(data);
            case PNG -> new PngProcessor().process(data);
            case WEBP -> new WebpProcessor().process(data);
            case AVIF -> new AvifInspector().process(data);
        };
        if (result.width() < 1 || result.height() < 1) {
            throw InvalidImageException.malformed();
        }
        if (result.width() > MAX_DIMENSION || result.height() > MAX_DIMENSION) {
            throw InvalidImageException.tooLarge();
        }
        return result;
    }
}
