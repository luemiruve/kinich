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

@IntegrationTest
class TextContentServiceIntegrationTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TextContentService service;
    @Autowired private TextContentRepository texts;
    @Autowired private SectionRepository sections;
    @Autowired private SectionPublicationService publication;
    @Autowired private UserRepository users;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void createAppendsAtTheEndAndBumpsTheRevision() {
        service.create("values", null, null, "Uno", ACTOR);
        TextContent second = service.create("values", null, null, "Dos", ACTOR);

        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(revisionOfValues()).isEqualTo(2);
    }

    @Test
    void editorialNoteAcceptsOnlyOneBlock() {
        service.create("editorial-note", null, null, "Nota", ACTOR);

        assertThatThrownBy(() -> service.create("editorial-note", null, null, "Otra", ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.MAX_ITEMS_REACHED));
    }

    @Test
    void valuesHasNoMaximum() {
        for (int i = 0; i < 30; i++) {
            service.create("values", null, null, "Valor " + i, ACTOR);
        }

        assertThat(texts.countBySectionId(VALUES_ID)).isEqualTo(30);
    }

    @Test
    void unknownAndNonTextSectionsAreNotFound() {
        for (String slug : List.of("hero", "closing-cta", "VALUES", "nope", "")) {
            assertThatThrownBy(() -> service.create(slug, null, null, "Texto", ACTOR))
                    .isInstanceOfSatisfying(ApiProblemException.class,
                            ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
        }
    }

    @Test
    void aBlockFromAnotherSectionIsNotFound() {
        TextContent value = service.create("values", null, null, "Valor", ACTOR);

        assertThatThrownBy(() -> service.update("editorial-note", value.getId(), null, null, "x", 0, ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(404));
    }

    @Test
    void updateWithAStaleVersionConflicts() {
        TextContent value = service.create("values", null, null, "Valor", ACTOR);
        service.update("values", value.getId(), null, null, "Editado", value.getVersion(), ACTOR);

        assertThatThrownBy(() -> service.update("values", value.getId(), null, null, "Otra", value.getVersion(), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }

    @Test
    void hidingKeepsTheBlockAndBumpsTheRevision() {
        TextContent value = service.create("values", null, null, "Valor", ACTOR);

        TextContent hidden = service.changeVisibility("values", value.getId(), true, value.getVersion(), ACTOR);

        assertThat(hidden.isHidden()).isTrue();
        assertThat(texts.countBySectionId(VALUES_ID)).isEqualTo(1);
        assertThat(revisionOfValues()).isEqualTo(2);
    }

    @Test
    void deleteRemovesTheRowAndCompactsPositions() {
        TextContent first = service.create("values", null, null, "Uno", ACTOR);
        service.create("values", null, null, "Dos", ACTOR);
        service.create("values", null, null, "Tres", ACTOR);

        service.delete("values", first.getId(), first.getVersion(), ACTOR);

        assertThat(texts.findBySectionIdOrderByPosition(VALUES_ID))
                .extracting(TextContent::getBody, TextContent::getPosition)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Dos", 1),
                        org.assertj.core.groups.Tuple.tuple("Tres", 2));
    }

    @Test
    void reorderFollowsTheGivenIds() {
        TextContent a = service.create("values", null, null, "A", ACTOR);
        TextContent b = service.create("values", null, null, "B", ACTOR);

        service.reorder("values", revisionOfValues(), List.of(b.getId(), a.getId()), ACTOR);

        assertThat(texts.findBySectionIdOrderByPosition(VALUES_ID)).extracting(TextContent::getBody)
                .containsExactly("B", "A");
    }

    @Test
    void reorderWithAStaleRevisionConflictsAndChangesNothing() {
        TextContent a = service.create("values", null, null, "A", ACTOR);
        TextContent b = service.create("values", null, null, "B", ACTOR);
        long stale = revisionOfValues() - 1;

        assertThatThrownBy(() -> service.reorder("values", stale, List.of(b.getId(), a.getId()), ACTOR))
                .isInstanceOfSatisfying(ApiProblemException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(texts.findBySectionIdOrderByPosition(VALUES_ID)).extracting(TextContent::getBody)
                .containsExactly("A", "B");
    }

    @Test
    void reorderRejectsMissingExtraOrDuplicatedIds() {
        TextContent a = service.create("values", null, null, "A", ACTOR);
        TextContent b = service.create("values", null, null, "B", ACTOR);
        long revision = revisionOfValues();

        for (List<UUID> ids : List.of(List.of(a.getId()), List.of(a.getId(), b.getId(), UUID.randomUUID()),
                List.of(a.getId(), a.getId()))) {
            assertThatThrownBy(() -> service.reorder("values", revision, ids, ACTOR))
                    .isInstanceOfSatisfying(ApiProblemException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    void concurrentCreatesInTheEditorialNoteLeaveExactlyOneBlock() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    service.create("editorial-note", null, null, "Nota", ACTOR);
                    return true;
                } catch (ApiProblemException | ObjectOptimisticLockingFailureException expected) {
                    return false;
                }
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Boolean> result : results) {
            created += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        executor.shutdownNow();

        assertThat(created).isEqualTo(1);
        assertThat(texts.countBySectionId(ContentTestData.EDITORIAL_NOTE_ID)).isEqualTo(1);
    }

    @Test
    void concurrentDeletesAndPublishesNeverDeadlockAndStayCoherent() throws Exception {
        TextContent first = service.create("values", null, null, "Uno", ACTOR);
        service.create("values", null, null, "Dos", ACTOR);
        long revision = revisionOfValues();
        users.deleteAll();
        UUID publisher = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                boolean delete = i % 2 == 0;
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        if (delete) {
                            service.delete("values", first.getId(), first.getVersion(), ACTOR);
                        } else {
                            publication.publish("values", revision, publisher);
                        }
                    } catch (ApiProblemException | ObjectOptimisticLockingFailureException expected) {
                        // Pierde la carrera: 404/409 esperados.
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Puede ganar el borrado o no (la publicación también versiona la sección); en ambos casos, sin huecos.
        List<Integer> positions = texts.findBySectionIdOrderByPosition(VALUES_ID).stream()
                .map(TextContent::getPosition).toList();
        assertThat(positions).isEqualTo(java.util.stream.IntStream.rangeClosed(1, positions.size()).boxed().toList());
    }

    private long revisionOfValues() {
        return sections.findById(VALUES_ID).orElseThrow().getDraftRevision();
    }
}
