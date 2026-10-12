package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String SECTIONS = "/api/admin/pages/home/sections/";

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
        return jdbc.queryForObject("SELECT draft_revision FROM sections WHERE section_key = ?", Long.class,
                SectionKey.fromSlug(slug).orElseThrow().name());
    }

    private ResultActions heading(String slug, String fields) throws Exception {
        return mvc.perform(put(SECTIONS + slug + "/heading").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{" + fields + ",\"revision\":" + revision(slug) + "}"));
    }

    private ResultActions publish(String slug) throws Exception {
        return mvc.perform(put(SECTIONS + slug + "/publication").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"revision\":" + revision(slug) + "}"));
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
}
