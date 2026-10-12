package com.kinich.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.kinich.shared.error.ApiProblemException;
import org.junit.jupiter.api.Test;

class ItemFramingTest {

    @Test
    void sectionsUseTheApprovedFraming() {
        assertThat(ItemSectionPolicy.rules(SectionKey.DISCIPLINES).framing()).isEqualTo(ItemFraming.CROP_4_5);
        assertThat(ItemSectionPolicy.rules(SectionKey.FEATURED_SELECTION).framing()).isEqualTo(ItemFraming.CROP_4_5);
        assertThat(ItemSectionPolicy.rules(SectionKey.HERO).framing()).isEqualTo(ItemFraming.FOCAL_POINT);
        assertThat(ItemSectionPolicy.rules(SectionKey.CLOSING_CTA).framing()).isEqualTo(ItemFraming.FOCAL_POINT);
    }

    @Test
    void theDefaultCropIsTheLargestCentered4by5Rectangle() {
        Crop landscape = ItemFraming.CROP_4_5.resolve(null, null, 6000, 4000).crop();
        assertThat(landscape.height()).isEqualTo(1.0);
        assertThat(landscape.width()).isCloseTo(3200 / 6000.0, within(1e-9));
        assertThat(landscape.x()).isCloseTo((1 - 3200 / 6000.0) / 2, within(1e-9));
        Crop portrait = ItemFraming.CROP_4_5.resolve(null, null, 3000, 6000).crop();
        assertThat(portrait.width()).isEqualTo(1.0);
        assertThat(portrait.height()).isCloseTo(3750 / 6000.0, within(1e-9));
        assertThat(portrait.y()).isCloseTo((1 - 3750 / 6000.0) / 2, within(1e-9));
    }

    @Test
    void aGivenCropMustBe4by5OnTheOriginal() {
        Crop ok = new Crop(0.1, 0.0, 3200 / 6000.0, 1.0);
        assertThat(ItemFraming.CROP_4_5.resolve(ok, null, 6000, 4000).crop()).isEqualTo(ok);
        assertThatThrownBy(() -> ItemFraming.CROP_4_5.resolve(new Crop(0, 0, 0.5, 0.5), null, 6000, 4000))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("crop"));
    }

    @Test
    void theDefaultFocalPointIsTheCenterAndAGivenOneIsKept() {
        assertThat(ItemFraming.FOCAL_POINT.resolve(null, null, 100, 100).mediaPosition()).isEqualTo(new FocalPoint(0.5, 0.5));
        assertThat(ItemFraming.FOCAL_POINT.resolve(null, new FocalPoint(0.2, 0.8), 100, 100).mediaPosition())
                .isEqualTo(new FocalPoint(0.2, 0.8));
    }

    @Test
    void theWrongKindOfFramingForTheSectionIsRejected() {
        assertThatThrownBy(() -> ItemFraming.FOCAL_POINT.resolve(new Crop(0, 0, 1, 1), null, 100, 100))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("crop"));
        assertThatThrownBy(() -> ItemFraming.CROP_4_5.resolve(null, new FocalPoint(0.5, 0.5), 100, 125))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getFieldErrors()).containsKey("mediaPosition"));
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThatThrownBy(() -> ItemFraming.CROP_4_5.resolve(new Crop(0.6, 0, 0.5, 0.5), null, 100, 125))
                .isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(() -> ItemFraming.FOCAL_POINT.resolve(null, new FocalPoint(1.2, 0.5), 100, 100))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getFieldErrors()).containsKey("mediaPosition"));
        assertThatThrownBy(() -> ItemFraming.CROP_4_5.resolve(new Crop(0, 0, 0, 0), null, 100, 125))
                .isInstanceOf(ApiProblemException.class);
    }
}
