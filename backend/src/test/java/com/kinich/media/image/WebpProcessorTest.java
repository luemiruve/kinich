package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebpProcessorTest {

    private final WebpProcessor processor = new WebpProcessor();

    @Test
    void readsLossyAndLosslessDimensions() {
        assertThat(processor.process(ImageFixtures.resource("lossy.webp"))).satisfies(r -> {
            assertThat(r.width()).isEqualTo(16);
            assertThat(r.height()).isEqualTo(12);
        });
        assertThat(processor.process(ImageFixtures.resource("lossless.webp"))).satisfies(r -> {
            assertThat(r.width()).isEqualTo(16);
            assertThat(r.height()).isEqualTo(12);
        });
    }

    @Test
    void removesExifAndXmpAndClearsTheirFlags() {
        byte[] webp = ImageFixtures.webpExtended(ImageFixtures.resource("lossy.webp"), 16, 12, 0x08 | 0x04,
                ImageFixtures.riffChunk("EXIF", ImageFixtures.exifTiff(1, true)),
                ImageFixtures.riffChunk("XMP ", "<x:xmpmeta>autor</x:xmpmeta>".getBytes(StandardCharsets.US_ASCII)));
        ImageInspection result = processor.process(webp);
        byte[] clean = result.content();
        assertThat(ImageFixtures.contains(clean, ImageFixtures.GPS_MARKER)).isFalse();
        assertThat(ImageFixtures.contains(clean, "xmpmeta".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(clean[20] & 0x0C).isZero(); // banderas EXIF y XMP de VP8X
        assertThat(Bytes.u32le(clean, 4)).isEqualTo(clean.length - 8L); // tamaño RIFF recalculado
        assertThat(result.width()).isEqualTo(16);
    }

    @Test
    void rejectsAnimationRotationAndBadSizes() {
        byte[] simple = ImageFixtures.resource("lossy.webp");
        assertThatThrownBy(() -> processor.process(ImageFixtures.webpExtended(simple, 16, 12, 0x02)))
                .hasMessage(InvalidImageException.animated().getMessage());
        assertThatThrownBy(() -> processor.process(ImageFixtures.webpExtended(simple, 16, 12, 0x08,
                ImageFixtures.riffChunk("EXIF", ImageFixtures.exifTiff(3, false)))))
                .hasMessage(InvalidImageException.rotated().getMessage());
        byte[] lying = simple.clone();
        lying[4] = (byte) 0xFF; lying[5] = (byte) 0xFF; // tamaño RIFF mayor que el archivo
        assertThatThrownBy(() -> processor.process(lying)).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void discardsBytesBeyondTheRiffSize() {
        byte[] padded = ImageFixtures.concat(ImageFixtures.resource("lossy.webp"), "<html>".getBytes(StandardCharsets.US_ASCII));
        assertThat(ImageFixtures.contains(processor.process(padded).content(),
                "<html>".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }
}
