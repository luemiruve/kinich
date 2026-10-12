package com.kinich.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.shared.error.ApiProblemException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemNormalizerTest {

    private static final UUID MEDIA = UUID.randomUUID();
    private static final ItemRules DISCIPLINES = ItemSectionPolicy.rules(SectionKey.DISCIPLINES);
    private static final ItemRules CLOSING = ItemSectionPolicy.rules(SectionKey.CLOSING_CTA);

    @Test
    void trimsAndTurnsBlankOptionalsIntoNull() {
        ItemValues values = ItemNormalizer.normalize(DISCIPLINES, MEDIA, "  Mesa al aire libre ", null, "  ",
                " Eventos ", "\n");
        assertThat(values.imageAlt()).isEqualTo("Mesa al aire libre");
        assertThat(values.eyebrow()).isNull();
        assertThat(values.title()).isEqualTo("Eventos");
        assertThat(values.description()).isNull();
    }

    @Test
    void requiresImageAltAndTheSectionsRequiredFields() {
        assertThatThrownBy(() -> ItemNormalizer.normalize(DISCIPLINES, null, " ", null, null, null, null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors())
                        .containsKeys("mediaId", "imageAlt", "title"));
    }

    @Test
    void rejectsFieldsTheSectionDoesNotAllow() {
        assertThatThrownBy(() -> ItemNormalizer.normalize(CLOSING, MEDIA, "alt", UUID.randomUUID(), null, "Título", null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors())
                        .containsKeys("categoryId", "title"));
    }

    @Test
    void rejectsControlCharactersWithoutEchoingThem() {
        assertThatThrownBy(() -> ItemNormalizer.normalize(DISCIPLINES, MEDIA, "alt\u0007", null, null, "T", null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors().get("imageAlt"))
                        .doesNotContain("\u0007"));
    }
}
