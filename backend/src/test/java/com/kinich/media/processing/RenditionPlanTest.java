package com.kinich.media.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.kinich.media.image.MediaFormat;
import org.junit.jupiter.api.Test;

class RenditionPlanTest {

    @Test
    void usesTheFiveCandidateWidthsWithoutUpscaling() {
        assertThat(RenditionPlan.widthsFor(6000)).containsExactly(480, 768, 1280, 1920, 2560);
        assertThat(RenditionPlan.widthsFor(2560)).containsExactly(480, 768, 1280, 1920, 2560);
        assertThat(RenditionPlan.widthsFor(1500)).containsExactly(480, 768, 1280);
        assertThat(RenditionPlan.widthsFor(480)).containsExactly(480);
    }

    @Test
    void anOriginalNarrowerThanTheSmallestCandidateKeepsItsWidth() {
        assertThat(RenditionPlan.widthsFor(300)).containsExactly(300);
    }

    @Test
    void avifFirstThenJpegAndNeverWebp() {
        assertThat(RenditionPlan.formatsFor(false)).containsExactly(MediaFormat.AVIF, MediaFormat.JPEG);
    }

    @Test
    void transparentImagesFallBackToPngInsteadOfJpeg() {
        assertThat(RenditionPlan.formatsFor(true)).containsExactly(MediaFormat.AVIF, MediaFormat.PNG);
    }
}
