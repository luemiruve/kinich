package com.kinich.media.image;

import java.nio.charset.StandardCharsets;

/** Lecturas con comprobación de límites: cualquier desbordamiento es una imagen inválida, nunca una excepción técnica. */
final class Bytes {

    private Bytes() {
    }

    static void check(byte[] data, long offset, long length) {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw InvalidImageException.malformed();
        }
    }

    static int u8(byte[] d, int i) {
        check(d, i, 1);
        return d[i] & 0xFF;
    }

    static int u16be(byte[] d, int i) {
        check(d, i, 2);
        return ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF);
    }

    static int u16le(byte[] d, int i) {
        check(d, i, 2);
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8);
    }

    static int u24le(byte[] d, int i) {
        check(d, i, 3);
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8) | ((d[i + 2] & 0xFF) << 16);
    }

    static long u32be(byte[] d, int i) {
        check(d, i, 4);
        return ((long) (d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16) | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }

    static long u32le(byte[] d, int i) {
        check(d, i, 4);
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8) | ((d[i + 2] & 0xFF) << 16) | ((long) (d[i + 3] & 0xFF) << 24);
    }

    static String ascii(byte[] d, int i, int length) {
        check(d, i, length);
        return new String(d, i, length, StandardCharsets.ISO_8859_1);
    }

    static boolean startsWith(byte[] d, int i, String prefix) {
        return i >= 0 && prefix.length() <= d.length - i && ascii(d, i, prefix.length()).equals(prefix);
    }

    /** Convierte un tamaño leído del archivo a int, rechazando valores imposibles. */
    static int size(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw InvalidImageException.malformed();
        }
        return (int) value;
    }
}
