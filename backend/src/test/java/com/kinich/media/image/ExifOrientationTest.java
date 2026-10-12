package com.kinich.media.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExifOrientationTest {

    @Test
    void readsOrientation() {
        byte[] tiff = ImageFixtures.exifTiff(6, false);
        assertThat(ExifOrientation.read(tiff, 0, tiff.length)).isEqualTo(6);
    }

    @Test
    void missingTagMeansNormal() {
        byte[] tiff = ImageFixtures.exifTiff(1, false);
        tiff[11] = 0x01; // la etiqueta deja de ser 0x0112
        assertThat(ExifOrientation.read(tiff, 0, tiff.length)).isEqualTo(1);
    }

    @Test
    void offsetsOutsideTheBlockAreMalformed() {
        byte[] tiff = ImageFixtures.exifTiff(1, false);
        tiff[7] = 0x7F; // IFD0 fuera del bloque
        assertThatThrownBy(() -> ExifOrientation.read(tiff, 0, tiff.length)).isInstanceOf(InvalidImageException.class);
    }
}
