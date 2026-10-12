package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
import com.kinich.media.image.ImageFixtures;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class PublicMediaFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

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

    @Test
    void onlyWebRenditionsOfPublishedMediaArePublicAndTheOriginalNeverIs() throws Exception {
        String mediaId = upload(ImageFixtures.withJpegSegment(ImageFixtures.jpeg(1600, 1000), 0xE1,
                ImageFixtures.exifApp1Payload(1, true)));
        String original = jdbc.queryForObject("SELECT id::text FROM media_files WHERE role = 'ORIGINAL' "
                + "AND media_asset_id = ?::uuid", String.class, mediaId);
        String body = "{\"mediaId\":\"" + mediaId + "\",\"imageAlt\":\"Cena al aire libre\",\"title\":\"Eventos\"}";
        String itemId = create("/api/admin/pages/home/sections/disciplines/items", body);
        String webUrl = objectMapper.readTree(mvc.perform(get("/api/admin/pages/home").with(session(token)))
                .andReturn().getResponse().getContentAsString()).at("/sections/2/items/0/image/fallback/candidates/0/url")
                .asText();
        String webFile = webUrl.substring(webUrl.lastIndexOf('/') + 1);
        mvc.perform(get("/api/public/media/files/" + webFile)).andExpect(status().isNotFound()); // borrador

        publish("disciplines");
        byte[] served = mvc.perform(get("/api/public/media/files/" + webFile))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("ETag", "\"" + webFile + "\""))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageFixtures.contains(served, ImageFixtures.GPS_MARKER)).isFalse();
        // Ni el original ni el id del medio lógico son resolubles, aunque el medio esté publicado.
        mvc.perform(get("/api/public/media/files/" + original)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/media/files/" + original).with(session(token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/media/files/" + mediaId)).andExpect(status().isNotFound());
        String home = mvc.perform(get("/api/public/pages/home"))
                .andExpect(jsonPath("$.sections[2].items[0].image.alt").value("Cena al aire libre"))
                .andExpect(jsonPath("$.sections[2].items[0].image.sources[0].type").value("image/avif"))
                .andExpect(jsonPath("$.sections[2].items[0].image.fallback.candidates.length()").value(3))
                .andExpect(jsonPath("$.sections[2].items[0].mediaId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(home).doesNotContain(original).doesNotContain(mediaId).doesNotContain("/api/admin/");

        long version = currentVersion(itemId);
        mvc.perform(patch("/api/admin/pages/home/sections/disciplines/items/" + itemId + "/visibility")
                .with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":" + version + "}")).andExpect(status().isOk());
        mvc.perform(get("/api/public/media/files/" + webFile)).andExpect(status().isOk()); // aún publicado
        publish("disciplines");
        mvc.perform(get("/api/public/media/files/" + webFile)).andExpect(status().isNotFound());
    }

    private String create(String url, String body) throws Exception {
        String json = mvc.perform(post(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private String upload(byte[] content) throws Exception {
        String json = mvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", content))
                        .with(session(token)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private JsonNode adminSection(String slug) throws Exception {
        String json = mvc.perform(get("/api/admin/pages/home").with(session(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode section : objectMapper.readTree(json).get("sections")) {
            if (section.get("sectionKey").asText().equals(slug)) {
                return section;
            }
        }
        throw new AssertionError("sin sección " + slug);
    }

    private void publish(String slug) throws Exception {
        long revision = adminSection(slug).get("revision").asLong();
        mvc.perform(put("/api/admin/pages/home/sections/" + slug + "/publication").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"revision\":" + revision + "}"))
                .andExpect(status().isOk());
    }

    private long currentVersion(String itemId) throws Exception {
        for (JsonNode item : adminSection("disciplines").get("items")) {
            if (item.get("id").asText().equals(itemId)) {
                return item.get("version").asLong();
            }
        }
        throw new AssertionError("sin elemento " + itemId);
    }
}
