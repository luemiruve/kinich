package com.kinich.content;

import static com.kinich.support.ContentTestData.VALUES_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.error.ErrorCode;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** draft → preview → publication transaction → published snapshot → public API. */
@IntegrationTest
class PublicationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TextContentService drafts;
    @Autowired private SectionPublicationService publication;
    @Autowired private AdminPageService adminPage;
    @Autowired private PublicPageService publicPage;
    @Autowired private SectionRepository sections;

    private UUID actor;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        actor = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void savingADraftDoesNotChangeThePublicPage() {
        drafts.create("values", null, null, "Borrador", actor);

        assertThat(publicTexts("values")).isEmpty();
    }

    @Test
    void publishingCopiesVisibleBlocksInDraftOrder() {
        drafts.create("values", null, null, "Uno", actor);
        TextContent hidden = drafts.create("values", null, null, "Oculto", actor);
        drafts.create("values", "Ey", "Tí", "Tres", actor);
        drafts.changeVisibility("values", hidden.getId(), true, hidden.getVersion(), actor);

        publication.publish(PageKey.HOME, "values", revision(), actor);

        assertThat(publicTexts("values")).extracting(PublicTextBlock::body).containsExactly("Uno", "Tres");
        assertThat(jdbc.queryForList("SELECT position FROM published_text_contents ORDER BY position", Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    void publishingRecordsWhoAndWhenAndClearsPendingChanges() {
        drafts.create("values", null, null, "Uno", actor);

        Section section = publication.publish(PageKey.HOME, "values", revision(), actor);

        assertThat(section.getPublishedRevision()).isEqualTo(section.getDraftRevision());
        assertThat(section.getPublishedBy()).isEqualTo(actor);
        assertThat(section.getPublishedAt()).isNotNull();
        assertThat(section.hasUnpublishedChanges()).isFalse();
    }

    @Test
    void aStaleRevisionConflictsAndKeepsThePreviousSnapshot() {
        drafts.create("values", null, null, "Publicado", actor);
        publication.publish(PageKey.HOME, "values", revision(), actor);
        long previewed = revision();
        drafts.create("values", null, null, "Añadido en otra pestaña", actor);

        assertThatThrownBy(() -> publication.publish(PageKey.HOME, "values", previewed, actor))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(publicTexts("values")).extracting(PublicTextBlock::body).containsExactly("Publicado");
    }

    @Test
    void deletingADraftKeepsItPublicUntilTheNextPublication() {
        TextContent block = drafts.create("values", null, null, "Uno", actor);
        publication.publish(PageKey.HOME, "values", revision(), actor);

        drafts.delete("values", block.getId(), block.getVersion(), actor);
        assertThat(publicTexts("values")).hasSize(1);

        publication.publish(PageKey.HOME, "values", revision(), actor);
        assertThat(publicTexts("values")).isEmpty();
    }

    @Test
    void statusesAreDerivedFromDraftAndSnapshot() {
        TextContent block = drafts.create("values", null, null, "Uno", actor);
        assertThat(onlyAdminBlock().status()).isEqualTo(ContentStatus.DRAFT);

        publication.publish(PageKey.HOME, "values", revision(), actor);
        assertThat(onlyAdminBlock().status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(onlyAdminBlock().modified()).isFalse();

        TextContent edited = drafts.update("values", block.getId(), null, null, "Editado", block.getVersion(), actor);
        assertThat(onlyAdminBlock().modified()).isTrue();
        assertThat(adminSection("values").hasUnpublishedChanges()).isTrue();

        drafts.changeVisibility("values", block.getId(), true, edited.getVersion(), actor);
        assertThat(onlyAdminBlock().status()).isEqualTo(ContentStatus.HIDDEN);
    }

    @Test
    void publicPageListsTheSixSectionsInCatalogOrderWithoutInternalFields() {
        PublicPageResponse page = publicPage.load();

        assertThat(page.pageKey()).isEqualTo("home");
        assertThat(page.sections()).extracting(PublicSectionResponse::sectionKey)
                .containsExactly("hero", "editorial-note", "disciplines", "values", "featured-selection", "closing-cta");
        assertThat(PublicTextBlock.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("id", "eyebrow", "title", "body");
    }

    @Test
    void adminPageMarksOnlyTextSectionsAsEditable() {
        AdminPageResponse page = adminPage.load();

        assertThat(page.sections()).filteredOn(AdminSectionResponse::textEditable)
                .extracting(AdminSectionResponse::sectionKey, AdminSectionResponse::maxBlocks)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("editorial-note", 1),
                        org.assertj.core.groups.Tuple.tuple("values", null));
    }

    @Test
    void concurrentPublicationsOfTheSameRevisionPublishOnce() throws Exception {
        drafts.create("values", null, null, "Uno", actor);
        drafts.create("values", null, null, "Dos", actor);
        long previewed = revision();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        publication.publish(PageKey.HOME, "values", previewed, actor);
                        return true;
                    } catch (ApiProblemException | ObjectOptimisticLockingFailureException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int published = 0;
            for (Future<Boolean> result : results) {
                published += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(published).isEqualTo(1);
            assertThat(publicTexts("values")).extracting(PublicTextBlock::body).containsExactly("Uno", "Dos");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM published_text_contents", Integer.class))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private long revision() {
        return sections.findById(VALUES_ID).orElseThrow().getDraftRevision();
    }

    private List<PublicTextBlock> publicTexts(String slug) {
        return publicPage.load().sections().stream().filter(s -> s.sectionKey().equals(slug)).findFirst()
                .orElseThrow().texts();
    }

    private AdminSectionResponse adminSection(String slug) {
        return adminPage.load().sections().stream().filter(s -> s.sectionKey().equals(slug)).findFirst().orElseThrow();
    }

    private TextBlockResponse onlyAdminBlock() {
        return adminSection("values").texts().getFirst();
    }
}
