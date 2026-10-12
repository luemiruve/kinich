package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.startsWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaTestData;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AdminItemApiIntegrationTest {

    private static final String DISCIPLINES = "/api/admin/pages/home/sections/disciplines/items";
    private static final String FEATURED = "/api/admin/pages/home/sections/featured-selection/items";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private UUID media;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        token = tokenService.issue(users.save(new User("admin@example.com", "{noop}x", "Administración")).getId());
        media = MediaTestData.readyImage(jdbc, 4, 3);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    @Test
    void everyItemWriteRequiresCsrf() throws Exception {
        String body = "{\"mediaId\":\"" + media + "\",\"imageAlt\":\"alt\",\"title\":\"T\"}";
        mvc.perform(post(DISCIPLINES).with(session(token)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String id = create(DISCIPLINES, body);
        mvc.perform(put(DISCIPLINES + "/" + id).with(session(token)).contentType(APPLICATION_JSON)
                .content(body.replace("}", ",\"version\":0}"))).andExpect(status().isForbidden());
        mvc.perform(patch(DISCIPLINES + "/" + id + "/visibility").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(delete(DISCIPLINES + "/" + id).param("version", "0").with(session(token)))
                .andExpect(status().isForbidden());
        mvc.perform(put(DISCIPLINES + "/order").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"revision\":1,\"ids\":[\"" + id + "\"]}")).andExpect(status().isForbidden());
    }

    @Test
    void textAndItemRoutesDoNotCross() throws Exception {
        mvc.perform(post("/api/admin/pages/home/sections/values/items").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\",\"imageAlt\":\"a\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/pages/home/sections/disciplines/texts").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201WithLocationAndTheFifthDisciplineIsRejected() throws Exception {
        String body = "{\"mediaId\":\"" + media + "\",\"imageAlt\":\"alt\",\"title\":\"T\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post(DISCIPLINES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(DISCIPLINES + "/")))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.image.fallback.type").value("image/jpeg"))
                    .andExpect(jsonPath("$.image.sources[0].type").value("image/avif"))
                    .andExpect(jsonPath("$.image.fallback.candidates[0].url").value(startsWith("/api/admin/media/files/")))
                    .andExpect(jsonPath("$.image.crop.width").value(closeTo(0.6, 1e-9)))
                    .andExpect(jsonPath("$.image.mediaPosition").doesNotExist());
        }
        mvc.perform(post(DISCIPLINES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAX_ITEMS_REACHED"));
    }

    @Test
    void validationErrorsUseTheThreeCodes() throws Exception {
        mvc.perform(post("/api/admin/pages/home/sections/closing-cta/items").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + media + "\",\"imageAlt\":\"alt\",\"title\":\"no permitido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.title").exists());
        mvc.perform(post(DISCIPLINES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + media + "\",\"imageAlt\":\"" + "a".repeat(301) + "\",\"title\":\"T\"}"))
                .andExpect(status().isBadRequest());
        String ids = java.util.stream.IntStream.range(0, 501).mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(put(FEATURED + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"revision\":0,\"ids\":[" + ids + "]}")).andExpect(status().isBadRequest());
    }

    @Test
    void adminPageExposesItemSectionsAndTheirFields() throws Exception {
        mvc.perform(get("/api/admin/pages/home").with(session(token)))
                .andExpect(jsonPath("$.sections[0].sectionKey").value("hero"))
                .andExpect(jsonPath("$.sections[0].itemEditable").value(true))
                .andExpect(jsonPath("$.sections[0].maxItems").value(1))
                .andExpect(jsonPath("$.sections[0].itemFields[0]").value("eyebrow"))
                .andExpect(jsonPath("$.sections[0].itemFraming").value("FOCAL_POINT"))
                .andExpect(jsonPath("$.sections[2].itemFraming").value("CROP_4_5"))
                .andExpect(jsonPath("$.sections[2].maxItems").value(4))
                .andExpect(jsonPath("$.sections[2].requiredItemFields[0]").value("title"))
                .andExpect(jsonPath("$.sections[3].itemEditable").value(false))
                .andExpect(jsonPath("$.sections[4].maxItems").doesNotExist());
    }

    @Test
    void framingIsValidatedPerSection() throws Exception {
        String base = "{\"mediaId\":\"" + media + "\",\"imageAlt\":\"alt\",\"title\":\"T\"";
        mvc.perform(post(DISCIPLINES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(base + ",\"crop\":{\"x\":0,\"y\":0,\"width\":1,\"height\":1}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.crop").exists());
        mvc.perform(post(DISCIPLINES).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(base + ",\"crop\":{\"x\":0,\"y\":0,\"width\":0.6}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/admin/pages/home/sections/hero/items").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"mediaId\":\"" + media + "\",\"imageAlt\":\"alt\","
                                + "\"mediaPosition\":{\"x\":0.25,\"y\":0.75}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image.mediaPosition.x").value(0.25))
                .andExpect(jsonPath("$.image.crop").doesNotExist());
    }

    private String create(String url, String body) throws Exception {
        String json = mvc.perform(post(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }
}
