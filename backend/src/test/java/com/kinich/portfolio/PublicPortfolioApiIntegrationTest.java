package com.kinich.portfolio;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
import com.kinich.content.PageKey;
import com.kinich.content.Section;
import com.kinich.content.SectionHeadingService;
import com.kinich.content.SectionPublicationService;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaTestData;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** API pública del portafolio: solo instantáneas publicadas, 404 indistinguible y sin ids internos. */
@IntegrationTest
class PublicPortfolioApiIntegrationTest {

    private static final String PROJECTS = "/api/admin/portfolio/projects";
    private static final String PAGE = "/api/public/pages/portfolio";
    private static final String DETAIL = "/api/public/portfolio/projects/{slug}";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SectionHeadingService headings;
    @Autowired private SectionPublicationService sectionPublication;

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

    @Test
    void emptyPortfolioReturnsEmptyListsAndNoHeadings() throws Exception {
        mvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageHeader").value(nullValue()))
                .andExpect(jsonPath("$.closingCta").value(nullValue()))
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.projects").isEmpty());
    }

    @Test
    void listsOnlyPublishedProjectsInPublicOrderWithCoverCrop() throws Exception {
        JsonNode a = create(body("a", null, first));
        JsonNode b = create(coverWithCrop("b"));
        create(body("borrador", null, third));           // nunca publicado
        publish(a);
        publish(b);

        String json = mvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(2))
                .andExpect(jsonPath("$.projects[0].slug").value("a"))
                .andExpect(jsonPath("$.projects[1].slug").value("b"))
                .andExpect(jsonPath("$.projects[1].title").value("Proyecto b"))
                .andExpect(jsonPath("$.projects[1].eyebrow").value("Valle"))
                .andExpect(jsonPath("$.projects[1].description").value("Texto"))
                .andExpect(jsonPath("$.projects[1].cover.alt").value("Portada"))
                .andExpect(jsonPath("$.projects[1].cover.width").value(400))
                .andExpect(jsonPath("$.projects[1].cover.crop.x").value(0.1))
                .andExpect(jsonPath("$.projects[1].cover.crop.width").value(0.6))
                .andExpect(jsonPath("$.projects[1].cover.sources").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertNoInternalIds(json, a, b, first, second, third);
    }

    @Test
    void publicOrderIsThePublishedPositionNotTheDraftOne() throws Exception {
        JsonNode a = create(body("a", null, first));
        JsonNode b = create(body("b", null, second));
        publish(a);
        publish(b);
        // Orden de borrador invertido sin publicar el orden: el público no cambia (D-15).
        String order = "{\"projects\":[" + entry(b) + "," + entry(a) + "]}";
        mvc.perform(put(PROJECTS + "/order").with(session(token)).with(csrf()).contentType(APPLICATION_JSON)
                .content(order)).andExpect(status().isOk());

        mvc.perform(get(PAGE))
                .andExpect(jsonPath("$.projects[0].slug").value("a"))
                .andExpect(jsonPath("$.projects[1].slug").value("b"));
    }

    @Test
    void categoriesAreDistinctPublishedLabelsInSpanishAlphabeticalOrder() throws Exception {
        String bodas = category("Bodas");
        String ñu = category("Ñandú");
        String niños = category("Niños");
        String ocultas = category("Zeta sin publicar");
        publish(create(withCategory("a", first, ñu)));
        publish(create(withCategory("b", second, bodas)));
        publish(create(withCategory("c", third, bodas)));
        publish(create(withCategory("d", first, niños)));
        publish(create(body("sin-categoria", null, first)));
        create(withCategory("e", first, ocultas));        // borrador sin publicar: su categoría no cuenta

        mvc.perform(get(PAGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[0]").value("Bodas"))
                .andExpect(jsonPath("$.categories[1]").value("Niños"))
                .andExpect(jsonPath("$.categories[2]").value("Ñandú"))
                .andExpect(jsonPath("$.projects[4].category").value(nullValue()));
    }

    @Test
    void exposesOnlyPublishedHeadings() throws Exception {
        Section header = headings.update(PageKey.PORTFOLIO, "page-header", "Portafolio", "Historias reales",
                "Bodas y retratos.", 0, adminId);
        sectionPublication.publish(PageKey.PORTFOLIO, "page-header", header.getDraftRevision(), adminId);
        headings.update(PageKey.PORTFOLIO, "closing-cta", null, "Solo borrador", null, 0, adminId);

        mvc.perform(get(PAGE))
                .andExpect(jsonPath("$.pageHeader.eyebrow").value("Portafolio"))
                .andExpect(jsonPath("$.pageHeader.title").value("Historias reales"))
                .andExpect(jsonPath("$.pageHeader.body").value("Bodas y retratos."))
                .andExpect(jsonPath("$.closingCta").value(nullValue()));
    }

    /** Editar el encabezado publicado sin volver a publicarlo no cambia lo que ve el público. */
    @Test
    void editedButUnpublishedHeaderKeepsThePublishedText() throws Exception {
        Section header = headings.update(PageKey.PORTFOLIO, "page-header", "Portafolio", "Historias reales",
                "Bodas y retratos.", 0, adminId);
        Section published = sectionPublication.publish(PageKey.PORTFOLIO, "page-header", header.getDraftRevision(),
                adminId);
        headings.update(PageKey.PORTFOLIO, "page-header", "Otro", "Título privado", "Texto privado",
                published.getDraftRevision(), adminId);

        mvc.perform(get(PAGE))
                .andExpect(jsonPath("$.pageHeader.eyebrow").value("Portafolio"))
                .andExpect(jsonPath("$.pageHeader.title").value("Historias reales"))
                .andExpect(jsonPath("$.pageHeader.body").value("Bodas y retratos."));
    }

    @Test
    void detailReturnsGalleryInOrderWithCaptionsAndNoCrop() throws Exception {
        String request = "{\"slug\":\"boda-ana\",\"title\":\"Boda de Ana\",\"eyebrow\":\"Valle\","
                + "\"description\":\"Texto\",\"cover\":{\"mediaId\":\"" + first + "\",\"alt\":\"Portada\"},"
                + "\"images\":[{\"mediaId\":\"" + second + "\",\"alt\":\"Uno\",\"caption\":\"Pie uno\"},"
                + "{\"mediaId\":\"" + third + "\",\"alt\":\"Dos\"}]}";
        JsonNode project = create(request);
        publish(project);

        String json = mvc.perform(get(DETAIL, "boda-ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("boda-ana"))
                .andExpect(jsonPath("$.title").value("Boda de Ana"))
                .andExpect(jsonPath("$.eyebrow").value("Valle"))
                .andExpect(jsonPath("$.description").value("Texto"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].image.alt").value("Uno"))
                .andExpect(jsonPath("$.images[0].image.width").value(400))
                .andExpect(jsonPath("$.images[0].image.height").value(500))
                .andExpect(jsonPath("$.images[0].image.crop").value(nullValue()))
                .andExpect(jsonPath("$.images[0].image.mediaPosition").value(nullValue()))
                .andExpect(jsonPath("$.images[0].image.sources").isNotEmpty())
                .andExpect(jsonPath("$.images[0].caption").value("Pie uno"))
                .andExpect(jsonPath("$.images[1].image.alt").value("Dos"))
                .andExpect(jsonPath("$.images[1].caption").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertNoInternalIds(json, project, first, second, third);
    }

    @Test
    void detailShowsThePublishedVersionNotTheDraft() throws Exception {
        JsonNode project = create(body("a", null, first));
        JsonNode published = publish(project);
        save(published, body("a", published.get("version").asLong(), second).replace("Proyecto a", "Cambio privado"));

        mvc.perform(get(DETAIL, "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Proyecto a"));
    }

    @Test
    void everyUnavailableSlugGetsTheSameNotFound() throws Exception {
        create(body("solo-borrador", null, first));
        JsonNode retired = create(body("retirado", null, second));
        JsonNode retiredPublished = publish(retired);
        JsonNode hidden = hide(retiredPublished);
        publish(hidden);                                   // retirado: instantánea eliminada
        JsonNode renamed = create(body("antiguo", null, third));
        JsonNode renamedPublished = publish(renamed);
        JsonNode renamedDraft = save(renamedPublished, body("nuevo", renamedPublished.get("version").asLong(), third));
        publish(renamedDraft);                             // slug antiguo ya no existe (D-13)
        mvc.perform(get(DETAIL, "nuevo")).andExpect(status().isOk());

        String reference = mvc.perform(get(DETAIL, "no-existe")).andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertThat(reference).isNotBlank();

        String[] slugs = {"solo-borrador", "retirado", "antiguo", "x".repeat(81), "Boda X", "a b", "x.y", "a--b"};
        for (String slug : slugs) {
            var response = mvc.perform(get(DETAIL, slug)).andReturn().getResponse();
            assertThat(response.getStatus()).as("estado para '%s'", slug).isEqualTo(404);
            assertThat(response.getContentAsString()).as("cuerpo para '%s'", slug).isEqualTo(reference);
            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).as("caché para '%s'", slug).contains("no-store");
        }
    }

    /** Review Focus 1: el slug nuevo del borrador no existe en público hasta publicarlo; el publicado sigue sirviéndose. */
    @Test
    void draftSlugChangeIsNotPublicUntilPublished() throws Exception {
        JsonNode published = publish(create(body("boda", null, first)));
        save(published, body("boda-2", published.get("version").asLong(), first));

        String reference = mvc.perform(get(DETAIL, "no-existe")).andReturn().getResponse().getContentAsString();
        mvc.perform(get(DETAIL, "boda-2")).andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(reference));
        mvc.perform(get(DETAIL, "boda")).andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("boda"));
    }

    /**
     * Un slug con barra codificada o salto de directorio (p. ej. {@code ../x}) lo rechaza el firewall de Spring
     * Security (y Tomcat) ANTES de llegar a la aplicación: 400 sin cuerpo ni cookies, igual que en toda la API.
     * No es validación de slug del portafolio; solo se fija que no sea un 500.
     */
    @Test
    void pathTraversalSlugsAreRejectedByTheFirewallWithoutCookies() throws Exception {
        mvc.perform(get(DETAIL, "../x"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void bothEndpointsAreNeverCachedAndNeverSetCookies() throws Exception {
        publish(create(body("a", null, first)));
        for (String url : new String[] {"/api/public/pages/portfolio", "/api/public/portfolio/projects/a"}) {
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void otherMethodsAreRejectedWithoutCookies() throws Exception {
        for (String url : new String[] {"/api/public/pages/portfolio", "/api/public/portfolio/projects/a"}) {
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    // --- Ayudas ---

    private static void assertNoInternalIds(String json, Object... ids) {
        for (Object id : ids) {
            String text = id instanceof JsonNode node ? node.get("id").asText() : id.toString();
            assertThat(json).as("la respuesta no debe contener %s", text).doesNotContain(text);
        }
    }

    private String category(String label) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO content_categories (id, label, created_at, updated_at) VALUES (?::uuid, ?, now(), now())",
                id, label);
        return id;
    }

    private JsonNode create(String body) throws Exception {
        String json = mvc.perform(post(PROJECTS).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode save(JsonNode project, String body) throws Exception {
        String json = mvc.perform(put(PROJECTS + "/" + project.get("id").asText()).with(session(token)).with(csrf())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode hide(JsonNode project) throws Exception {
        String json = mvc.perform(patch(PROJECTS + "/" + project.get("id").asText() + "/visibility").with(session(token))
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"hidden\":true,\"version\":" + project.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private JsonNode publish(JsonNode project) throws Exception {
        String json = mvc.perform(put(PROJECTS + "/" + project.get("id").asText() + "/publication").with(session(token))
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"version\":" + project.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private String entry(JsonNode project) throws Exception {
        String json = mvc.perform(get(PROJECTS + "/" + project.get("id").asText()).with(session(token)))
                .andReturn().getResponse().getContentAsString();
        return "{\"id\":\"" + project.get("id").asText() + "\",\"version\":" + objectMapper.readTree(json).get("version").asLong() + "}";
    }

    private static String body(String slug, Long version, UUID... images) {
        String gallery = IntStream.range(0, images.length)
                .mapToObj(i -> "{\"mediaId\":\"" + images[i] + "\",\"alt\":\"alt " + i + "\"}")
                .collect(Collectors.joining(","));
        return "{\"slug\":\"" + slug + "\",\"title\":\"Proyecto " + slug + "\",\"cover\":{\"mediaId\":\"" + images[0]
                + "\",\"alt\":\"Portada\"},\"images\":[" + gallery + "]" + (version == null ? "" : ",\"version\":" + version)
                + "}";
    }

    private String withCategory(String slug, UUID image, String categoryId) {
        return body(slug, null, image).replaceFirst("\\{", "{\"categoryId\":\"" + categoryId + "\",");
    }

    private String coverWithCrop(String slug) {
        return "{\"slug\":\"" + slug + "\",\"title\":\"Proyecto " + slug + "\",\"eyebrow\":\"Valle\","
                + "\"description\":\"Texto\",\"cover\":{\"mediaId\":\"" + first + "\",\"alt\":\"Portada\","
                + "\"crop\":{\"x\":0.1,\"y\":0,\"width\":0.6,\"height\":1}},\"images\":[{\"mediaId\":\"" + second
                + "\",\"alt\":\"Uno\"}]}";
    }
}
