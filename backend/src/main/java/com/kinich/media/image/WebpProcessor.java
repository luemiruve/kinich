package com.kinich.media.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Reescribe el contenedor RIFF/WebP: conserva VP8X, VP8, VP8L, ALPH e ICCP; elimina EXIF, XMP y chunks
 * desconocidos; limpia las banderas EXIF/XMP de VP8X y recalcula el tamaño RIFF. Rechaza animación
 * y orientación ≠ 1.
 */
final class WebpProcessor {

    private static final int FLAG_ANIMATION = 0x02;
    private static final int FLAG_XMP = 0x04;
    private static final int FLAG_EXIF = 0x08;
    private static final Set<String> KEEP = Set.of("VP8X", "VP8 ", "VP8L", "ALPH", "ICCP");

    ImageInspection process(byte[] d) {
        if (d.length < 12 || !Bytes.startsWith(d, 0, "RIFF") || !Bytes.startsWith(d, 8, "WEBP")) {
            throw InvalidImageException.malformed();
        }
        long riffSize = Bytes.u32le(d, 4);
        if (riffSize < 4 || riffSize > d.length - 8L) {
            throw InvalidImageException.malformed();
        }
        int end = (int) (8 + riffSize);
        ByteArrayOutputStream body = new ByteArrayOutputStream(end);
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        int pos = 12;
        int width = -1;
        int height = -1;
        int bitstreams = 0;
        boolean extended = false;
        while (pos < end) {
            String fourcc = Bytes.ascii(d, pos, 4);
            int size = Bytes.size(Bytes.u32le(d, pos + 4));
            int padded = size + (size & 1);
            if (pos + 8L + size > end) {
                throw InvalidImageException.malformed();
            }
            int data = pos + 8;
            switch (fourcc) {
                case "VP8X" -> {
                    if (pos != 12 || size < 10) {
                        throw InvalidImageException.malformed();
                    }
                    if ((Bytes.u8(d, data) & FLAG_ANIMATION) != 0) {
                        throw InvalidImageException.animated();
                    }
                    extended = true;
                    width = 1 + Bytes.u24le(d, data + 4);
                    height = 1 + Bytes.u24le(d, data + 7);
                }
                case "ANIM", "ANMF" -> throw InvalidImageException.animated();
                case "EXIF" -> {
                    int tiff = Bytes.startsWith(d, data, "Exif\0\0") ? data + 6 : data;
                    if (ExifOrientation.read(d, tiff, data + size) != 1) {
                        throw InvalidImageException.rotated();
                    }
                }
                case "VP8 " -> {
                    bitstreams++;
                    if (!extended) {
                        if (size < 10 || Bytes.u8(d, data + 3) != 0x9D || Bytes.u8(d, data + 4) != 0x01
                                || Bytes.u8(d, data + 5) != 0x2A) {
                            throw InvalidImageException.malformed();
                        }
                        width = Bytes.u16le(d, data + 6) & 0x3FFF;
                        height = Bytes.u16le(d, data + 8) & 0x3FFF;
                    }
                }
                case "VP8L" -> {
                    bitstreams++;
                    if (!extended) {
                        if (size < 5 || Bytes.u8(d, data) != 0x2F) {
                            throw InvalidImageException.malformed();
                        }
                        long bits = Bytes.u32le(d, data + 1);
                        width = (int) (bits & 0x3FFF) + 1;
                        height = (int) ((bits >> 14) & 0x3FFF) + 1;
                    }
                }
                default -> {
                    // ALPH/ICCP se copian abajo; XMP y desconocidos se descartan.
                }
            }
            if (KEEP.contains(fourcc)) {
                int copy = (int) Math.min(8L + padded, end - pos);
                int start = body.size();
                body.write(d, pos, copy);
                if (copy < 8 + padded) {
                    body.write(0);
                }
                if (fourcc.equals("VP8X")) {
                    byte[] written = body.toByteArray();
                    written[start + 8] = (byte) (written[start + 8] & ~(FLAG_EXIF | FLAG_XMP));
                    body.reset();
                    body.writeBytes(written);
                }
            }
            pos += 8 + padded;
        }
        if (bitstreams != 1 || width <= 0 || height <= 0) {
            throw InvalidImageException.malformed();
        }
        byte[] content = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(8 + content.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(content.length).put(content);
        return new ImageInspection(MediaFormat.WEBP, width, height, out.array());
    }
}
