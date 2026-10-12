package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AdminContentApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        userId = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        token = tokenService.issue(userId);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    private static final String VALUES = "/api/admin/pages/home/sections/values/texts";
    private static final String NOTE = "/api/admin/pages/home/sections/editorial-note/texts";

    @Test
    void adminPageRequiresASession() throws Exception {
        mvc.perform(get("/api/admin/pages/home")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesRequireCsrf() throws Exception {
        mvc.perform(post(VALUES).with(session(token)).contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyWriteRequiresCsrfEvenWithAValidSession() throws Exception {
        String id = create(VALUES, "Valor");
        mvc.perform(put(VALUES + "/" + id).with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"body\":\"x\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(patch(VALUES + "/" + id + "/visibility").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(delete(VALUES + "/" + id).param("version", "0").with(session(token)))
                .andExpect(status().isForbidden());
        mvc.perform(put(VALUES + "/order").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"revision\":0,\"ids\":[]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pages/home/sections/values/publication").with(session(token))
                .contentType(APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isForbidden());
    }

    @Test
    void mismatchedCsrfTokenIsRejected() throws Exception {
        mvc.perform(post(VALUES).with(session(token)).cookie(new Cookie("XSRF-TOKEN", "uno"))
                        .header("X-XSRF-TOKEN", "otro").contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    /** Sin CSRF se rechaza antes de autenticar (403); con CSRF válido la sesión inválida sí da 401. */
    @Test
    void garbageSessionWithoutCsrfIs403ButWithCsrfIs401() throws Exception {
        mvc.perform(post(VALUES).with(session("basura")).contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(VALUES).with(session("basura")).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfRejectedWriteSetsAtMostOneXsrfCookie() throws Exception {
        var cookies = mvc.perform(post(VALUES).with(session(token)).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden()).andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).count()).isLessThanOrEqualTo(1);
    }

    @Test
    void createReturns201WithLocationAndDraftStatus() throws Exception {
        mvc.perform(post(VALUES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"eyebrow\":\"  \",\"title\":\"Luz\",\"body\":\"Texto\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(VALUES + "/")))
                .andExpect(jsonPath("$.eyebrow").value(nullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void massAssignmentIsIgnored() throws Exception {
        String forged = "{\"body\":\"Texto\",\"status\":\"PUBLISHED\",\"position\":9,\"hidden\":true,"
                + "\"id\":\"00000000-0000-0000-0000-000000000001\",\"createdBy\":\"00000000-0000-0000-0000-000000000002\","
                + "\"sectionId\":\"0d1e0000-0000-4000-8000-000000000002\"}";
        mvc.perform(post(VALUES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(forged))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not("00000000-0000-0000-0000-000000000001")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.position").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM text_contents WHERE section_id = ?", Integer.class,
                ContentTestData.EDITORIAL_NOTE_ID)).isZero();
        assertThat(jdbc.queryForObject("SELECT created_by FROM text_contents", UUID.class)).isEqualTo(userId);
    }

    @Test
    void validationErrorsUseTheStableCodeAndDoNotEchoValues() throws Exception {
        String tooLong = "x".repeat(2001);
        mvc.perform(post(VALUES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.body").exists())
                .andExpect(content().string(not(containsString(tooLong))));
    }

    @Test
    void emojisUpToTheLimitAreAccepted() throws Exception {
        mvc.perform(post(VALUES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"" + "📷".repeat(1000) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void secondEditorialNoteIsRejected() throws Exception {
        create(NOTE, "Nota");
        mvc.perform(post(NOTE).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content("{\"body\":\"Otra\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAX_ITEMS_REACHED"));
    }

    @Test
    void nonTextSectionsAre404() throws Exception {
        mvc.perform(post("/api/admin/pages/home/sections/hero/texts").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void aBlockAddressedThroughAnotherSectionIs404() throws Exception {   // IDOR/BOLA
        String id = create(VALUES, "Valor");
        mvc.perform(put(NOTE + "/" + id).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"x\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(NOTE + "/" + id).param("version", "0").with(session(token)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch(NOTE + "/" + id + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"hidden\":true,\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionsAndRevisionsAre409() throws Exception {
        String id = create(VALUES, "Valor");
        mvc.perform(put(VALUES + "/" + id).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"body\":\"Uno\",\"version\":0}")).andExpect(status().isOk());

        mvc.perform(put(VALUES + "/" + id).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"Dos\",\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(patch(VALUES + "/" + id + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"hidden\":true,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(delete(VALUES + "/" + id).param("version", "0").with(session(token)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(put(VALUES + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"revision\":0,\"ids\":[\"" + id + "\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(put("/api/admin/pages/home/sections/values/publication").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void orderAcceptsAtMost500Ids() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 501).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(put(VALUES + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"revision\":0,\"ids\":[" + ids + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void savingDoesNotChangeThePublicPageButPublishingDoes() throws Exception {
        create(VALUES, "Nuevo valor");
        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[3].texts").isEmpty());

        long revision = jdbc.queryForObject("SELECT draft_revision FROM sections WHERE id = ?", Long.class,
                ContentTestData.VALUES_ID);
        mvc.perform(put("/api/admin/pages/home/sections/values/publication").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"revision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionKey").value("values"))
                .andExpect(jsonPath("$.publishedRevision").value((int) revision));

        mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[3].texts[0].body").value("Nuevo valor"));
    }

    @Test
    void errorsNeverExposeInternals() throws Exception {
        mvc.perform(put(VALUES + "/no-es-uuid").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"x\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private String create(String url, String body) throws Exception {
        String json = mvc.perform(post(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }
}
