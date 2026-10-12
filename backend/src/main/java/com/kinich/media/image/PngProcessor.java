package com.kinich.media.image;

import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Copia solo los chunks de la lista blanca (imagen y color), verificando CRC. Elimina tEXt/zTXt/iTXt/eXIf/tIME
 * y cualquier otro chunk auxiliar; descarta lo que sigue a IEND; rechaza APNG y orientación ≠ 1.
 */
final class PngProcessor {

    private static final Set<String> KEEP =
            Set.of("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", "pHYs");

    ImageInspection process(byte[] d) {
        if (ImageFormatDetector.detect(d).filter(f -> f == MediaFormat.PNG).isEmpty()) {
            throw InvalidImageException.malformed();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
        out.write(d, 0, 8);
        int pos = 8;
        int width = -1;
        int height = -1;
        boolean sawData = false;
        while (true) {
            int length = Bytes.size(Bytes.u32be(d, pos));
            Bytes.check(d, pos, 12L + length);
            String type = Bytes.ascii(d, pos + 4, 4);
            if (!type.chars().allMatch(Character::isLetter)) {
                throw InvalidImageException.malformed();
            }
            CRC32 crc = new CRC32();
            crc.update(d, pos + 4, 4 + length);
            if (crc.getValue() != Bytes.u32be(d, pos + 8 + length)) {
                throw InvalidImageException.malformed();
            }
            if (width == -1) {
                if (!type.equals("IHDR") || length != 13) {
                    throw InvalidImageException.malformed();
                }
                width = Bytes.size(Bytes.u32be(d, pos + 8));
                height = Bytes.size(Bytes.u32be(d, pos + 12));
            }
            if (type.equals("acTL")) {
                throw InvalidImageException.animated();
            }
            if (type.equals("eXIf") && ExifOrientation.read(d, pos + 8, pos + 8 + length) != 1) {
                throw InvalidImageException.rotated();
            }
            if (KEEP.contains(type)) {
                out.write(d, pos, 12 + length);
            } else if (Character.isUpperCase(type.charAt(0))) {
                throw InvalidImageException.malformed();
            }
            sawData |= type.equals("IDAT");
            pos += 12 + length;
            if (type.equals("IEND")) {
                break;
            }
        }
        if (!sawData) {
            throw InvalidImageException.malformed();
        }
        return new ImageInspection(MediaFormat.PNG, width, height, out.toByteArray());
    }
}
