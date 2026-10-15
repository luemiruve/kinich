package com.kinich.content;

import static com.kinich.support.ContentTestData.PORTFOLIO_CLOSING_CTA_ID;
import static com.kinich.support.ContentTestData.PORTFOLIO_PAGE_HEADER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V11: la página Portafolio en el catálogo editorial (solo cabeceras). */
@IntegrationTest
class PortfolioPageCatalogIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private SectionHeadingService headings;
    @Autowired private SectionPublicationService publication;
    @Autowired private PublicPageService publicPage;

    @BeforeEach
    @AfterEach
    void reset() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void portfolioHasPageHeaderAndClosingCtaInOrder() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, section_key, position FROM sections WHERE page_key = 'PORTFOLIO' ORDER BY position");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("id", PORTFOLIO_PAGE_HEADER_ID)
                .containsEntry("section_key", "PAGE_HEADER").containsEntry("position", 1);
        assertThat(rows.get(1)).containsEntry("id", PORTFOLIO_CLOSING_CTA_ID)
                .containsEntry("section_key", "CLOSING_CTA").containsEntry("position", 2);
    }

    @Test
    void pageKeysOutsideTheCatalogueAreRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sections (id, page_key, section_key, position, created_at, "
                + "updated_at) VALUES (?, 'OTHER', 'INTRO', 1, now(), now())", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("sections_page_key_check");
    }

    @Test
    void pageKeyResolvesOnlyKnownSlugs() {
        assertThat(PageKey.fromSlug("home")).contains(PageKey.HOME);
        assertThat(PageKey.fromSlug("portfolio")).contains(PageKey.PORTFOLIO);
        assertThat(PageKey.fromSlug("PORTFOLIO")).isEmpty();
        assertThat(PageKey.fromSlug("otra")).isEmpty();
        assertThat(PageKey.fromSlug(null)).isEmpty();
    }

    @Test
    void publicHeadingsOnlyContainPublishedHeadingsOfThatPage() {
        users.deleteAll();
        UUID actor = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        Section header = headings.update(PageKey.PORTFOLIO, "page-header", "Portafolio", "Historias reales",
                "Bodas y retratos.", 0, actor);
        publication.publish(PageKey.PORTFOLIO, "page-header", header.getDraftRevision(), actor);
        headings.update(PageKey.PORTFOLIO, "closing-cta", null, "Solo borrador", null, 0, actor);

        Map<SectionKey, SectionHeading> result = publicPage.headings(PageKey.PORTFOLIO);

        assertThat(result).containsOnlyKeys(SectionKey.PAGE_HEADER);
        assertThat(result.get(SectionKey.PAGE_HEADER))
                .isEqualTo(new SectionHeading("Portafolio", "Historias reales", "Bodas y retratos."));
        assertThat(publicPage.headings(PageKey.HOME)).isEmpty();
    }
}
