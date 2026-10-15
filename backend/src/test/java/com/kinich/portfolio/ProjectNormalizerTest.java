package com.kinich.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.content.CropInput;
import com.kinich.shared.error.ApiProblemException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectNormalizerTest {

    private static final UUID MEDIA = UUID.randomUUID();

    private static ProjectImageRequest image() {
        return new ProjectImageRequest(MEDIA, "Mesa", null);
    }

    private static ProjectRequest request(String slug, String title, String eyebrow, String description,
                                          CoverRequest cover, List<ProjectImageRequest> images) {
        return new ProjectRequest(slug, title, eyebrow, null, description, cover, images, null);
    }

    private static ProjectRequest valid(String title) {
        return request("boda-2024", title, null, null, new CoverRequest(MEDIA, "Portada", null), List.of(image()));
    }

    private static void assertErrors(ProjectRequest request, String... keys) {
        assertThatThrownBy(() -> ProjectNormalizer.normalize(request))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getFieldErrors()).containsKeys(keys));
    }

    @Test
    void normalizesAValidProject() {
        ProjectValues values = ProjectNormalizer.normalize(new ProjectRequest(" boda-2024 ", " Boda ", "  ",
                UUID.randomUUID(), "Texto", new CoverRequest(MEDIA, " Portada ", new CropInput(0.1, 0.2, 0.5, 0.625)),
                List.of(new ProjectImageRequest(MEDIA, "Mesa", " pie ")), 3L));
        assertThat(values.slug()).isEqualTo("boda-2024");
        assertThat(values.title()).isEqualTo("Boda");
        assertThat(values.eyebrow()).isNull();
        assertThat(values.coverAlt()).isEqualTo("Portada");
        assertThat(values.coverCrop().width()).isEqualTo(0.5);
        assertThat(values.images()).hasSize(1);
        assertThat(values.images().get(0).caption()).isEqualTo("pie");
    }

    @Test
    void requiresTitle() {
        assertErrors(valid(null), "title");
        assertErrors(valid("   "), "title");
    }

    @Test
    void rejectsLineBreaksInSingleLineFields() {
        assertErrors(valid("Boda\nen la playa"), "title");
    }

    @Test
    void rejectsInvalidSlugs() {
        assertErrors(request("Boda-2024", "T", null, null, new CoverRequest(MEDIA, "a", null), List.of(image())), "slug");
        assertErrors(request("a".repeat(81), "T", null, null, new CoverRequest(MEDIA, "a", null), List.of(image())), "slug");
        assertErrors(request(" ", "T", null, null, new CoverRequest(MEDIA, "a", null), List.of(image())), "slug");
        assertErrors(request(null, "T", null, null, new CoverRequest(MEDIA, "a", null), List.of(image())), "slug");
    }

    @Test
    void limitsEyebrowAndDescription() {
        assertErrors(request("s", "T", "e".repeat(121), null, new CoverRequest(MEDIA, "a", null), List.of(image())),
                "eyebrow");
        assertErrors(request("s", "T", null, "d".repeat(2001), new CoverRequest(MEDIA, "a", null), List.of(image())),
                "description");
    }

    @Test
    void cleansControlCharactersOrRejectsThemWithoutEchoing() {
        ProjectValues values = ProjectNormalizer.normalize(request("s", "T", null, " uno\r\ndos ",
                new CoverRequest(MEDIA, "a", null), List.of(image())));
        assertThat(values.description()).isEqualTo("uno\ndos");
        assertThatThrownBy(() -> ProjectNormalizer.normalize(request("s", "T", null, "x\u0007",
                new CoverRequest(MEDIA, "a", null), List.of(image()))))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getFieldErrors().get("description")).doesNotContain("\u0007"));
    }

    @Test
    void requiresAtLeastOneImage() {
        assertErrors(request("s", "T", null, null, new CoverRequest(MEDIA, "a", null), List.of()), "images");
        assertErrors(request("s", "T", null, null, new CoverRequest(MEDIA, "a", null), null), "images");
    }

    @Test
    void rejectsMoreThanSixtyImages() {
        List<ProjectImageRequest> images = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            images.add(image());
        }
        assertThatThrownBy(() -> ProjectNormalizer.normalize(request("s", "T", null, null,
                new CoverRequest(MEDIA, "a", null), images)))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getCode()).isEqualTo(com.kinich.shared.error.ErrorCode.MAX_ITEMS_REACHED));
    }

    @Test
    void acceptsSixtyImagesAndDuplicatedMedia() {
        List<ProjectImageRequest> images = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            images.add(image());
        }
        assertThat(ProjectNormalizer.normalize(request("s", "T", null, null, new CoverRequest(MEDIA, "a", null), images))
                .images()).hasSize(60);
    }

    @Test
    void reportsImageAndCoverErrorsByIndexedField() {
        List<ProjectImageRequest> images = List.of(new ProjectImageRequest(MEDIA, "a", "c".repeat(301)), image(),
                new ProjectImageRequest(MEDIA, "  ", null));
        assertErrors(request("s", "T", null, null, new CoverRequest(MEDIA, "a", null), images),
                "images[0].caption", "images[2].alt");
        assertErrors(request("s", "T", null, null, new CoverRequest(MEDIA, null, null), List.of(image())), "cover.alt");
    }

    @Test
    void collectsAllErrorsAtOnce() {
        assertThatThrownBy(() -> ProjectNormalizer.normalize(request("Mal Slug", " ", "e".repeat(121), null,
                new CoverRequest(MEDIA, null, null), List.of())))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors())
                        .containsKeys("slug", "title", "eyebrow", "cover.alt", "images"));
    }
}
