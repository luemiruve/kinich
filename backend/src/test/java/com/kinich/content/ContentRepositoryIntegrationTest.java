package com.kinich.content;

import static com.kinich.support.ContentTestData.EDITORIAL_NOTE_ID;
import static com.kinich.support.ContentTestData.VALUES_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ContentRepositoryIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SectionRepository sections;
    @Autowired private TextContentRepository texts;
    @Autowired private PublishedTextContentRepository published;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void homeSectionsLoadInCatalogOrder() {
        assertThat(sections.findByPageKeyOrderByPosition(PageKey.HOME))
                .extracting(Section::getSectionKey)
                .containsExactly(SectionKey.HERO, SectionKey.EDITORIAL_NOTE, SectionKey.DISCIPLINES,
                        SectionKey.VALUES, SectionKey.FEATURED_SELECTION, SectionKey.CLOSING_CTA);
    }

    @Test
    void findsASectionByKey() {
        assertThat(sections.findByPageKeyAndSectionKey(PageKey.HOME, SectionKey.VALUES))
                .get().extracting(Section::getId).isEqualTo(VALUES_ID);
    }

    @Test
    void aDraftIsOnlyFoundInsideItsOwnSection() {
        TextContent saved = texts.save(new TextContent(VALUES_ID, new TextValues(null, null, "Luz"), 1));

        assertThat(texts.findByIdAndSectionId(saved.getId(), VALUES_ID)).isPresent();
        assertThat(texts.findByIdAndSectionId(saved.getId(), EDITORIAL_NOTE_ID)).isEmpty();
    }

    @Test
    void deleteBySectionIdRemovesOnlyThatSnapshot() {
        TextContent draft = texts.save(new TextContent(VALUES_ID, new TextValues(null, null, "Luz"), 1));
        published.save(PublishedTextContent.snapshotOf(draft, 1));
        TextContent note = texts.save(new TextContent(EDITORIAL_NOTE_ID, new TextValues(null, null, "Nota"), 1));
        published.save(PublishedTextContent.snapshotOf(note, 1));

        tx.executeWithoutResult(status -> published.deleteBySectionId(VALUES_ID));

        assertThat(published.findBySectionIdInOrderByPosition(List.of(VALUES_ID, EDITORIAL_NOTE_ID)))
                .extracting(PublishedTextContent::getSectionId).containsExactly(EDITORIAL_NOTE_ID);
    }

    @Test
    void snapshotCopiesContentAndLinksTheDraft() {
        TextContent draft = texts.save(new TextContent(VALUES_ID, new TextValues("Eyebrow", "Título", "Luz"), 1));

        PublishedTextContent copy = published.save(PublishedTextContent.snapshotOf(draft, 1));

        assertThat(published.findBySourceId(draft.getId())).get().extracting(PublishedTextContent::getId)
                .isEqualTo(copy.getId());
        assertThat(draft.hasSameContentAs(copy)).isTrue();
    }
}
