package com.kinich.media.image;

import java.util.Optional;

/** Identifica el formato SOLO por su contenido. La extensión y el Content-Type del cliente no se consultan. */
public final class ImageFormatDetector {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageFormatDetector() {
    }

    public static Optional<MediaFormat> detect(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return Optional.of(MediaFormat.JPEG);
        }
        if (d.length >= PNG.length && java.util.Arrays.equals(d, 0, PNG.length, PNG, 0, PNG.length)) {
            return Optional.of(MediaFormat.PNG);
        }
        if (d.length >= 12 && Bytes.startsWith(d, 0, "RIFF") && Bytes.startsWith(d, 8, "WEBP")) {
            return Optional.of(MediaFormat.WEBP);
        }
        if (d.length >= 16 && Bytes.startsWith(d, 4, "ftyp")) {
            int boxSize = (int) Math.min(Bytes.u32be(d, 0), d.length);
            for (int i = 8; i + 4 <= boxSize; i += 4) {
                if (i == 12) {
                    continue; // minor_version
                }
                String brand = Bytes.ascii(d, i, 4);
                if (brand.equals("avif") || brand.equals("avis")) {
                    return Optional.of(MediaFormat.AVIF);
                }
            }
        }
        return Optional.empty();
    }
}
