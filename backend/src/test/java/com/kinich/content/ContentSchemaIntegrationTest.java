package com.kinich.content;

import static com.kinich.support.ContentTestData.VALUES_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Restricciones que ddl-auto=validate no comprueba: CHECK, UNIQUE, FK y la semilla. */
@IntegrationTest
class ContentSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void homeHasSixSectionsInOrder() {
        List<String> keys = jdbc.queryForList(
                "SELECT section_key FROM sections WHERE page_key = 'HOME' ORDER BY position", String.class);

        assertThat(keys).containsExactly("HERO", "EDITORIAL_NOTE", "DISCIPLINES", "VALUES",
                "FEATURED_SELECTION", "CLOSING_CTA");
    }

    @Test
    void unknownPageKeyIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sections (id, page_key, section_key, position, created_at, "
                + "updated_at) VALUES (?, 'ABOUT', 'INTRO', 1, now(), now())", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("sections_page_key_check");
    }

    @Test
    void sectionKeyMustBeUpperSnakeCase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sections (id, page_key, section_key, position, created_at, "
                + "updated_at) VALUES (?, 'HOME', 'intro', 7, now(), now())", UUID.randomUUID()))
                .hasMessageContaining("sections_section_key_check");
    }

    @Test
    void publishedRevisionCannotExceedDraftRevision() {
        assertThatThrownBy(() -> jdbc.update("UPDATE sections SET published_revision = 1 WHERE id = ?", VALUES_ID))
                .hasMessageContaining("sections_revisions_check");
    }

    @Test
    void blankBodyIsRejected() {
        assertThatThrownBy(() -> insertDraft(UUID.randomUUID(), null, "   ", 1))
                .hasMessageContaining("text_contents_body_check");
    }

    @Test
    void blankOptionalFieldIsRejected() {
        assertThatThrownBy(() -> insertDraft(UUID.randomUUID(), " ", "Texto", 1))
                .hasMessageContaining("text_contents_eyebrow_check");
    }

    @Test
    void positionMustBePositive() {
        assertThatThrownBy(() -> insertDraft(UUID.randomUUID(), null, "Texto", 0))
                .hasMessageContaining("text_contents_position_check");
    }

    @Test
    void draftMustBelongToAnExistingSection() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO text_contents (id, section_id, body, position, created_at, "
                + "updated_at) VALUES (?, ?, 'Texto', 1, now(), now())", UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("text_contents_section_fk");
    }

    @Test
    void publishedPositionsAreUniquePerSection() {
        insertPublished(null, 1);
        assertThatThrownBy(() -> insertPublished(null, 1))
                .hasMessageContaining("published_text_contents_section_position_uk");
    }

    @Test
    void deletingTheDraftKeepsThePublishedCopy() {
        UUID draftId = UUID.randomUUID();
        insertDraft(draftId, null, "Texto", 1);
        insertPublished(draftId, 1);

        jdbc.update("DELETE FROM text_contents WHERE id = ?", draftId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM published_text_contents WHERE source_id IS NULL",
                Integer.class)).isEqualTo(1);
    }

    private void insertDraft(UUID id, String eyebrow, String body, int position) {
        jdbc.update("INSERT INTO text_contents (id, section_id, eyebrow, body, position, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, now(), now())", id, VALUES_ID, eyebrow, body, position);
    }

    private void insertPublished(UUID sourceId, int position) {
        jdbc.update("INSERT INTO published_text_contents (id, section_id, source_id, body, position, created_at, "
                + "updated_at) VALUES (?, ?, ?, 'Texto', ?, now(), now())", UUID.randomUUID(), VALUES_ID, sourceId,
                position);
    }
}
