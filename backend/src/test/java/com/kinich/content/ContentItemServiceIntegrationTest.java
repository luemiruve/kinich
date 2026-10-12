package com.kinich.content;

import static com.kinich.support.ContentTestData.DISCIPLINES_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.content.ContentItemService.ItemInput;
import com.kinich.shared.error.ApiProblemException;
import com.kinich.shared.error.ErrorCode;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaTestData;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@IntegrationTest
class ContentItemServiceIntegrationTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContentItemService service;
    @Autowired private ContentItemRepository items;
    @Autowired private SectionRepository sections;

    private UUID media;

    @BeforeEach
    void reset() {
        ContentTestData.reset(jdbc);
        media = MediaTestData.readyImage(jdbc, 4, 3);
    }

    @AfterEach
    void cleanUp() {
        ContentTestData.reset(jdbc);
    }

    private ItemInput card(String title) {
        return new ItemInput(media, "Texto alternativo", null, null, title, null, null, null);
    }

    private long revision(UUID sectionId) {
        return sections.findById(sectionId).orElseThrow().getDraftRevision();
    }

    @Test
    void createAppendsAtTheEndAndBumpsTheRevision() {
        service.create("disciplines", card("Uno"), ACTOR);
        ContentItem second = service.create("disciplines", card("Dos"), ACTOR);
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(revision(DISCIPLINES_ID)).isEqualTo(2);
    }

    @Test
    void disciplinesAcceptFourAndHeroAndClosingAcceptOne() {
        for (int i = 0; i < 4; i++) {
            service.create("disciplines", card("D" + i), ACTOR);
        }
        assertThatThrownBy(() -> service.create("disciplines", card("Quinta"), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MAX_ITEMS_REACHED));
        service.create("hero", new ItemInput(media, "alt", null, "Edición 2024", null, null, null, null), ACTOR);
        assertThatThrownBy(() -> service.create("hero", new ItemInput(media, "alt", null, null, null, null, null, null), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.MAX_ITEMS_REACHED));
        service.create("closing-cta", new ItemInput(media, "alt", null, null, null, null, null, null), ACTOR);
        assertThatThrownBy(() -> service.create("closing-cta", new ItemInput(media, "alt", null, null, null, null, null, null), ACTOR))
                .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void featuredSelectionHasNoMaximum() {
        for (int i = 0; i < 9; i++) {
            service.create("featured-selection", card("F" + i), ACTOR);
        }
        assertThat(items.countBySectionId(ContentTestData.FEATURED_ID)).isEqualTo(9);
    }

    @Test
    void unknownMediaOrCategoryIsAValidationError() {
        assertThatThrownBy(() -> service.create("disciplines", new ItemInput(UUID.randomUUID(), "alt", null, null, "T", null, null, null), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("mediaId"));
        assertThatThrownBy(() -> service.create("disciplines", new ItemInput(media, "alt", UUID.randomUUID(), null, "T", null, null, null), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("categoryId"));
    }

    @Test
    void textAndUnknownSectionsAreNotFound() {
        for (String slug : new String[] {"values", "editorial-note", "nope", ""}) {
            assertThatThrownBy(() -> service.create(slug, card("x"), ACTOR)).as(slug)
                    .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void anItemFromAnotherSectionIsNotFound() {
        ContentItem item = service.create("disciplines", card("D"), ACTOR);
        assertThatThrownBy(() -> service.update("featured-selection", item.getId(), card("x"), item.getVersion(), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void staleVersionAndRevisionConflict() {
        ContentItem item = service.create("disciplines", card("D"), ACTOR);
        service.update("disciplines", item.getId(), card("D2"), item.getVersion(), ACTOR);
        assertThatThrownBy(() -> service.update("disciplines", item.getId(), card("D3"), item.getVersion(), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThatThrownBy(() -> service.reorder("disciplines", 0, List.of(item.getId()), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }

    @Test
    void hideDeleteAndReorderKeepPositionsCompact() {
        ContentItem a = service.create("featured-selection", card("A"), ACTOR);
        ContentItem b = service.create("featured-selection", card("B"), ACTOR);
        ContentItem c = service.create("featured-selection", card("C"), ACTOR);
        service.changeVisibility("featured-selection", b.getId(), true, b.getVersion(), ACTOR);
        assertThat(items.findById(b.getId()).orElseThrow().isHidden()).isTrue();
        service.delete("featured-selection", a.getId(), a.getVersion(), ACTOR);
        assertThat(items.findBySectionIdOrderByPosition(ContentTestData.FEATURED_ID))
                .extracting(ContentItem::getId, ContentItem::getPosition)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(b.getId(), 1), org.assertj.core.groups.Tuple.tuple(c.getId(), 2));
        service.reorder("featured-selection", revision(ContentTestData.FEATURED_ID), List.of(c.getId(), b.getId()), ACTOR);
        assertThat(items.findBySectionIdOrderByPosition(ContentTestData.FEATURED_ID)).extracting(ContentItem::getId)
                .containsExactly(c.getId(), b.getId());
    }

    @Test
    void concurrentCreatesNeverExceedFourDisciplines() throws Exception {
        for (int i = 0; i < 3; i++) {
            service.create("disciplines", card("D" + i), ACTOR);
        }
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.create("disciplines", card("Concurrente"), ACTOR);
                        return true;
                    } catch (ApiProblemException | ObjectOptimisticLockingFailureException expected) {
                        // 409 VERSION_CONFLICT (concurrente) o MAX_ITEMS_REACHED (ya llena)
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (Future<Boolean> result : results) {
                created += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(created).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(items.countBySectionId(DISCIPLINES_ID)).isEqualTo(4);
    }

    private long filesOf(UUID asset) {
        return jdbc.queryForObject("SELECT count(*) FROM media_files WHERE media_asset_id = ?", Long.class, asset);
    }

    @Test
    void twoItemsCanUseTheSamePhotoWithDifferentCrops() {
        // media: 4 × 3 → un 4:5 ocupa todo el alto (ancho 2,4 px = 0,6).
        ContentItem left = service.create("disciplines",
                new ItemInput(media, "alt", null, null, "Izquierda", null, new Crop(0, 0, 0.6, 1), null), ACTOR);
        ContentItem right = service.create("featured-selection",
                new ItemInput(media, "alt", null, null, "Derecha", null, new Crop(0.4, 0, 0.6, 1), null), ACTOR);

        assertThat(items.findById(left.getId()).orElseThrow().getFraming().crop()).isEqualTo(new Crop(0, 0, 0.6, 1));
        assertThat(items.findById(right.getId()).orElseThrow().getFraming().crop()).isEqualTo(new Crop(0.4, 0, 0.6, 1));
        assertThat(filesOf(media)).isEqualTo(3); // el encuadre no genera ni modifica archivos
    }

    @Test
    void cropsMustBe4by5AndTheSectionDecidesTheKindOfFraming() {
        assertThatThrownBy(() -> service.create("disciplines",
                new ItemInput(media, "alt", null, null, "T", null, new Crop(0, 0, 1, 1), null), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("crop"));
        assertThatThrownBy(() -> service.create("hero",
                new ItemInput(media, "alt", null, null, null, null, new Crop(0, 0, 0.6, 1), null), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("crop"));
        assertThatThrownBy(() -> service.create("disciplines",
                new ItemInput(media, "alt", null, null, "T", null, null, new FocalPoint(0.5, 0.5)), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        e -> assertThat(e.getFieldErrors()).containsKey("mediaPosition"));
    }

    @Test
    void withoutAnExplicitFramingTheCenteredOneIsStored() {
        ContentItem card = service.create("disciplines", card("Centrada"), ACTOR);
        ContentItem hero = service.create("hero", new ItemInput(media, "alt", null, null, null, null, null, null), ACTOR);
        Crop crop = card.getFraming().crop();
        assertThat(crop.x()).isCloseTo(0.2, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(crop.y()).isZero();
        assertThat(crop.width()).isCloseTo(0.6, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(crop.height()).isEqualTo(1.0);
        assertThat(hero.getFraming().mediaPosition()).isEqualTo(new FocalPoint(0.5, 0.5));
    }

    @Test
    void replacingOrDeletingAnItemKeepsTheOriginalAndItsRenditions() {
        UUID other = com.kinich.support.MediaTestData.readyImage(jdbc, 8, 10);
        ContentItem item = service.create("disciplines", card("Uno"), ACTOR);
        ContentItem replaced = service.update("disciplines", item.getId(),
                new ItemInput(other, "alt", null, null, "Uno", null, null, null), item.getVersion(), ACTOR);
        service.delete("disciplines", replaced.getId(), replaced.getVersion(), ACTOR);

        assertThat(filesOf(media)).isEqualTo(3);
        assertThat(filesOf(other)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_files WHERE role = 'ORIGINAL'", Long.class)).isEqualTo(2);
    }

    @Test
    void aMediaThatIsNotReadyCannotBeUsed() {
        jdbc.update("UPDATE media_assets SET status = 'PROCESSING' WHERE id = ?", media);
        assertThatThrownBy(() -> service.create("disciplines", card("T"), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.getFieldErrors()).containsKey("mediaId"));
    }
}
