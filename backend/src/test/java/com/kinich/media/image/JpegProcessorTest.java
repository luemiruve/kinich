package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class JpegProcessorTest {

    private final JpegProcessor processor = new JpegProcessor();

    @Test
    void readsDimensionsAndKeepsAValidJpeg() throws Exception {
        ImageInspection result = processor.process(ImageFixtures.jpeg(40, 30));
        assertThat(result.format()).isEqualTo(MediaFormat.JPEG);
        assertThat(result.width()).isEqualTo(40);
        assertThat(result.height()).isEqualTo(30);
        assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(result.content())).getWidth()).isEqualTo(40);
    }

    @Test
    void removesExifWithGpsXmpIptcAndComments() {
        byte[] jpeg = ImageFixtures.jpeg(8, 8);
        jpeg = ImageFixtures.withJpegSegment(jpeg, 0xE1, ImageFixtures.exifApp1Payload(1, true));
        jpeg = ImageFixtures.withJpegSegment(jpeg, 0xE1,
                "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>autor</x:xmpmeta>".getBytes(StandardCharsets.US_ASCII));
        jpeg = ImageFixtures.withJpegSegment(jpeg, 0xED, "Photoshop 3.0\0IPTC".getBytes(StandardCharsets.US_ASCII));
        jpeg = ImageFixtures.withJpegSegment(jpeg, 0xFE, "comentario privado".getBytes(StandardCharsets.US_ASCII));

        byte[] clean = processor.process(jpeg).content();

        assertThat(ImageFixtures.contains(clean, ImageFixtures.GPS_MARKER)).isFalse();
        assertThat(ImageFixtures.contains(clean, "Exif".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageFixtures.contains(clean, "xmpmeta".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageFixtures.contains(clean, "IPTC".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageFixtures.contains(clean, "comentario".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void keepsIccProfile() {
        byte[] icc = "ICC_PROFILE\0\1\1perfil".getBytes(StandardCharsets.US_ASCII);
        byte[] clean = processor.process(ImageFixtures.withJpegSegment(ImageFixtures.jpeg(8, 8), 0xE2, icc)).content();
        assertThat(ImageFixtures.contains(clean, icc)).isTrue();
    }

    @Test
    void rewritesJfifWithoutThumbnailAndDropsJfxx() {
        byte[] jfifWithThumb = new byte[14 + 6];
        System.arraycopy("JFIF\0".getBytes(StandardCharsets.US_ASCII), 0, jfifWithThumb, 0, 5);
        jfifWithThumb[5] = 1;
        jfifWithThumb[6] = 2;
        jfifWithThumb[12] = 2; // miniatura 2 × 1 RGB
        jfifWithThumb[13] = 1;
        System.arraycopy("THUMB!".getBytes(StandardCharsets.US_ASCII), 0, jfifWithThumb, 14, 6);
        byte[] jfxx = "JFXX\0\u0010miniatura".getBytes(StandardCharsets.US_ASCII);
        byte[] jpeg = ImageFixtures.withJpegSegment(ImageFixtures.jpeg(8, 8), 0xE0, jfifWithThumb);
        jpeg = ImageFixtures.withJpegSegment(jpeg, 0xE0, jfxx);

        byte[] clean = processor.process(jpeg).content();

        assertThat(ImageFixtures.contains(clean, "THUMB!".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageFixtures.contains(clean, "JFXX".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageFixtures.contains(clean, "JFIF".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }

    @Test
    void rejectsFrameTypesBrowsersDoNotShow() {
        byte[] jpeg = ImageFixtures.jpeg(8, 8);
        for (int i = 0; i < jpeg.length - 1; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xC0) {
                jpeg[i + 1] = (byte) 0xC3; // SOF3 (sin pérdida)
                break;
            }
        }
        byte[] lossless = jpeg;
        assertThatThrownBy(() -> processor.process(lossless)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void discardsEverythingAfterEndOfImage() {
        byte[] polyglot = ImageFixtures.concat(ImageFixtures.jpeg(8, 8),
                "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII));
        byte[] clean = processor.process(polyglot).content();
        assertThat(clean[clean.length - 2] & 0xFF).isEqualTo(0xFF);
        assertThat(clean[clean.length - 1] & 0xFF).isEqualTo(0xD9);
        assertThat(ImageFixtures.contains(clean, "script".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void rejectsPendingRotation() {
        byte[] rotated = ImageFixtures.withJpegSegment(ImageFixtures.jpeg(8, 8), 0xE1, ImageFixtures.exifApp1Payload(6, false));
        assertThatThrownBy(() -> processor.process(rotated)).isInstanceOf(InvalidImageException.class)
                .hasMessage(InvalidImageException.rotated().getMessage());
    }

    @Test
    void rejectsTruncatedAndCorrupt() {
        byte[] jpeg = ImageFixtures.jpeg(8, 8);
        assertThatThrownBy(() -> processor.process(Arrays.copyOf(jpeg, jpeg.length / 2)))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> processor.process(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .isInstanceOf(InvalidImageException.class);
        byte[] badLength = jpeg.clone();
        badLength[4] = 0x7F; // longitud del primer segmento fuera del archivo
        assertThatThrownBy(() -> processor.process(badLength)).isInstanceOf(InvalidImageException.class);
    }
}
