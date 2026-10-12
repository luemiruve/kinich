package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageFormatDetectorTest {

    @Test
    void detectsByMagicBytesNotByName() {
        assertThat(ImageFormatDetector.detect(ImageFixtures.jpeg(2, 2))).contains(MediaFormat.JPEG);
        assertThat(ImageFormatDetector.detect(ImageFixtures.png(2, 2))).contains(MediaFormat.PNG);
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertThat(ImageFormatDetector.detect(webp)).contains(MediaFormat.WEBP);
        byte[] avif = {0, 0, 0, 20, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f', 0, 0, 0, 0, 'm', 'i', 'f', '1'};
        assertThat(ImageFormatDetector.detect(avif)).contains(MediaFormat.AVIF);
    }

    @Test
    void rejectsSvgHtmlGifHeicAndEmpty() {
        assertThat(ImageFormatDetector.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormatDetector.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormatDetector.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        byte[] heic = {0, 0, 0, 20, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0, 'm', 'i', 'f', '1'};
        assertThat(ImageFormatDetector.detect(heic)).isEmpty();
        assertThat(ImageFormatDetector.detect(new byte[0])).isEmpty();
    }
}
