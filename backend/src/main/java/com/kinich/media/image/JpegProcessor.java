package com.kinich.media.image;

import java.io.ByteArrayOutputStream;

/**
 * Recorre los segmentos del JPEG y reescribe el archivo sin metadatos, sin recodificar píxeles:
 * - elimina APP1 (EXIF/XMP, incluido GPS), APP13 (IPTC), COM, APP0 JFXX y todo APPn salvo APP2 ICC_PROFILE y
 *   APP14 Adobe; APP0 JFIF se reescribe SIN miniatura (podría mostrar el encuadre original);
 * - descarta todo lo que sigue al primer EOI (imágenes MPF secundarias y datos anexados);
 * - solo acepta SOF0/SOF1/SOF2 (lo que muestran los navegadores);
 * - rechaza orientación EXIF distinta de 1 y marcadores desconocidos.
 */
final class JpegProcessor {

    ImageInspection process(byte[] d) {
        if (d.length < 4 || Bytes.u8(d, 0) != 0xFF || Bytes.u8(d, 1) != 0xD8) {
            throw InvalidImageException.malformed();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        int width = -1;
        int height = -1;
        while (true) {
            if (Bytes.u8(d, pos) != 0xFF) {
                throw InvalidImageException.malformed();
            }
            while (Bytes.u8(d, pos) == 0xFF) {
                pos++;
            }
            int marker = Bytes.u8(d, pos++);
            if (marker == 0xD9) {
                out.write(0xFF);
                out.write(0xD9);
                break;
            }
            if (marker == 0x00 || marker == 0x01 || marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7)) {
                throw InvalidImageException.malformed();
            }
            int length = Bytes.u16be(d, pos);
            if (length < 2) {
                throw InvalidImageException.malformed();
            }
            Bytes.check(d, pos, length);
            int payload = pos + 2;
            int payloadLength = length - 2;
            if (marker == 0xE0) {
                if (Bytes.startsWith(d, payload, "JFIF\0")) {
                    if (payloadLength < 14) {
                        throw InvalidImageException.malformed();
                    }
                    out.write(0xFF);
                    out.write(0xE0);
                    out.write(0);
                    out.write(16);
                    out.write(d, payload, 12); // identificador, versión, unidades y densidades
                    out.write(0);              // ancho de la miniatura
                    out.write(0);              // alto de la miniatura
                }
                pos += length;
                continue;
            }
            if (isStartOfFrame(marker)) {
                if (marker > 0xC2) {
                    throw InvalidImageException.malformed();
                }
                if (width != -1 || payloadLength < 6) {
                    throw InvalidImageException.malformed();
                }
                height = Bytes.u16be(d, payload + 1);
                width = Bytes.u16be(d, payload + 3);
            }
            if (marker == 0xE1 && Bytes.startsWith(d, payload, "Exif\0\0")
                    && ExifOrientation.read(d, payload + 6, payload + payloadLength) != 1) {
                throw InvalidImageException.rotated();
            }
            if (keep(marker, d, payload)) {
                out.write(0xFF);
                out.write(marker);
                out.write(d, pos, length);
            }
            pos += length;
            if (marker == 0xDA) {
                if (width == -1) {
                    throw InvalidImageException.malformed();
                }
                int start = pos;
                pos = endOfEntropyData(d, pos);
                out.write(d, start, pos - start);
            }
        }
        if (width <= 0 || height <= 0) {
            throw InvalidImageException.malformed();
        }
        return new ImageInspection(MediaFormat.JPEG, width, height, out.toByteArray());
    }

    /** Avanza por los datos comprimidos hasta el siguiente marcador real (ni relleno FF00 ni RSTn). */
    private static int endOfEntropyData(byte[] d, int pos) {
        while (true) {
            if (Bytes.u8(d, pos) != 0xFF) {
                pos++;
                continue;
            }
            int next = Bytes.u8(d, pos + 1);
            if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                pos += 2;
            } else if (next == 0xFF) {
                pos++;
            } else {
                return pos;
            }
        }
    }

    private static boolean isStartOfFrame(int m) {
        return (m >= 0xC0 && m <= 0xCF) && m != 0xC4 && m != 0xC8 && m != 0xCC;
    }

    private static boolean keep(int marker, byte[] d, int payload) {
        if (marker == 0xE2) {
            return Bytes.startsWith(d, payload, "ICC_PROFILE\0");
        }
        if (marker == 0xEE) {
            return Bytes.startsWith(d, payload, "Adobe");
        }
        if (marker >= 0xE0 && marker <= 0xEF || marker == 0xFE) {
            return false;
        }
        if (isStartOfFrame(marker) || marker == 0xC4 || marker == 0xCC || marker == 0xDA || marker == 0xDB
                || marker == 0xDC || marker == 0xDD || marker == 0xDE || marker == 0xDF) {
            return true;
        }
        throw InvalidImageException.malformed();
    }
}
