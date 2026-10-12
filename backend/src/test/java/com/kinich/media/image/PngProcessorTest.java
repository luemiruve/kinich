package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PngProcessorTest {

    private final PngProcessor processor = new PngProcessor();

    @Test
    void readsDimensionsAndStaysDecodable() throws Exception {
        ImageInspection result = processor.process(ImageFixtures.png(50, 20));
        assertThat(result.width()).isEqualTo(50);
        assertThat(result.height()).isEqualTo(20);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.content())).getHeight()).isEqualTo(20);
    }

    @Test
    void removesTextExifAndTimeChunks() {
        byte[] png = ImageFixtures.png(4, 4);
        png = ImageFixtures.withPngChunk(png, "tEXt", "Author\0Autor de prueba".getBytes(StandardCharsets.ISO_8859_1));
        png = ImageFixtures.withPngChunk(png, "iTXt", "XML:com.adobe.xmp\0\0\0\0\0<x:xmpmeta/>".getBytes(StandardCharsets.ISO_8859_1));
        png = ImageFixtures.withPngChunk(png, "eXIf", ImageFixtures.exifTiff(1, true));
        png = ImageFixtures.withPngChunk(png, "tIME", new byte[7]);
        byte[] clean = processor.process(png).content();
        for (String gone : new String[] {"tEXt", "iTXt", "eXIf", "tIME", "Autor de prueba", "xmpmeta"}) {
            assertThat(ImageFixtures.contains(clean, gone.getBytes(StandardCharsets.ISO_8859_1))).as(gone).isFalse();
        }
        assertThat(ImageFixtures.contains(clean, ImageFixtures.GPS_MARKER)).isFalse();
    }

    @Test
    void discardsBytesAfterIend() {
        byte[] polyglot = ImageFixtures.concat(ImageFixtures.png(4, 4), "<html>".getBytes(StandardCharsets.US_ASCII));
        assertThat(ImageFixtures.contains(processor.process(polyglot).content(),
                "<html>".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void rejectsApngRotationBadCrcAndUnknownCriticalChunks() {
        assertThatThrownBy(() -> processor.process(ImageFixtures.withPngChunk(ImageFixtures.png(4, 4), "acTL", new byte[8])))
                .hasMessage(InvalidImageException.animated().getMessage());
        assertThatThrownBy(() -> processor.process(ImageFixtures.withPngChunk(ImageFixtures.png(4, 4), "eXIf",
                ImageFixtures.exifTiff(8, false)))).hasMessage(InvalidImageException.rotated().getMessage());
        byte[] badCrc = ImageFixtures.png(4, 4);
        badCrc[8 + 8 + 13] ^= 0x01;
        assertThatThrownBy(() -> processor.process(badCrc)).isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> processor.process(ImageFixtures.withPngChunk(ImageFixtures.png(4, 4), "ZZZZ", new byte[1])))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void rejectsMissingIend() {
        byte[] png = ImageFixtures.png(4, 4);
        assertThatThrownBy(() -> processor.process(java.util.Arrays.copyOf(png, png.length - 12)))
                .isInstanceOf(InvalidImageException.class);
    }
}
