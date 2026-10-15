package com.kinich.portfolio;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Entrega pública de medios (Task 7): solo las representaciones WEB de un medio referenciado por alguna instantánea
 * publicada (Inicio o Portafolio) se sirven; los borradores, los retirados y el ORIGINAL nunca. Se suben imágenes reales
 * porque el endpoint lee el archivo físico.
 */
@IntegrationTest
class PortfolioMediaServingIntegrationTest {

    private static final String PROJECTS = "/api/admin/portfolio/projects";
    private static final String FILES = "/api/public/media/files/";

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
    void coverAndGalleryFilesOfAPublishedProjectAreServed() throws Exception {
        String cover = upload();
        String gallery = upload();
        JsonNode project = create("boda", cover, gallery);
        mvc.perform(get(FILES + webFile(cover))).andExpect(status().isNotFound()); // borrador
        mvc.perform(get(FILES + webFile(gallery))).andExpect(status().isNotFound());

        publish(project);

        String coverFile = webFile(cover);
        mvc.perform(get(FILES + coverFile)).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + coverFile + "\""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get(FILES + webFile(gallery))).andExpect(status().isOk());
    }

    @Test
    void filesOfADraftOnlyOrNeverPublishedHiddenProjectAreNotServed() throws Exception {
        String cover = upload();
        String gallery = upload();
        create("borrador", cover, gallery);
        JsonNode hidden = create("oculto", upload(), upload());
        String hiddenCover = coverAsset(hidden);
        hide(hidden);

        mvc.perform(get(FILES + webFile(cover))).andExpect(status().isNotFound());
        mvc.perform(get(FILES + webFile(gallery))).andExpect(status().isNotFound());
        mvc.perform(get(FILES + webFile(hiddenCover))).andExpect(status().isNotFound());
    }

    @Test
    void filesStopBeingServedAfterWithdrawAndPublish() throws Exception {
        String cover = upload();
        String gallery = upload();
        JsonNode project = publish(create("boda", cover, gallery));
        String coverFile = webFile(cover);
        String galleryFile = webFile(gallery);
        mvc.perform(get(FILES + coverFile)).andExpect(status().isOk());

        project = hide(project);
        // D-16: retirar solo marca el borrador; sigue público hasta publicar.
        mvc.perform(get(FILES + coverFile)).andExpect(status().isOk());
        mvc.perform(get(FILES + galleryFile)).andExpect(status().isOk());

        publish(project);
        mvc.perform(get(FILES + coverFile)).andExpect(status().isNotFound());
        mvc.perform(get(FILES + galleryFile)).andExpect(status().isNotFound());
    }

    @Test
    void originalFilesAreNeverServedEvenForPublishedProjects() throws Exception {
        String cover = upload();
        String gallery = upload();
        publish(create("boda", cover, gallery));
        for (String asset : new String[] {cover, gallery}) {
            String original = jdbc.queryForObject("SELECT id::text FROM media_files WHERE role = 'ORIGINAL' "
                    + "AND media_asset_id = ?::uuid", String.class, asset);
            mvc.perform(get(FILES + original)).andExpect(status().isNotFound());
            mvc.perform(get(FILES + asset)).andExpect(status().isNotFound());
        }
    }

    /** Review Focus 2: un medio compartido con un item publicado de Inicio sigue sirviéndose al retirar el proyecto. */
    @Test
    void sharedMediaStaysServedWhileAPublishedHomeItemUsesItAndStopsWhenNobodyDoes() throws Exception {
        String shared = upload();
        String other = upload();
        String itemId = createHomeItem(shared);
        publishHome("disciplines");
        JsonNode project = publish(create("boda", shared, other));
        String sharedFile = webFile(shared);
        String otherFile = webFile(other);
        mvc.perform(get(FILES + sharedFile)).andExpect(status().isOk());

        publish(hide(project)); // el proyecto se retira
        mvc.perform(get(FILES + otherFile)).andExpect(status().isNotFound()); // solo lo usaba el proyecto
        mvc.perform(get(FILES + sharedFile)).andExpect(status().isOk()); // Inicio todavía lo usa

        // Inicio deja de referenciarlo: ocultar el item y publicar la sección.
        long version = homeItemVersion("disciplines", itemId);
        mvc.perform(patch("/api/admin/pages/home/sections/disciplines/items/" + itemId + "/visibility")
                .with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":" + version + "}")).andExpect(status().isOk());
        mvc.perform(get(FILES + sharedFile)).andExpect(status().isOk()); // aún publicado
        publishHome("disciplines");
        mvc.perform(get(FILES + sharedFile)).andExpect(status().isNotFound());
    }

    @Test
    void ifNoneMatchAnswers304OnlyWhenTheFileIsServable() throws Exception {
        String cover = upload();
        JsonNode project = create("boda", cover, upload());
        String file = webFile(cover);
        RequestBuilder conditional = get(FILES + file).header("If-None-Match", "\"" + file + "\"");
        mvc.perform(conditional).andExpect(status().isNotFound()); // borrador: no es servible, nunca 304

        project = publish(project);
        mvc.perform(get(FILES + file).header("If-None-Match", "\"" + file + "\"")).andExpect(status().isNotModified());

        publish(hide(project));
        mvc.perform(get(FILES + file).header("If-None-Match", "\"" + file + "\"")).andExpect(status().isNotFound());
        String original = jdbc.queryForObject("SELECT id::text FROM media_files WHERE role = 'ORIGINAL' "
                + "AND media_asset_id = ?::uuid", String.class, cover);
        mvc.perform(get(FILES + original).header("If-None-Match", "\"" + original + "\""))
                .andExpect(status().isNotFound());
    }

    // --- Ayudas ---

    private String webFile(String assetId) {
        return jdbc.queryForObject("SELECT id::text FROM media_files WHERE role = 'WEB' AND media_asset_id = ?::uuid "
                + "ORDER BY id LIMIT 1", String.class, assetId);
    }

    private String coverAsset(JsonNode project) {
        return jdbc.queryForObject("SELECT cover_media_asset_id::text FROM portfolio_projects WHERE id = ?::uuid",
                String.class, project.get("id").asText());
    }

    private String upload() throws Exception {
        String json = mvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", ImageFixtures.jpeg(1600, 1000)))
                        .with(session(token)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private JsonNode create(String slug, String cover, String gallery) throws Exception {
        String body = "{\"slug\":\"" + slug + "\",\"title\":\"Proyecto " + slug + "\",\"cover\":{\"mediaId\":\"" + cover
                + "\",\"alt\":\"Portada\"},\"images\":[{\"mediaId\":\"" + gallery + "\",\"alt\":\"Galería\"}]}";
        String json = mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode hide(JsonNode project) throws Exception {
        String json = mvc.perform(patch(PROJECTS + "/" + project.get("id").asText() + "/visibility")
                        .with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"hidden\":true,\"version\":" + project.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode publish(JsonNode project) throws Exception {
        String json = mvc.perform(put(PROJECTS + "/" + project.get("id").asText() + "/publication")
                        .with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"version\":" + project.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String createHomeItem(String mediaId) throws Exception {
        String body = "{\"mediaId\":\"" + mediaId + "\",\"imageAlt\":\"Cena al aire libre\",\"title\":\"Eventos\"}";
        String json = mvc.perform(post("/api/admin/pages/home/sections/disciplines/items").with(session(token))
                        .with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private JsonNode homeSection(String slug) throws Exception {
        String json = mvc.perform(get("/api/admin/pages/home").with(session(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode section : objectMapper.readTree(json).get("sections")) {
            if (section.get("sectionKey").asText().equals(slug)) {
                return section;
            }
        }
        throw new AssertionError("sin sección " + slug);
    }

    private long homeItemVersion(String slug, String itemId) throws Exception {
        for (JsonNode item : homeSection(slug).get("items")) {
            if (item.get("id").asText().equals(itemId)) {
                return item.get("version").asLong();
            }
        }
        throw new AssertionError("sin elemento " + itemId);
    }

    private void publishHome(String slug) throws Exception {
        long revision = homeSection(slug).get("revision").asLong();
        mvc.perform(put("/api/admin/pages/home/sections/" + slug + "/publication").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"revision\":" + revision + "}"))
                .andExpect(status().isOk());
    }
}
