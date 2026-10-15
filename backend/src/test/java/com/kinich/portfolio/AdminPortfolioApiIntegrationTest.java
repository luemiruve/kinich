package com.kinich.portfolio;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaTestData;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API de administración de proyectos. Las instantáneas publicadas se siembran por JDBC: la publicación llega en la
 * tarea siguiente y aquí solo se comprueba cómo se derivan estado, cambios y orden.
 */
@IntegrationTest
class AdminPortfolioApiIntegrationTest {

    private static final String PROJECTS = "/api/admin/portfolio/projects";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private javax.sql.DataSource dataSource;

    private String token;
    private UUID adminId;
    /** 4:3 → portada centrada de ancho 0,6. */
    private UUID landscape;
    private UUID second;
    private UUID third;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        adminId = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        token = tokenService.issue(adminId);
        landscape = MediaTestData.readyImage(jdbc, 400, 300);
        second = MediaTestData.readyImage(jdbc, 400, 500);
        third = MediaTestData.readyImage(jdbc, 300, 400);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    // --- Seguridad ---

    @Test
    void adminRoutesRequireSessionAndCsrf() throws Exception {
        mvc.perform(get(PROJECTS)).andExpect(status().isUnauthorized());
        mvc.perform(post(PROJECTS).with(session(token)).contentType(APPLICATION_JSON).content(body("boda", landscape)))
                .andExpect(status().isForbidden());
        JsonNode created = create(body("boda", landscape));
        String id = created.get("id").asText();
        mvc.perform(put(PROJECTS + "/" + id).with(session(token)).contentType(APPLICATION_JSON)
                .content(body("boda", 0L, landscape))).andExpect(status().isForbidden());
        mvc.perform(patch(PROJECTS + "/" + id + "/visibility").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(delete(PROJECTS + "/" + id).param("version", "0").with(session(token)))
                .andExpect(status().isForbidden());
        mvc.perform(put(PROJECTS + "/order").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"projects\":[{\"id\":\"" + id + "\",\"version\":0}]}")).andExpect(status().isForbidden());
    }

    @Test
    void unknownProjectIs404() throws Exception {
        String unknown = PROJECTS + "/" + UUID.randomUUID();
        mvc.perform(get(unknown).with(session(token))).andExpect(status().isNotFound());
        mvc.perform(put(unknown).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content(body("boda", 0L, landscape))).andExpect(status().isNotFound());
        mvc.perform(patch(unknown + "/visibility").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"hidden\":true,\"version\":0}")).andExpect(status().isNotFound());
        mvc.perform(delete(unknown).param("version", "0").with(session(token)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- Crear ---

    @Test
    void createReturns201AsDraftAtTheEndWithCenteredCoverAndAudit() throws Exception {
        create(body("primero", landscape));
        String category = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO content_categories (id, label, created_at, updated_at) VALUES (?::uuid, 'Bodas', now(), now())",
                category);
        String request = "{\"slug\":\"boda-ana\",\"title\":\"Boda de Ana\",\"eyebrow\":\"Valle\",\"categoryId\":\""
                + category + "\",\"description\":\"Texto\",\"cover\":{\"mediaId\":\"" + landscape
                + "\",\"alt\":\"Portada\"},\"images\":[{\"mediaId\":\"" + second
                + "\",\"alt\":\"Uno\",\"caption\":\"Pie\"}]}";
        String id = mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(PROJECTS + "/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.slug").value("boda-ana"))
                .andExpect(jsonPath("$.publishedSlug").doesNotExist())
                .andExpect(jsonPath("$.category.label").value("Bodas"))
                .andExpect(jsonPath("$.coverMediaId").value(landscape.toString()))
                .andExpect(jsonPath("$.cover.alt").value("Portada"))
                .andExpect(jsonPath("$.cover.crop.width").value(closeTo(0.6, 1e-9)))
                .andExpect(jsonPath("$.cover.crop.x").value(closeTo(0.2, 1e-9)))
                .andExpect(jsonPath("$.cover.fallback.candidates[0].url").value(startsWith("/api/admin/media/files/")))
                .andExpect(jsonPath("$.images[0].mediaId").value(second.toString()))
                .andExpect(jsonPath("$.images[0].caption").value("Pie"))
                .andExpect(jsonPath("$.images[0].image.alt").value("Uno"))
                .andExpect(jsonPath("$.images[0].image.crop").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(id).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT draft_revision FROM portfolio_projects WHERE id = ?", Long.class, projectId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT created_by FROM portfolio_projects WHERE id = ?", UUID.class, projectId))
                .isEqualTo(adminId);
    }

    @Test
    void the201stProjectIsRejected() throws Exception {
        for (int i = 1; i <= PortfolioLimits.MAX_PROJECTS; i++) {
            jdbc.update("INSERT INTO portfolio_projects (id, slug, title, cover_media_asset_id, cover_alt, cover_crop_x, "
                    + "cover_crop_y, cover_crop_width, cover_crop_height, position, created_at, updated_at) "
                    + "VALUES (?, ?, 'T', ?, 'a', 0, 0, 0.6, 1, ?, now(), now())", UUID.randomUUID(), "p-" + i, landscape, i);
        }
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("otro", landscape)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAX_ITEMS_REACHED"));
    }

    @Test
    void sixtyOneImagesAreRejectedAndARepeatedMediaIsAllowed() throws Exception {
        UUID[] many = new UUID[61];
        java.util.Arrays.fill(many, second);
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("muchas", many)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAX_ITEMS_REACHED"));
        // R-4: el mismo medio dos veces en la galería y también como portada.
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("repetido", landscape, landscape)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[1].mediaId").value(landscape.toString()));
    }

    @Test
    void missingOrNotReadyMediaAndUnknownCategoryAreFieldErrors() throws Exception {
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors['images[0].mediaId']").exists());
        jdbc.update("UPDATE media_assets SET status = 'PROCESSING' WHERE id = ?", third);
        String request = "{\"slug\":\"boda\",\"title\":\"T\",\"categoryId\":\"" + UUID.randomUUID()
                + "\",\"cover\":{\"mediaId\":\"" + third + "\",\"alt\":\"a\"},\"images\":[{\"mediaId\":\"" + second
                + "\",\"alt\":\"a\"},{\"mediaId\":\"" + third + "\",\"alt\":\"b\"}]}";
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['cover.mediaId']").exists())
                .andExpect(jsonPath("$.errors['images[1].mediaId']").exists())
                .andExpect(jsonPath("$.errors['images[0].mediaId']").doesNotExist())
                .andExpect(jsonPath("$.errors.categoryId").exists());
        assertThat(count("portfolio_projects")).isZero();
    }

    @Test
    void coverCropMustBe45AndInsideTheImage() throws Exception {
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(bodyWithCrop("cuadrado", "{\"x\":0,\"y\":0,\"width\":1,\"height\":1}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.crop").exists());
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(bodyWithCrop("fuera", "{\"x\":0.5,\"y\":0,\"width\":0.6,\"height\":1}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.crop").exists());
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(bodyWithCrop("valido", "{\"x\":0.1,\"y\":0,\"width\":0.6,\"height\":1}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cover.crop.x").value(closeTo(0.1, 1e-9)));
    }

    // --- Guardar ---

    @Test
    void putReplacesTheWholeGalleryAndRequiresTheCurrentVersion() throws Exception {
        JsonNode created = create(body("boda", landscape, second, third));
        String url = PROJECTS + "/" + created.get("id").asText();
        long version = created.get("version").asLong();
        // Intercambia posiciones y quita una: ejercita orphanRemoval y la UNIQUE diferida de posiciones.
        mvc.perform(put(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", version, third, landscape)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].mediaId").value(third.toString()))
                .andExpect(jsonPath("$.images[1].mediaId").value(landscape.toString()));
        assertThat(count("portfolio_project_images")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT position FROM portfolio_project_images ORDER BY position", Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT draft_revision FROM portfolio_projects", Long.class)).isEqualTo(2L);
        mvc.perform(get(url).with(session(token)))
                .andExpect(jsonPath("$.images[0].mediaId").value(third.toString()))
                .andExpect(jsonPath("$.images[0].image.alt").value("alt 0"));
        mvc.perform(put(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", version, landscape)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(put(url).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", (Long) null, landscape)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void slugUsedByAnotherDraftIsRejected() throws Exception {
        create(body("boda", landscape));
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", landscape)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").value("ya está en uso"));
        JsonNode other = create(body("otra", landscape));
        mvc.perform(put(PROJECTS + "/" + other.get("id").asText()).with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content(body("boda", other.get("version").asLong(), landscape)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").value("ya está en uso"));
        // Guardar con su propio slug no choca consigo mismo.
        mvc.perform(put(PROJECTS + "/" + other.get("id").asText()).with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content(body("otra", other.get("version").asLong(), landscape)))
                .andExpect(status().isOk());
    }

    /** Review Focus 1: A publicado como «boda» cambia su borrador a «boda-2»; B no puede tomar «boda». */
    @Test
    void slugPublishedByAnotherProjectIsRejected() throws Exception {
        JsonNode a = create(body("boda", landscape));
        UUID aId = UUID.fromString(a.get("id").asText());
        publish(aId, "boda", 1);
        mvc.perform(put(PROJECTS + "/" + aId).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda-2", a.get("version").asLong(), landscape)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("boda-2"))
                .andExpect(jsonPath("$.publishedSlug").value("boda"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.modified").value(true));
        mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("boda", landscape)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").value("ya está en uso"));
        JsonNode b = create(body("otra", landscape));
        mvc.perform(put(PROJECTS + "/" + b.get("id").asText()).with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content(body("boda", b.get("version").asLong(), landscape)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.slug").value("ya está en uso"));
        // A puede volver a su propio slug publicado.
        long aVersion = objectMapper.readTree(mvc.perform(get(PROJECTS + "/" + aId).with(session(token)))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
        mvc.perform(put(PROJECTS + "/" + aId).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content(body("boda", aVersion, landscape))).andExpect(status().isOk());
    }

    // --- Visibilidad ---

    @Test
    void hidingKeepsTheSnapshotAndMarksItModified() throws Exception {
        JsonNode published = create(body("publicado", landscape));
        UUID publishedId = UUID.fromString(published.get("id").asText());
        publish(publishedId, "publicado", 1);
        mvc.perform(patch(PROJECTS + "/" + publishedId + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"hidden\":true,\"version\":" + published.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.modified").value(true));
        assertThat(count("published_portfolio_projects")).isEqualTo(1);

        JsonNode draft = create(body("borrador", landscape));
        mvc.perform(patch(PROJECTS + "/" + draft.get("id").asText() + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"hidden\":true,\"version\":99}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(PROJECTS + "/" + draft.get("id").asText() + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"hidden\":true,\"version\":" + draft.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.modified").value(false));
    }

    // --- Eliminar ---

    @Test
    void deleteIsImmediateAndRenumbersDraftAndPublishedPositions() throws Exception {
        JsonNode a = create(body("a", landscape));
        JsonNode b = create(body("b", landscape, second));
        JsonNode c = create(body("c", landscape));
        publish(UUID.fromString(a.get("id").asText()), "a", 1);
        publish(UUID.fromString(b.get("id").asText()), "b", 2);
        publish(UUID.fromString(c.get("id").asText()), "c", 3);
        String bUrl = PROJECTS + "/" + b.get("id").asText();
        mvc.perform(delete(bUrl).param("version", "99").with(session(token)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(delete(bUrl).param("version", b.get("version").asText()).with(session(token)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT slug FROM portfolio_projects ORDER BY position", String.class))
                .containsExactly("a", "c");
        assertThat(jdbc.queryForList("SELECT position FROM portfolio_projects ORDER BY position", Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbc.queryForList("SELECT slug FROM published_portfolio_projects ORDER BY position", String.class))
                .containsExactly("a", "c");
        assertThat(jdbc.queryForList("SELECT position FROM published_portfolio_projects ORDER BY position", Integer.class))
                .containsExactly(1, 2);
        assertThat(count("portfolio_project_images")).isEqualTo(2);
        mvc.perform(get(bUrl).with(session(token))).andExpect(status().isNotFound());
    }

    // --- Orden en borrador ---

    @Test
    void draftOrderRenumbersOnlyDraftsAndFlagsOrderModified() throws Exception {
        JsonNode a = create(body("a", landscape));
        JsonNode b = create(body("b", landscape));
        JsonNode c = create(body("c", landscape));
        publish(UUID.fromString(a.get("id").asText()), "a", 1);
        publish(UUID.fromString(b.get("id").asText()), "b", 2);
        mvc.perform(get(PROJECTS).with(session(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderModified").value(false))
                .andExpect(jsonPath("$.projects[0].slug").value("a"))
                .andExpect(jsonPath("$.projects[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.projects[0].modified").value(false))
                .andExpect(jsonPath("$.projects[0].cover.crop.width").value(closeTo(0.6, 1e-9)))
                .andExpect(jsonPath("$.projects[2].status").value("DRAFT"));

        String foreign = order(entry(c), entry(b), "{\"id\":\"" + UUID.randomUUID() + "\",\"version\":0}");
        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(foreign))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projects").exists());
        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(b), entry(b))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projects").exists());
        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(b))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projects").exists());
        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(b), "{\"id\":\"" + a.get("id").asText() + "\",\"version\":99}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(b), entry(a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderModified").value(true))
                .andExpect(jsonPath("$.projects[0].slug").value("c"))
                .andExpect(jsonPath("$.projects[0].position").value(1))
                .andExpect(jsonPath("$.projects[1].slug").value("b"))
                .andExpect(jsonPath("$.projects[2].slug").value("a"))
                .andExpect(jsonPath("$.projects[2].position").value(3))
                // Ordenar no es un cambio del contenido del proyecto.
                .andExpect(jsonPath("$.projects[2].modified").value(false));
        // D-15: el orden público no cambia.
        assertThat(jdbc.queryForList("SELECT slug FROM published_portfolio_projects ORDER BY position", String.class))
                .containsExactly("a", "b");
    }

    @Test
    void responsesNeverExposeOriginalsOrStorageKeys() throws Exception {
        JsonNode created = create(body("boda", landscape, second));
        String detail = mvc.perform(get(PROJECTS + "/" + created.get("id").asText()).with(session(token)))
                .andReturn().getResponse().getContentAsString();
        String list = mvc.perform(get(PROJECTS).with(session(token))).andReturn().getResponse().getContentAsString();
        List<String> forbidden = jdbc.queryForList("SELECT id::text FROM media_files WHERE role = 'ORIGINAL' "
                + "UNION ALL SELECT storage_key FROM media_files", String.class);
        assertThat(forbidden).isNotEmpty();
        for (String value : forbidden) {
            assertThat(detail).doesNotContain(value);
            assertThat(list).doesNotContain(value);
        }
        assertThat(detail).doesNotContain("storageKey").doesNotContain("ORIGINAL");
    }

    /** Carrera: otra transacción inserta el mismo slug sin confirmar; la comprobación previa no la ve y la UNIQUE sí. */
    @Test
    void concurrentSlugViolationIsA400() throws Exception {
        try (java.sql.Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (java.sql.PreparedStatement insert = other.prepareStatement("INSERT INTO portfolio_projects (id, slug, "
                    + "title, cover_media_asset_id, cover_alt, cover_crop_x, cover_crop_y, cover_crop_width, "
                    + "cover_crop_height, position, created_at, updated_at) "
                    + "VALUES (?, 'carrera', 'T', ?, 'a', 0, 0, 0.6, 1, 1, now(), now())")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, landscape);
                insert.executeUpdate();
            }
            java.util.concurrent.CompletableFuture<org.springframework.mock.web.MockHttpServletResponse> request =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return mvc.perform(post(PROJECTS).with(session(token)).with(csrf())
                                    .contentType(APPLICATION_JSON).content(body("carrera", landscape)))
                                    .andReturn().getResponse();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
            // Espera a que la petición quede bloqueada por la fila sin confirmar antes de confirmarla.
            long deadline = System.currentTimeMillis() + 10_000;
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                    + "AND datname = current_database()", Integer.class) == 0) {
                assertThat(System.currentTimeMillis()).isLessThan(deadline);
                assertThat(request).isNotDone();
                Thread.onSpinWait();
            }
            other.commit();
            org.springframework.mock.web.MockHttpServletResponse response = request.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(objectMapper.readTree(response.getContentAsString()).at("/errors/slug").asText())
                    .isEqualTo("ya está en uso");
        }
    }

    // --- Ayudas ---

    private JsonNode create(String body) throws Exception {
        String json = mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    /** Siembra la instantánea publicada tal como la dejará la publicación (Task 5). */
    private void publish(UUID source, String slug, int position) {
        jdbc.update("INSERT INTO published_portfolio_projects (id, source_id, slug, title, cover_media_asset_id, cover_alt, "
                + "cover_crop_x, cover_crop_y, cover_crop_width, cover_crop_height, position, published_at) "
                + "SELECT gen_random_uuid(), id, ?, title, cover_media_asset_id, cover_alt, cover_crop_x, cover_crop_y, "
                + "cover_crop_width, cover_crop_height, ?, now() FROM portfolio_projects WHERE id = ?", slug, position, source);
        jdbc.update("UPDATE portfolio_projects SET published_revision = draft_revision WHERE id = ?", source);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static String body(String slug, UUID... images) {
        return body(slug, (Long) null, images);
    }

    private static String body(String slug, Long version, UUID... images) {
        String gallery = IntStream.range(0, images.length)
                .mapToObj(i -> "{\"mediaId\":\"" + images[i] + "\",\"alt\":\"alt " + i + "\"}")
                .collect(Collectors.joining(","));
        return "{\"slug\":\"" + slug + "\",\"title\":\"Proyecto " + slug + "\",\"cover\":{\"mediaId\":\"" + images[0]
                + "\",\"alt\":\"Portada\"},\"images\":[" + gallery + "]" + (version == null ? "" : ",\"version\":" + version)
                + "}";
    }

    private String bodyWithCrop(String slug, String crop) {
        return "{\"slug\":\"" + slug + "\",\"title\":\"T\",\"cover\":{\"mediaId\":\"" + landscape
                + "\",\"alt\":\"a\",\"crop\":" + crop + "},\"images\":[{\"mediaId\":\"" + second + "\",\"alt\":\"a\"}]}";
    }

    private String entry(JsonNode project) throws Exception {
        String json = mvc.perform(get(PROJECTS + "/" + project.get("id").asText()).with(session(token)))
                .andReturn().getResponse().getContentAsString();
        return "{\"id\":\"" + project.get("id").asText() + "\",\"version\":"
                + objectMapper.readTree(json).get("version").asLong() + "}";
    }

    private static String order(String... entries) {
        return "{\"projects\":[" + String.join(",", entries) + "]}";
    }
}
