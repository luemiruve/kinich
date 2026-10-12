package com.kinich.media.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Imágenes sintéticas para las pruebas: nunca fotografías reales. */
public final class ImageFixtures {

    public static final byte[] GPS_MARKER = "GPS-LAT-19.70".getBytes(StandardCharsets.US_ASCII);

    private ImageFixtures() {
    }

    public static byte[] jpeg(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    /** PNG con canal alfa totalmente transparente (p. ej. el fondo de un logotipo). */
    public static byte[] transparentPng(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("sin escritor para " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** TIFF big-endian con IFD0: Orientation y, opcionalmente, un valor ASCII que imita datos GPS. */
    public static byte[] exifTiff(int orientation, boolean withGps) {
        int entries = withGps ? 2 : 1;
        int ifdSize = 2 + entries * 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(8 + ifdSize + GPS_MARKER.length).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) entries);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        if (withGps) {
            tiff.putShort((short) 0x010E).putShort((short) 2).putInt(GPS_MARKER.length).putInt(8 + ifdSize);
        }
        tiff.putInt(0);
        if (withGps) {
            tiff.put(GPS_MARKER);
        }
        return tiff.array();
    }

    public static byte[] exifApp1Payload(int orientation, boolean withGps) {
        byte[] tiff = exifTiff(orientation, withGps);
        byte[] payload = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.US_ASCII), 0, payload, 0, 6);
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        return payload;
    }

    /** Inserta un segmento justo después de SOI. */
    public static byte[] withJpegSegment(byte[] jpeg, int marker, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(marker);
        int length = payload.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Inserta un chunk con CRC correcto justo después de IHDR. */
    public static byte[] withPngChunk(byte[] png, String type, byte[] data) {
        int afterIhdr = 8 + 12 + 13;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, afterIhdr);
        writePngChunk(out, type, data);
        out.write(png, afterIhdr, png.length - afterIhdr);
        return out.toByteArray();
    }

    public static void writePngChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    public static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static byte[] resource(String name) {
        try (var in = ImageFixtures.class.getResourceAsStream("/media/" + name)) {
            return java.util.Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] riffChunk(String fourcc, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(8 + data.length + (data.length & 1)).order(ByteOrder.LITTLE_ENDIAN);
        b.put(fourcc.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return b.array();
    }

    /** Envuelve el bitstream de un WebP simple en un contenedor VP8X con las banderas y chunks indicados. */
    public static byte[] webpExtended(byte[] simpleWebp, int width, int height, int flags, byte[]... extraChunks) {
        byte[] bitstream = java.util.Arrays.copyOfRange(simpleWebp, 12, simpleWebp.length);
        byte[] vp8x = new byte[10];
        vp8x[0] = (byte) flags;
        int w = width - 1;
        int h = height - 1;
        vp8x[4] = (byte) w; vp8x[5] = (byte) (w >> 8); vp8x[6] = (byte) (w >> 16);
        vp8x[7] = (byte) h; vp8x[8] = (byte) (h >> 8); vp8x[9] = (byte) (h >> 16);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(riffChunk("VP8X", vp8x));
        for (byte[] extra : extraChunks) {
            body.writeBytes(extra);
        }
        body.writeBytes(bitstream);
        byte[] content = body.toByteArray();
        ByteBuffer riff = ByteBuffer.allocate(8 + content.length).order(ByteOrder.LITTLE_ENDIAN);
        riff.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(content.length).put(content);
        return riff.array();
    }

    public static byte[] box(String type, byte[]... children) {
        int size = 8;
        for (byte[] c : children) {
            size += c.length;
        }
        ByteBuffer b = ByteBuffer.allocate(size);
        b.putInt(size).put(type.getBytes(StandardCharsets.US_ASCII));
        for (byte[] c : children) {
            b.put(c);
        }
        return b.array();
    }

    public static byte[] fullBox(String type, int version, byte[]... children) {
        byte[][] all = new byte[children.length + 1][];
        all[0] = new byte[] {(byte) version, 0, 0, 0};
        System.arraycopy(children, 0, all, 1, children.length);
        return box(type, all);
    }

    public static byte[] ftyp(String major, String... compatible) {
        ByteBuffer b = ByteBuffer.allocate(8 + 4 * compatible.length);
        b.put(major.getBytes(StandardCharsets.US_ASCII)).putInt(0);
        for (String c : compatible) {
            b.put(c.getBytes(StandardCharsets.US_ASCII));
        }
        return box("ftyp", b.array());
    }

    public static byte[] infe(int itemId, String itemType, String contentType) {
        ByteBuffer b = ByteBuffer.allocate(64);
        b.putShort((short) itemId).putShort((short) 0).put(itemType.getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        if (contentType != null) {
            b.put(contentType.getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        }
        return fullBox("infe", 2, java.util.Arrays.copyOf(b.array(), b.position()));
    }

    public static byte[] ispe(int width, int height) {
        return fullBox("ispe", 0, ByteBuffer.allocate(8).putInt(width).putInt(height).array());
    }

    /** irot: 6 bits reservados + 2 bits de ángulo (× 90°). */
    public static byte[] irot(int quarterTurns) {
        return box("irot", new byte[] {(byte) (quarterTurns & 0x03)});
    }

    /** ipma versión 0, sin bandera 1: item_ID u16, n asociaciones de 1 byte (índice 1-based en ipco). */
    public static byte[] ipma(int itemId, int... propertyIndexes) {
        ByteBuffer b = ByteBuffer.allocate(4 + 2 + 1 + propertyIndexes.length);
        b.putInt(1).putShort((short) itemId).put((byte) propertyIndexes.length);
        for (int index : propertyIndexes) {
            b.put((byte) index);
        }
        return fullBox("ipma", 0, b.array());
    }

    /** AVIF sintético (sin datos de imagen): ítem primario 1; ipco = `properties`; se le asocian todas. */
    public static byte[] avif(String major, byte[][] properties, byte[] iref, byte[]... extraInfe) {
        return avif(major, properties, java.util.stream.IntStream.rangeClosed(1, properties.length).toArray(), iref, extraInfe);
    }

    /** Igual, pero asociando al ítem primario solo los índices (1-based) indicados. */
    public static byte[] avif(String major, byte[][] properties, int[] primaryIndexes, byte[] iref, byte[]... extraInfe) {
        byte[][] infes = new byte[extraInfe.length + 1][];
        infes[0] = infe(1, "av01", null);
        System.arraycopy(extraInfe, 0, infes, 1, extraInfe.length);
        byte[][] iinfChildren = new byte[infes.length + 1][];
        iinfChildren[0] = ByteBuffer.allocate(2).putShort((short) infes.length).array();
        System.arraycopy(infes, 0, iinfChildren, 1, infes.length);
        int[] indexes = primaryIndexes;
        java.util.List<byte[]> metaChildren = new java.util.ArrayList<>(java.util.List.of(
                fullBox("pitm", 0, new byte[] {0, 1}),
                fullBox("iinf", 0, iinfChildren),
                box("iprp", box("ipco", properties), ipma(1, indexes))));
        if (iref != null) {
            metaChildren.add(iref);
        }
        return concat(ftyp(major, "mif1", major), fullBox("meta", 0, metaChildren.toArray(byte[][]::new)));
    }

    public static byte[] avif(String major, int width, int height, byte[]... extraInfe) {
        return avif(major, new byte[][] {ispe(width, height)}, null, extraInfe);
    }

    /** iref versión 0 con una referencia de tipo `type` desde el ítem `from` hacia el ítem 1. */
    public static byte[] iref(String type, int from) {
        return fullBox("iref", 0, box(type, ByteBuffer.allocate(6).putShort((short) from).putShort((short) 1)
                .putShort((short) 1).array()));
    }
}
