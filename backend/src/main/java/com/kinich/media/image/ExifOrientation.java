package com.kinich.media.image;

/** Lee la etiqueta Orientation (0x0112) del IFD0 de un bloque TIFF/EXIF. Devuelve 1 si no existe. */
final class ExifOrientation {

    private static final int ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    static int read(byte[] d, int start, int end) {
        Bytes.check(d, start, end - start);
        boolean little = Bytes.startsWith(d, start, "II");
        if (!little && !Bytes.startsWith(d, start, "MM")) {
            throw InvalidImageException.malformed();
        }
        if (u16(d, start + 2, little, end) != 42) {
            throw InvalidImageException.malformed();
        }
        long ifd = u32(d, start + 4, little, end);
        if (ifd < 8 || ifd > end - start - 2) {
            throw InvalidImageException.malformed();
        }
        int ifdPos = start + (int) ifd;
        int count = u16(d, ifdPos, little, end);
        for (int i = 0; i < count; i++) {
            int entry = ifdPos + 2 + i * 12;
            if (entry + 12 > end) {
                throw InvalidImageException.malformed();
            }
            if (u16(d, entry, little, end) == ORIENTATION) {
                return u16(d, entry + 8, little, end);
            }
        }
        return 1;
    }

    private static int u16(byte[] d, int i, boolean little, int end) {
        if (i + 2 > end) {
            throw InvalidImageException.malformed();
        }
        return little ? Bytes.u16le(d, i) : Bytes.u16be(d, i);
    }

    private static long u32(byte[] d, int i, boolean little, int end) {
        if (i + 4 > end) {
            throw InvalidImageException.malformed();
        }
        return little ? Bytes.u32le(d, i) : Bytes.u32be(d, i);
    }
}
