package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AvifInspectorTest {

    private final AvifInspector inspector = new AvifInspector();

    @Test
    void readsPrimaryItemDimensionsAndKeepsBytes() {
        byte[] avif = ImageFixtures.avif("avif", 1600, 900);
        ImageInspection result = inspector.process(avif);
        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(900);
        assertThat(result.content()).isEqualTo(avif);
    }

    @Test
    void usesThePrimaryItemsIspeNotTheLargestOne() {
        // ipco: [ispe 400×300, ispe 9000×9000 de otro ítem]; al primario solo se le asocia la propiedad 1.
        byte[] avif = ImageFixtures.avif("avif",
                new byte[][] {ImageFixtures.ispe(400, 300), ImageFixtures.ispe(9000, 9000)}, new int[] {1}, null);
        ImageInspection result = inspector.process(avif);
        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(300);
    }

    @Test
    void rejectsExifXmpAndThumbnails() {
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avif", 10, 10, ImageFixtures.infe(2, "Exif", null))))
                .hasMessage(InvalidImageException.metadata().getMessage());
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avif", 10, 10,
                ImageFixtures.infe(3, "mime", "application/rdf+xml"))))
                .hasMessage(InvalidImageException.metadata().getMessage());
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avif",
                new byte[][] {ImageFixtures.ispe(10, 10)}, ImageFixtures.iref("thmb", 2), ImageFixtures.infe(2, "av01", null))))
                .hasMessage(InvalidImageException.metadata().getMessage());
    }

    @Test
    void rejectsPendingRotation() { // irot
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avif",
                new byte[][] {ImageFixtures.ispe(10, 10), ImageFixtures.irot(1)}, null)))
                .hasMessage(InvalidImageException.rotated().getMessage());
        assertThat(inspector.process(ImageFixtures.avif("avif",
                new byte[][] {ImageFixtures.ispe(10, 10), ImageFixtures.irot(0)}, null)).width()).isEqualTo(10);
    }

    @Test
    void rejectsImageSequences() {
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avis", 10, 10)))
                .hasMessage(InvalidImageException.animated().getMessage());
        byte[] withMoov = ImageFixtures.concat(ImageFixtures.avif("avif", 10, 10), ImageFixtures.box("moov"));
        assertThatThrownBy(() -> inspector.process(withMoov)).hasMessage(InvalidImageException.animated().getMessage());
    }

    @Test
    void rejectsMissingStructuresAndLyingBoxSizes() {
        assertThatThrownBy(() -> inspector.process(ImageFixtures.ftyp("avif", "mif1")))
                .isInstanceOf(InvalidImageException.class);
        byte[] lying = ImageFixtures.avif("avif", 10, 10);
        lying[3] = (byte) 0x7F; // tamaño de ftyp mayor que el archivo
        assertThatThrownBy(() -> inspector.process(lying)).isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> inspector.process(ImageFixtures.avif("avif", new byte[][] {ImageFixtures.irot(0)}, null)))
                .isInstanceOf(InvalidImageException.class); // sin ispe en el ítem primario
    }

    @Test
    void realAvifFromSipsIsAccepted() {
        ImageInspection result = inspector.process(ImageFixtures.resource("plain.avif"));
        assertThat(result.width()).isEqualTo(16);
        assertThat(result.height()).isEqualTo(12);
    }
}
