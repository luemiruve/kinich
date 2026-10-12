package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageInspectorTest {

    private final ImageInspector inspector = new ImageInspector();

    @Test
    void dispatchesByRealFormat() {
        assertThat(inspector.inspect(ImageFixtures.png(3, 3)).format()).isEqualTo(MediaFormat.PNG);
        assertThat(inspector.inspect(ImageFixtures.jpeg(3, 3)).format()).isEqualTo(MediaFormat.JPEG);
        assertThat(inspector.inspect(ImageFixtures.resource("lossless.webp")).format()).isEqualTo(MediaFormat.WEBP);
        assertThat(inspector.inspect(ImageFixtures.avif("avif", 5, 5)).format()).isEqualTo(MediaFormat.AVIF);
    }

    @Test
    void enforces8000PixelsPerSide() {
        assertThat(inspector.inspect(ImageFixtures.avif("avif", 8000, 8000)).width()).isEqualTo(8000);
        assertThatThrownBy(() -> inspector.inspect(ImageFixtures.avif("avif", 8001, 10)))
                .hasMessage(InvalidImageException.tooLarge().getMessage());
        assertThatThrownBy(() -> inspector.inspect(ImageFixtures.avif("avif", 10, 8001)))
                .hasMessage(InvalidImageException.tooLarge().getMessage());
    }

    @Test
    void rejectsSvgEvenWithAnImageName() {
        assertThatThrownBy(() -> inspector.inspect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .hasMessage(InvalidImageException.unsupported().getMessage());
    }
}
