package com.kinich.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.content.ContentItemService.ItemInput;
import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.error.ErrorCode;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaTestData;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ItemPublicationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContentItemService items;
    @Autowired private ContentCategoryService categories;
    @Autowired private SectionPublicationService publication;
    @Autowired private SectionRepository sections;
    @Autowired private PublicPageService publicPage;
    @Autowired private AdminPageService adminPage;
    @Autowired private UserRepository users;

    private UUID actor;
    private UUID media;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        actor = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        media = MediaTestData.readyImage(jdbc, 1600, 1067);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    private long revision(String slug) {
        return sections.findAll().stream().filter(s -> s.getSectionKey().slug().equals(slug)).findFirst().orElseThrow()
                .getDraftRevision();
    }

    private PublicItem onlyPublicItem(String slug) {
        return publicPage.load().sections().stream().filter(s -> s.sectionKey().equals(slug)).findFirst().orElseThrow()
                .items().get(0);
    }

    @Test
    void publishingCopiesVisibleItemsWithTheCategoryLabelAndPublicUrl() {
        UUID category = categories.create("Cobertura social", actor).getId();
        items.create("disciplines", new ItemInput(media, "Mesa", category, null, "Eventos", "Descripción", null, null), actor);
        assertThat(publicPage.load().sections().stream().filter(s -> s.sectionKey().equals("disciplines"))
                .findFirst().orElseThrow().items()).isEmpty();

        publication.publish(PageKey.HOME, "disciplines", revision("disciplines"), actor);

        PublicItem item = onlyPublicItem("disciplines");
        assertThat(item.category()).isEqualTo("Cobertura social");
        assertThat(item.image().fallback().type()).isEqualTo("image/jpeg");
        assertThat(item.image().fallback().candidates()).allSatisfy(candidate ->
                assertThat(candidate.url()).startsWith("/api/public/media/files/"));
        assertThat(item.image().sources()).extracting(source -> source.type()).containsExactly("image/avif");
        assertThat(item.image().width()).isEqualTo(1600);
        assertThat(item.image().crop()).isNotNull(); // Disciplinas: rectángulo 4:5 (centrado por defecto)
        assertThat(item.image().mediaPosition()).isNull();
        assertThat(item.image().alt()).isEqualTo("Mesa");
    }

    @Test
    void hiddenItemsAreNotPublishedAndStaleRevisionConflicts() {
        ContentItem item = items.create("featured-selection", new ItemInput(media, "alt", null, null, "T", null, null, null), actor);
        items.changeVisibility("featured-selection", item.getId(), true, item.getVersion(), actor);
        publication.publish(PageKey.HOME, "featured-selection", revision("featured-selection"), actor);
        assertThat(publicPage.load().sections().stream().filter(s -> s.sectionKey().equals("featured-selection"))
                .findFirst().orElseThrow().items()).isEmpty();
        assertThatThrownBy(() -> publication.publish(PageKey.HOME, "featured-selection", 0, actor))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }

    @Test
    void adminViewDerivesStatusAndModifiedIncludingTheImage() {
        ContentItem item = items.create("hero", new ItemInput(media, "alt", null, "Edición 2024", null, null, null, null), actor);
        publication.publish(PageKey.HOME, "hero", revision("hero"), actor);
        UUID other = MediaTestData.readyImage(jdbc, 4, 3);
        ContentItem current = items.update("hero", item.getId(), new ItemInput(other, "alt", null, "Edición 2024", null, null, null, null),
                item.getVersion(), actor);

        ItemResponse response = adminPage.load().sections().stream().filter(s -> s.sectionKey().equals("hero"))
                .findFirst().orElseThrow().items().get(0);
        assertThat(response.status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(response.modified()).isTrue();
        assertThat(response.image().fallback().candidates()).allSatisfy(candidate ->
                assertThat(candidate.url()).startsWith("/api/admin/media/files/"));
        assertThat(response.version()).isEqualTo(current.getVersion());
    }

    @Test
    void textSectionsStillPublishAsBefore() {
        jdbc.update("INSERT INTO text_contents (id, section_id, body, position, created_at, updated_at) "
                + "VALUES (?, ?, 'Nota', 1, now(), now())", UUID.randomUUID(), ContentTestData.EDITORIAL_NOTE_ID);
        jdbc.update("UPDATE sections SET draft_revision = 1 WHERE id = ?", ContentTestData.EDITORIAL_NOTE_ID);
        publication.publish(PageKey.HOME, "editorial-note", 1, actor);
        assertThat(publicPage.load().sections().stream().filter(s -> s.sectionKey().equals("editorial-note"))
                .findFirst().orElseThrow().texts()).hasSize(1);
    }

    @Test
    void changingTheDraftFramingDoesNotTouchWhatIsPublishedUntilPublishing() {
        Crop left = new Crop(0, 0, 853.6 / 1600.0, 1.0); // 4:5 sobre 1600 × 1067
        Crop right = new Crop(1 - 853.6 / 1600.0, 0, 853.6 / 1600.0, 1.0);
        ContentItem item = items.create("disciplines", new ItemInput(media, "Mesa", null, null, "Eventos", null, left, null),
                actor);
        publication.publish(PageKey.HOME, "disciplines", revision("disciplines"), actor);

        ContentItem moved = items.update("disciplines", item.getId(),
                new ItemInput(media, "Mesa", null, null, "Eventos", null, right, null), item.getVersion(), actor);

        assertThat(onlyPublicItem("disciplines").image().crop()).isEqualTo(left);
        ItemResponse draft = adminPage.load().sections().stream().filter(s -> s.sectionKey().equals("disciplines"))
                .findFirst().orElseThrow().items().get(0);
        assertThat(draft.modified()).isTrue();
        assertThat(draft.image().crop()).isEqualTo(right);
        assertThat(moved.getMediaId()).isEqualTo(media);

        publication.publish(PageKey.HOME, "disciplines", revision("disciplines"), actor);
        assertThat(onlyPublicItem("disciplines").image().crop()).isEqualTo(right);
    }

    @Test
    void heroAndClosingPublishTheirFocalPoint() {
        items.create("hero", new ItemInput(media, "alt", null, null, null, null, null, new FocalPoint(0.2, 0.7)), actor);
        publication.publish(PageKey.HOME, "hero", revision("hero"), actor);
        PublicItem hero = onlyPublicItem("hero");
        assertThat(hero.image().mediaPosition()).isEqualTo(new FocalPoint(0.2, 0.7));
        assertThat(hero.image().crop()).isNull();
    }
}
