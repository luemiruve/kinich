package com.kinich.portfolio;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Publicación por proyecto y del orden (D-15, D-16, R-1, R-2, R-5). La API pública llega después: la instantánea se
 * comprueba directamente en la base, que es lo único que leerá el sitio público.
 */
@IntegrationTest
class PortfolioPublicationIntegrationTest {

    private static final String PROJECTS = "/api/admin/portfolio/projects";
    private static final String ORDER = "/api/admin/portfolio/projects/order";
    private static final String ORDER_PUBLICATION = "/api/admin/portfolio/order/publication";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private UUID adminId;
    private UUID first;
    private UUID second;
    private UUID third;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        adminId = users.save(new User("admin@example.com", "{noop}x", "Administración")).getId();
        token = tokenService.issue(adminId);
        first = MediaTestData.readyImage(jdbc, 400, 300);
        second = MediaTestData.readyImage(jdbc, 400, 500);
        third = MediaTestData.readyImage(jdbc, 300, 400);
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    // --- Seguridad ---

    @Test
    void publicationRoutesRequireCsrfAndKnownProjects() throws Exception {
        JsonNode a = create(body("a", first));
        mvc.perform(put(PROJECTS + "/" + id(a) + "/publication").with(session(token)).contentType(APPLICATION_JSON)
                .content(version(a))).andExpect(status().isForbidden());
        mvc.perform(put(ORDER_PUBLICATION).with(session(token)).contentType(APPLICATION_JSON)
                .content(order(entry(a)))).andExpect(status().isForbidden());
        mvc.perform(put(ORDER_PUBLICATION).with(csrf()).contentType(APPLICATION_JSON).content(order(entry(a))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PROJECTS + "/" + UUID.randomUUID() + "/publication").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isNotFound());
        mvc.perform(put(PROJECTS + "/" + id(a) + "/publication").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    // --- Publicar un proyecto ---

    @Test
    void publishingCopiesEveryFieldAndTheGalleryInOrder() throws Exception {
        String category = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO content_categories (id, label, created_at, updated_at) VALUES (?::uuid, 'Bodas', now(), now())",
                category);
        String request = "{\"slug\":\"boda-ana\",\"title\":\"Boda de Ana\",\"eyebrow\":\"Valle\",\"categoryId\":\""
                + category + "\",\"description\":\"Texto\",\"cover\":{\"mediaId\":\"" + first
                + "\",\"alt\":\"Portada\",\"crop\":{\"x\":0.1,\"y\":0,\"width\":0.6,\"height\":1}},\"images\":[{\"mediaId\":\""
                + second + "\",\"alt\":\"Uno\",\"caption\":\"Pie\"},{\"mediaId\":\"" + third + "\",\"alt\":\"Dos\"}]}";
        JsonNode project = create(request);
        long versionBefore = project.get("version").asLong();

        mvc.perform(publication(project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.publishedSlug").value("boda-ana"))
                .andExpect(jsonPath("$.version").value(versionBefore + 1));

        Map<String, Object> copy = jdbc.queryForMap("SELECT * FROM published_portfolio_projects WHERE source_id = ?",
                UUID.fromString(id(project)));
        assertThat(copy.get("slug")).isEqualTo("boda-ana");
        assertThat(copy.get("title")).isEqualTo("Boda de Ana");
        assertThat(copy.get("eyebrow")).isEqualTo("Valle");
        assertThat(copy.get("description")).isEqualTo("Texto");
        assertThat(copy.get("category_label")).isEqualTo("Bodas");
        assertThat(copy.get("cover_media_asset_id")).isEqualTo(first);
        assertThat(copy.get("cover_alt")).isEqualTo("Portada");
        assertThat((Double) copy.get("cover_crop_x")).isEqualTo(0.1);
        assertThat((Double) copy.get("cover_crop_width")).isEqualTo(0.6);
        assertThat(copy.get("position")).isEqualTo(1);
        assertThat(copy.get("published_by")).isEqualTo(adminId);
        assertThat(copy.get("published_at")).isNotNull();
        assertThat(jdbc.queryForList("SELECT media_asset_id, alt, caption, position FROM published_portfolio_project_images "
                + "ORDER BY position")).extracting(row -> row.get("media_asset_id") + "|" + row.get("alt") + "|"
                + row.get("caption") + "|" + row.get("position"))
                .containsExactly(second + "|Uno|Pie|1", third + "|Dos|null|2");
        assertThat(jdbc.queryForObject("SELECT published_revision = draft_revision FROM portfolio_projects", Boolean.class))
                .isTrue();
    }

    /** Republicar cambiando el orden de la galería: la UNIQUE (published_project_id, position) no es diferida. */
    @Test
    void draftChangesStayPrivateUntilRepublishedWhichKeepsThePublicPosition() throws Exception {
        publish(create(body("a", first)));
        JsonNode b = publish(create(body("b", first, second, third)));
        publish(create(body("c", first)));

        JsonNode edited = save(b, body("b-nuevo", b.get("version").asLong(), third, first));
        assertThat(edited.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(edited.get("modified").asBoolean()).isTrue();
        assertThat(publishedGallery("b")).containsExactly(first, second, third);
        assertThat(jdbc.queryForObject("SELECT title FROM published_portfolio_projects WHERE slug = 'b'", String.class))
                .isEqualTo("Proyecto b");

        mvc.perform(publication(edited)).andExpect(status().isOk())
                .andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.publishedSlug").value("b-nuevo"));
        assertThat(publishedSlugs()).containsExactly("a", "b-nuevo", "c");
        assertThat(publishedPositions()).containsExactly(1, 2, 3);
        assertThat(publishedGallery("b-nuevo")).containsExactly(third, first);
        assertThat(jdbc.queryForObject("SELECT title FROM published_portfolio_projects WHERE slug = 'b-nuevo'", String.class))
                .isEqualTo("Proyecto b-nuevo");
        assertThat(count("published_portfolio_project_images")).isEqualTo(4);
    }

    /** R-2: la primera publicación entra al final del orden público, sea cual sea su lugar en el borrador. */
    @Test
    void firstPublicationGoesToTheEndOfThePublicOrder() throws Exception {
        JsonNode a = create(body("a", first));
        JsonNode b = create(body("b", first));
        JsonNode c = create(body("c", first));
        publish(c);
        publish(a);
        publish(b);
        assertThat(publishedSlugs()).containsExactly("c", "a", "b");
        assertThat(publishedPositions()).containsExactly(1, 2, 3);
        mvc.perform(get(PROJECTS).with(session(token))).andExpect(jsonPath("$.orderModified").value(true));
    }

    @Test
    void republishingAnUnchangedProjectStillRequiresTheCurrentVersion() throws Exception {
        JsonNode a = create(body("a", first));
        JsonNode published = publish(a);
        mvc.perform(publication(a)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        JsonNode again = publish(published);
        assertThat(again.get("version").asLong()).isGreaterThan(published.get("version").asLong());
        mvc.perform(publication(published)).andExpect(status().isConflict());
        assertThat(count("published_portfolio_projects")).isEqualTo(1);
    }

    @Test
    void staleVersionIs409AndPublishesNothing() throws Exception {
        JsonNode a = create(body("a", first));
        mvc.perform(put(PROJECTS + "/" + id(a) + "/publication").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"version\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(count("published_portfolio_projects")).isZero();
    }

    @Test
    void concurrentPublicationsWithTheSameVersionPublishOnce() throws Exception {
        JsonNode a = create(body("a", first, second));
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(publication(a)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("published_portfolio_projects")).isEqualTo(1);
        assertThat(count("published_portfolio_project_images")).isEqualTo(2);
    }

    /** Dos primeras publicaciones simultáneas no calculan la misma posición final (R-2): n+1 y n+2. */
    @Test
    void concurrentFirstPublicationsGetDistinctPublicPositions() throws Exception {
        publish(create(body("base", first)));
        List<JsonNode> fresh = List.of(create(body("a", first)), create(body("b", second)));
        ExecutorService executor = Executors.newFixedThreadPool(fresh.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (JsonNode project : fresh) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(publication(project)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(publishedPositions()).containsExactly(1, 2, 3);
        assertThat(publishedSlugs().get(0)).isEqualTo("base");
        assertThat(publishedSlugs().subList(1, 3)).containsExactlyInAnyOrder("a", "b");
    }

    /**
     * Eliminar y publicar el mismo proyecto a la vez: ambos toman primero el bloqueo de publicación, así que uno gana
     * y el otro recibe 404 o 409 (nunca un 500 ni un interbloqueo), y el orden público queda 1..n.
     */
    @Test
    void concurrentDeleteAndPublishOfTheSameProjectEndInOneSuccess() throws Exception {
        for (int round = 0; round < 5; round++) {
            publish(create(body("x" + round, first)));
            JsonNode a = publish(create(body("a" + round, first, second)));
            publish(create(body("y" + round, first)));
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Integer> statuses = new ArrayList<>();
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> deletion = executor.submit(() -> {
                    start.await();
                    return mvc.perform(delete(PROJECTS + "/" + id(a)).param("version", a.get("version").asText())
                            .with(session(token)).with(csrf())).andReturn().getResponse().getStatus();
                });
                Future<Integer> publication = executor.submit(() -> {
                    start.await();
                    return mvc.perform(publication(a)).andReturn().getResponse().getStatus();
                });
                start.countDown();
                statuses.add(deletion.get(10, TimeUnit.SECONDS));
                statuses.add(publication.get(10, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
            assertThat(statuses).as("ronda %s", round).isIn(List.of(204, 404), List.of(409, 200));
            List<Integer> positions = publishedPositions();
            assertThat(positions).isEqualTo(IntStream.rangeClosed(1, positions.size()).boxed().toList());
            assertThat(publishedSlugs()).endsWith("y" + round).contains("x" + round);
        }
    }

    /** Un slug que ya ocupa la instantánea de OTRO proyecto da 400, nunca 500. */
    @Test
    void slugTakenByAnotherSnapshotIsA400() throws Exception {
        JsonNode a = create(body("boda", first));
        JsonNode b = publish(create(body("otro", first)));
        jdbc.update("UPDATE published_portfolio_projects SET slug = 'boda' WHERE source_id = ?::uuid", id(b));
        mvc.perform(publication(a)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").value("ya está en uso"));
        assertThat(publishedSlugs()).containsExactly("boda");
        assertThat(count("published_portfolio_projects")).isEqualTo(1);
    }

    // --- Retirar (D-16) ---

    /** Review Focus 3: retirar en mitad del orden público deja 1..n sin alterar el orden relativo de los demás. */
    @Test
    void hidingThenPublishingRemovesTheSnapshotAndRenumbersThePublicOrder() throws Exception {
        publish(create(body("a", first)));
        JsonNode b = publish(create(body("b", first, second)));
        publish(create(body("c", first)));
        publish(create(body("d", first)));

        JsonNode hidden = hide(b);
        assertThat(hidden.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishedSlugs()).containsExactly("a", "b", "c", "d");

        mvc.perform(publication(hidden)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.publishedSlug").doesNotExist());
        assertThat(publishedSlugs()).containsExactly("a", "c", "d");
        assertThat(publishedPositions()).containsExactly(1, 2, 3);
        // Solo quedan las galerías (de una imagen) de a, c y d.
        assertThat(count("published_portfolio_project_images")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT published_revision = draft_revision FROM portfolio_projects WHERE slug = 'b'",
                Boolean.class)).isTrue();
        mvc.perform(get(PROJECTS).with(session(token))).andExpect(jsonPath("$.orderModified").value(false));
    }

    /** R-5: publicar algo oculto que nunca se publicó responde 200 sin cambios. */
    @Test
    void publishingAHiddenNeverPublishedProjectChangesNothing() throws Exception {
        JsonNode hidden = hide(create(body("a", first)));
        mvc.perform(publication(hidden)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.version").value(hidden.get("version").asLong()));
        assertThat(count("published_portfolio_projects")).isZero();
        assertThat(jdbc.queryForObject("SELECT published_revision FROM portfolio_projects", Long.class)).isZero();
    }

    /** Repetir la visibilidad actual no es un cambio: ni sube la versión ni marca el proyecto como modificado. */
    @Test
    void sameVisibilityChangesNothing() throws Exception {
        JsonNode a = publish(create(body("a", first)));
        mvc.perform(patch(PROJECTS + "/" + id(a) + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"hidden\":false,\"version\":"
                                + a.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.version").value(a.get("version").asLong()));
    }

    @Test
    void deletingIsImmediateEvenWhenPublished() throws Exception {
        publish(create(body("a", first)));
        JsonNode b = publish(create(body("b", first, second)));
        mvc.perform(delete(PROJECTS + "/" + id(b)).param("version", b.get("version").asText()).with(session(token))
                .with(csrf())).andExpect(status().isNoContent());
        assertThat(publishedSlugs()).containsExactly("a");
        assertThat(count("published_portfolio_project_images")).isEqualTo(1);
    }

    // --- Orden (D-15, R-1) ---

    @Test
    void savingTheDraftOrderKeepsThePublicOrderUntilItIsPublished() throws Exception {
        JsonNode a = publish(create(body("a", first)));
        JsonNode b = publish(create(body("b", first)));
        JsonNode c = publish(create(body("c", first)));
        JsonNode d = create(body("d", first));

        mvc.perform(put(ORDER).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(d), entry(a), entry(b))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderModified").value(true));
        assertThat(publishedSlugs()).containsExactly("a", "b", "c");

        List<Long> versions = versions();
        mvc.perform(put(ORDER_PUBLICATION).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(c), entry(d), entry(a), entry(b))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderModified").value(false))
                .andExpect(jsonPath("$.projects[0].slug").value("c"))
                .andExpect(jsonPath("$.projects[1].status").value("DRAFT"));
        assertThat(publishedSlugs()).containsExactly("c", "a", "b");
        assertThat(publishedPositions()).containsExactly(1, 2, 3);
        assertThat(versions()).isEqualTo(versions);
        assertThat(count("published_portfolio_projects")).isEqualTo(3);
    }

    @Test
    void publishingTheOrderValidatesTheFullListLikeTheDraftOrder() throws Exception {
        JsonNode a = publish(create(body("a", first)));
        JsonNode b = publish(create(body("b", first)));
        String unknown = "{\"id\":\"" + UUID.randomUUID() + "\",\"version\":0}";
        for (String invalid : List.of(order(entry(a)), order(entry(a), entry(a)), order(entry(a), entry(b), unknown))) {
            mvc.perform(put(ORDER_PUBLICATION).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projects").exists());
        }
        mvc.perform(put(ORDER_PUBLICATION).with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                        .content(order(entry(b), "{\"id\":\"" + id(a) + "\",\"version\":99}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(publishedSlugs()).containsExactly("a", "b");
    }

    // --- Ayudas ---

    private JsonNode create(String body) throws Exception {
        String json = mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode save(JsonNode project, String body) throws Exception {
        String json = mvc.perform(put(PROJECTS + "/" + id(project)).with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode hide(JsonNode project) throws Exception {
        String json = mvc.perform(patch(PROJECTS + "/" + id(project) + "/visibility").with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"hidden\":true,\"version\":"
                                + project.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode publish(JsonNode project) throws Exception {
        String json = mvc.perform(publication(project)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private org.springframework.test.web.servlet.RequestBuilder publication(JsonNode project) {
        return put(PROJECTS + "/" + id(project) + "/publication").with(session(token)).with(csrf())
                .contentType(APPLICATION_JSON).content(version(project));
    }

    private static String id(JsonNode project) {
        return project.get("id").asText();
    }

    private static String version(JsonNode project) {
        return "{\"version\":" + project.get("version").asLong() + "}";
    }

    private List<String> publishedSlugs() {
        return jdbc.queryForList("SELECT slug FROM published_portfolio_projects ORDER BY position", String.class);
    }

    private List<Integer> publishedPositions() {
        return jdbc.queryForList("SELECT position FROM published_portfolio_projects ORDER BY position", Integer.class);
    }

    private List<UUID> publishedGallery(String slug) {
        return jdbc.queryForList("SELECT i.media_asset_id FROM published_portfolio_project_images i "
                + "JOIN published_portfolio_projects p ON p.id = i.published_project_id WHERE p.slug = ? "
                + "ORDER BY i.position", UUID.class, slug);
    }

    private List<Long> versions() {
        return jdbc.queryForList("SELECT version FROM portfolio_projects ORDER BY id", Long.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static String body(String slug, UUID... images) {
        return body(slug, null, images);
    }

    private static String body(String slug, Long version, UUID... images) {
        String gallery = IntStream.range(0, images.length)
                .mapToObj(i -> "{\"mediaId\":\"" + images[i] + "\",\"alt\":\"alt " + i + "\"}")
                .collect(Collectors.joining(","));
        return "{\"slug\":\"" + slug + "\",\"title\":\"Proyecto " + slug + "\",\"cover\":{\"mediaId\":\"" + images[0]
                + "\",\"alt\":\"Portada\"},\"images\":[" + gallery + "]" + (version == null ? "" : ",\"version\":" + version)
                + "}";
    }

    /** Versión actual del proyecto (cambia al publicar o reordenar). */
    private String entry(JsonNode project) throws Exception {
        String json = mvc.perform(get(PROJECTS + "/" + id(project)).with(session(token)))
                .andReturn().getResponse().getContentAsString();
        return "{\"id\":\"" + id(project) + "\",\"version\":" + objectMapper.readTree(json).get("version").asLong() + "}";
    }

    private static String order(String... entries) {
        return "{\"projects\":[" + String.join(",", entries) + "]}";
    }
}
