package com.kinich.content;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PublicPageApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TextContentService drafts;
    @Autowired private SectionPublicationService publication;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        UUID actor = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        drafts.create("values", null, null, "Publicado", actor);
        long revision = jdbc.queryForObject("SELECT draft_revision FROM sections WHERE id = ?", Long.class,
                ContentTestData.VALUES_ID);
        publication.publish("values", revision, actor);
        drafts.create("values", null, null, "Borrador sin publicar", actor);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void isPublicAndReturnsTheSixSectionsInOrder() throws Exception {
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageKey").value("home"))
                .andExpect(jsonPath("$.sections[*].sectionKey").value(contains("hero", "editorial-note", "disciplines",
                        "values", "featured-selection", "closing-cta")));
    }

    @Test
    void onlyPublishedContentIsReturnedWithoutInternalFields() throws Exception {
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[3].texts[*].body").value(contains("Publicado")))
                .andExpect(jsonPath("$.sections[3].texts[0].status").doesNotExist())
                .andExpect(jsonPath("$.sections[3].texts[0].version").doesNotExist())
                .andExpect(jsonPath("$.sections[3].texts[0].hidden").doesNotExist())
                .andExpect(jsonPath("$.sections[3].texts[0].createdBy").doesNotExist())
                .andExpect(content().string(not(containsString("Borrador sin publicar"))));
    }

    @Test
    void neverSetsCookiesAndIsNotCached() throws Exception {
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void ignoresSessionCookies() throws Exception {
        mvc.perform(get("/api/public/pages/home").cookie(new Cookie("kinich_session", "token-invalido")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void rejectsOtherMethodsWithoutCookies() throws Exception {
        mvc.perform(post("/api/public/pages/home").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void adminApiStillIssuesTheCsrfCookie() throws Exception {   // no regresión de la cadena principal
        mvc.perform(get("/api/auth/me")).andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }
}
