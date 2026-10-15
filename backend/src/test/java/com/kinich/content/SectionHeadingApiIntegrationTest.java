package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.auth.TokenService;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Textos de cabecera de cada sección: contenido editorial con borrador y publicación. */
@IntegrationTest
class SectionHeadingApiIntegrationTest {

    private static final String PAGES = "/api/admin/pages/";
    private static final String SECTIONS = PAGES + "home/sections/";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;

    private String token;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        token = tokenService.issue(users.save(new User("admin@example.com", "{noop}x", "Administración")).getId());
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    private long revision(String slug) {
        return revision(PageKey.HOME, slug);
    }

    private long revision(PageKey page, String slug) {
        return jdbc.queryForObject("SELECT draft_revision FROM sections WHERE page_key = ? AND section_key = ?",
                Long.class, page.name(), SectionKey.fromSlug(slug).orElseThrow().name());
    }

    private ResultActions heading(String slug, String fields) throws Exception {
        return heading(PageKey.HOME, slug, fields);
    }

    private ResultActions heading(PageKey page, String slug, String fields) throws Exception {
        return mvc.perform(put(PAGES + page.slug() + "/sections/" + slug + "/heading").with(session(token))
                .with(csrf()).contentType(APPLICATION_JSON)
                .content("{" + fields + ",\"revision\":" + revision(page, slug) + "}"));
    }

    private ResultActions publish(String slug) throws Exception {
        return publish(PageKey.HOME, slug);
    }

    private ResultActions publish(PageKey page, String slug) throws Exception {
        return mvc.perform(put(PAGES + page.slug() + "/sections/" + slug + "/publication").with(session(token))
                .with(csrf()).contentType(APPLICATION_JSON).content("{\"revision\":" + revision(page, slug) + "}"));
    }

    private ResultActions putHeadingLike(String path) throws Exception {
        return mvc.perform(put(path).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"title\":\"x\",\"revision\":0}"));
    }

    @Test
    void aFreshInstallationHasNoHeadingsAndEachSectionDeclaresItsFields() throws Exception {
        mvc.perform(get("/api/admin/pages/home").with(session(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].sectionKey").value("hero"))
                .andExpect(jsonPath("$.sections[0].headingFields[0]").value("title"))
                .andExpect(jsonPath("$.sections[0].heading.title").isEmpty())
                .andExpect(jsonPath("$.sections[2].headingFields.length()").value(2))
                .andExpect(jsonPath("$.sections[5].headingFields.length()").value(3));
        mvc.perform(get("/api/public/pages/home")).andExpect(jsonPath("$.sections[0].heading").isEmpty());
    }

    @Test
    void aHeadingIsADraftUntilTheSectionIsPublished() throws Exception {
        heading("disciplines", "\"eyebrow\":\" Servicios \",\"title\":\"Disciplinas\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.heading.eyebrow").value("Servicios"))
                .andExpect(jsonPath("$.heading.title").value("Disciplinas"))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
        mvc.perform(get("/api/public/pages/home")).andExpect(jsonPath("$.sections[2].heading").isEmpty());

        publish("disciplines").andExpect(status().isOk());

        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[2].heading.eyebrow").value("Servicios"))
                .andExpect(jsonPath("$.sections[2].heading.title").value("Disciplinas"));

        heading("disciplines", "\"title\":\"Otro título\"").andExpect(status().isOk());
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[2].heading.title").value("Disciplinas"));
    }

    @Test
    void theClosingCtaHasEyebrowTitleAndBody() throws Exception {
        heading("closing-cta", "\"eyebrow\":\"Agenda\",\"title\":\"¿Planeas un evento?\",\"body\":\"Escríbenos.\"")
                .andExpect(status().isOk());
        publish("closing-cta").andExpect(status().isOk());
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[5].heading.body").value("Escríbenos."));
    }

    @Test
    void eachSectionOnlyAcceptsItsOwnFields() throws Exception {
        heading("disciplines", "\"title\":\"x\",\"body\":\"no aplica\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.body").exists());
        heading("hero", "\"eyebrow\":\"no aplica\",\"title\":\"Título\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eyebrow").exists());
        heading("values", "\"title\":\"" + "a".repeat(201) + "\"").andExpect(status().isBadRequest());
        heading("values", "\"title\":\"Dos\\nlíneas\"").andExpect(status().isBadRequest());
    }

    @Test
    void emptyFieldsClearTheHeading() throws Exception {
        heading("values", "\"title\":\"Valores\"").andExpect(status().isOk());
        heading("values", "\"title\":\"   \"").andExpect(status().isOk()).andExpect(jsonPath("$.heading.title").isEmpty());
    }

    @Test
    void aStaleRevisionIsAConflictAndWritesNeedSessionAndCsrf() throws Exception {
        long stale = revision("values");
        heading("values", "\"title\":\"Valores\"").andExpect(status().isOk());
        mvc.perform(put(SECTIONS + "values/heading").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"revision\":" + stale + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(put(SECTIONS + "values/heading").with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"revision\":0}")).andExpect(status().isUnauthorized());
        mvc.perform(put(SECTIONS + "values/heading").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"revision\":0}")).andExpect(status().isForbidden());
        mvc.perform(put(SECTIONS + "inexistente/heading").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isNotFound());
    }

    // --- Página Portafolio: mismo flujo de cabeceras, solo sus secciones ---

    @Test
    void thePortfolioPageListsOnlyItsHeadingSections() throws Exception {
        mvc.perform(get("/api/admin/pages/portfolio").with(session(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pageKey").value("portfolio"))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].sectionKey").value("page-header"))
                .andExpect(jsonPath("$.sections[0].headingFields.length()").value(3))
                .andExpect(jsonPath("$.sections[0].textEditable").value(false))
                .andExpect(jsonPath("$.sections[0].itemEditable").value(false))
                .andExpect(jsonPath("$.sections[0].texts").isEmpty())
                .andExpect(jsonPath("$.sections[0].items").isEmpty())
                .andExpect(jsonPath("$.sections[1].sectionKey").value("closing-cta"))
                .andExpect(jsonPath("$.sections[1].headingFields.length()").value(3))
                .andExpect(jsonPath("$.sections[1].itemEditable").value(false))
                .andExpect(jsonPath("$.sections[1].maxItems").isEmpty())
                .andExpect(jsonPath("$.sections[1].itemFields").isEmpty());
    }

    @Test
    void thePortfolioHeaderAcceptsEyebrowTitleAndBody() throws Exception {
        heading(PageKey.PORTFOLIO, "page-header",
                "\"eyebrow\":\" Portafolio \",\"title\":\"Historias reales\",\"body\":\"Bodas y retratos.\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heading.eyebrow").value("Portafolio"))
                .andExpect(jsonPath("$.heading.title").value("Historias reales"))
                .andExpect(jsonPath("$.heading.body").value("Bodas y retratos."))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
        heading(PageKey.PORTFOLIO, "closing-cta",
                "\"eyebrow\":\"Agenda\",\"title\":\"¿Hablamos?\",\"body\":\"Escríbenos.\"").andExpect(status().isOk());
        publish(PageKey.PORTFOLIO, "page-header").andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionKey").value("page-header"));
        mvc.perform(get("/api/admin/pages/portfolio").with(session(token)))
                .andExpect(jsonPath("$.sections[0].hasUnpublishedChanges").value(false))
                .andExpect(jsonPath("$.sections[1].hasUnpublishedChanges").value(true));
    }

    @Test
    void unknownPagesAndSectionsOfAnotherPageAreNotFound() throws Exception {
        putHeadingLike(PAGES + "portfolio/sections/hero/heading").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "portfolio/sections/hero/publication").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "portfolio/sections/values/heading").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "home/sections/page-header/heading").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "home/sections/page-header/publication").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "unknown/sections/values/heading").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "unknown/sections/values/publication").andExpect(status().isNotFound());
        putHeadingLike(PAGES + "HOME/sections/values/heading").andExpect(status().isNotFound());
        mvc.perform(get(PAGES + "unknown").with(session(token))).andExpect(status().isNotFound());
        mvc.perform(get(PAGES + "portfolio")).andExpect(status().isUnauthorized());
    }

    @Test
    void thePortfolioSectionsNeverAcceptTextsOrItems() throws Exception {
        mvc.perform(get(PAGES + "portfolio/sections/closing-cta/items").with(session(token)))
                .andExpect(status().isNotFound());
        mvc.perform(post(PAGES + "portfolio/sections/page-header/texts").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(post(PAGES + "portfolio/sections/closing-cta/items").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void publishingAPortfolioSectionDoesNotTouchHomeAndViceVersa() throws Exception {
        heading("closing-cta", "\"title\":\"Cierre de Inicio\"").andExpect(status().isOk());
        heading(PageKey.PORTFOLIO, "closing-cta", "\"title\":\"Cierre de Portafolio\"").andExpect(status().isOk());
        heading(PageKey.PORTFOLIO, "page-header", "\"title\":\"Portafolio\"").andExpect(status().isOk());

        publish(PageKey.PORTFOLIO, "closing-cta").andExpect(status().isOk());
        publish(PageKey.PORTFOLIO, "page-header").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sections WHERE page_key = 'HOME' "
                + "AND (published_revision <> 0 OR published_at IS NOT NULL OR published_heading_title IS NOT NULL)",
                Integer.class)).isZero();
        mvc.perform(get("/api/public/pages/home")).andExpect(jsonPath("$.sections[5].heading").isEmpty());

        long portfolioPublished = jdbc.queryForObject("SELECT published_revision FROM sections WHERE id = ?",
                Long.class, ContentTestData.PORTFOLIO_CLOSING_CTA_ID);
        heading(PageKey.PORTFOLIO, "closing-cta", "\"title\":\"Cierre nuevo de Portafolio\"")
                .andExpect(status().isOk());
        publish("closing-cta").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT published_heading_title FROM sections WHERE id = ?", String.class,
                ContentTestData.PORTFOLIO_CLOSING_CTA_ID)).isEqualTo("Cierre de Portafolio");
        assertThat(jdbc.queryForObject("SELECT published_revision FROM sections WHERE id = ?", Long.class,
                ContentTestData.PORTFOLIO_CLOSING_CTA_ID)).isEqualTo(portfolioPublished);
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[5].heading.title").value("Cierre de Inicio"));
    }
}
