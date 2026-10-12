package com.kinich.settings;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@IntegrationTest
class SiteSettingsApiIntegrationTest {

    private static final String ADMIN = "/api/admin/settings";
    private static final String LINKS = ADMIN + "/social-links";
    private static final String PUBLIC = "/api/public/site";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper json;

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

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mvc.perform(request.with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(body));
    }

    private long version() throws Exception {
        return read(mvc.perform(get(ADMIN).with(session(token)))).get("version").asLong();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions identity(String fields) throws Exception {
        return send(put(ADMIN + "/identity"), "{" + fields + ",\"version\":" + version() + "}");
    }

    private ResultActions contact(String fields) throws Exception {
        return send(put(ADMIN + "/contact"), "{" + fields + ",\"version\":" + version() + "}");
    }

    private ResultActions createLink(String body) throws Exception {
        return send(post(LINKS), body);
    }

    @Test
    void aFreshInstallationHasNoIdentityContactOrLinks() throws Exception {
        mvc.perform(get(ADMIN).with(session(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityConfigured").value(false))
                .andExpect(jsonPath("$.identity.businessName").isEmpty())
                .andExpect(jsonPath("$.identity.logo").isEmpty())
                .andExpect(jsonPath("$.contact.email").isEmpty())
                .andExpect(jsonPath("$.socialLinks", hasSize(0)));
        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").isEmpty())
                .andExpect(jsonPath("$.logo").isEmpty())
                .andExpect(jsonPath("$.logoOnDark").isEmpty())
                .andExpect(jsonPath("$.contact.whatsapp").isEmpty())
                .andExpect(jsonPath("$.socialLinks", hasSize(0)));
    }

    @Test
    void savingTheIdentityAppliesImmediatelyWithoutPublication() throws Exception {
        identity("\"businessName\":\"  Estudio de Ejemplo \",\"tagline\":\"Fotografía y video\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.businessName").value("Estudio de Ejemplo"))
                // Sin logotipo principal la identidad aún no está completa (V10).
                .andExpect(jsonPath("$.identityConfigured").value(false));

        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Estudio de Ejemplo"))
                .andExpect(jsonPath("$.tagline").value("Fotografía y video"))
                .andExpect(jsonPath("$.logo").isEmpty());
    }

    @Test
    void identityIsValidated() throws Exception {
        identity("\"businessName\":\"   \"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.businessName").exists());
        identity("\"businessName\":\"Dos\\nlíneas\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.businessName").exists());
        identity("\"businessName\":\"" + "a".repeat(121) + "\"").andExpect(status().isBadRequest());
        identity("\"businessName\":\"Estudio\",\"tagline\":\"" + "a".repeat(201) + "\"").andExpect(status().isBadRequest());
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        long stale = version();
        identity("\"businessName\":\"Estudio\"").andExpect(status().isOk());
        send(put(ADMIN + "/contact"), "{\"email\":\"contacto@example.com\",\"version\":" + stale + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void contactIsNormalizedAndPublic() throws Exception {
        contact("\"email\":\" contacto@example.com \",\"whatsapp\":\"+1 (555) 555-0100\",\"phone\":\"+1 555 555 0199\","
                + "\"location\":\"Ciudad de ejemplo\",\"address\":\"Calle 1\\nInterior 2\","
                + "\"openingHours\":\"Lunes a viernes\\n10:00–18:00\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.email").value("contacto@example.com"))
                .andExpect(jsonPath("$.contact.whatsapp").value("+15555550100"))
                .andExpect(jsonPath("$.contact.phone").value("+15555550199"));

        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.whatsapp").value("+15555550100"))
                .andExpect(jsonPath("$.contact.address").value("Calle 1\nInterior 2"))
                .andExpect(jsonPath("$.contact.openingHours").value("Lunes a viernes\n10:00–18:00"));
    }

    @Test
    void contactIsValidatedAndEmptyFieldsAreCleared() throws Exception {
        contact("\"email\":\"no-es-un-correo\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
        contact("\"whatsapp\":\"5555550100\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.whatsapp").exists());
        contact("\"phone\":\"+0123456789\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists());
        contact("\"location\":\"" + "a".repeat(121) + "\"").andExpect(status().isBadRequest());

        contact("\"email\":\"contacto@example.com\"").andExpect(status().isOk());
        contact("\"email\":\"  \"").andExpect(status().isOk()).andExpect(jsonPath("$.contact.email").isEmpty());
    }

    @Test
    void socialLinksAreAnOrderedCollectionOfHttpsUrls() throws Exception {
        String instagram = read(createLink("{\"platform\":\"INSTAGRAM\",\"url\":\"https://www.instagram.com/ejemplo\","
                + "\"visible\":true}").andExpect(status().isCreated())).get("id").asText();
        createLink("{\"platform\":\"OTHER\",\"label\":\"Portafolio externo\",\"url\":\"https://portfolio.example.com\","
                + "\"visible\":true}").andExpect(status().isCreated());
        createLink("{\"platform\":\"VIMEO\",\"url\":\"https://vimeo.com/ejemplo\",\"visible\":false}")
                .andExpect(status().isCreated());

        mvc.perform(get(ADMIN).with(session(token)))
                .andExpect(jsonPath("$.socialLinks", hasSize(3)))
                .andExpect(jsonPath("$.socialLinks[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.socialLinks[1].label").value("Portafolio externo"));
        mvc.perform(get(PUBLIC))
                .andExpect(jsonPath("$.socialLinks", hasSize(2)))
                .andExpect(jsonPath("$.socialLinks[0].url").value("https://www.instagram.com/ejemplo"))
                .andExpect(jsonPath("$.socialLinks[0].id").doesNotExist());

        JsonNode links = read(mvc.perform(get(ADMIN).with(session(token)))).get("socialLinks");
        String order = "{\"items\":[" + ref(links.get(2)) + "," + ref(links.get(0)) + "," + ref(links.get(1)) + "]}";
        send(put(LINKS + "/order"), order).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN).with(session(token)))
                .andExpect(jsonPath("$.socialLinks[0].platform").value("VIMEO"))
                .andExpect(jsonPath("$.socialLinks[1].platform").value("INSTAGRAM"));
        send(put(LINKS + "/order"), order).andExpect(status().isConflict()); // versiones ya consumidas

        long linkVersion = read(mvc.perform(get(ADMIN).with(session(token)))).get("socialLinks").get(1).get("version").asLong();
        mvc.perform(delete(LINKS + "/" + instagram).param("version", String.valueOf(linkVersion))
                .with(session(token)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN).with(session(token))).andExpect(jsonPath("$.socialLinks", hasSize(2)))
                .andExpect(jsonPath("$.socialLinks[1].position").value(2));
    }

    private static String ref(JsonNode link) {
        return "{\"id\":\"" + link.get("id").asText() + "\",\"version\":" + link.get("version").asLong() + "}";
    }

    @Test
    void socialLinksAreValidated() throws Exception {
        createLink("{\"platform\":\"INSTAGRAM\",\"url\":\"http://www.instagram.com/ejemplo\",\"visible\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.url").exists());
        createLink("{\"platform\":\"INSTAGRAM\",\"url\":\"javascript:alert(1)\",\"visible\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.url").exists());
        createLink("{\"platform\":\"INSTAGRAM\",\"url\":\"https://usuario:clave@example.com\",\"visible\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.url").exists());
        createLink("{\"platform\":\"INSTAGRAM\",\"url\":\"https://\",\"visible\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.url").exists());
        createLink("{\"platform\":\"OTHER\",\"url\":\"https://example.com\",\"visible\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.label").exists());
        createLink("{\"platform\":\"MYSPACE\",\"url\":\"https://example.com\",\"visible\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void socialLinksHaveATechnicalMaximum() throws Exception {
        for (int i = 0; i < SocialLinkService.MAX_LINKS; i++) {
            createLink("{\"platform\":\"OTHER\",\"label\":\"Enlace " + i + "\",\"url\":\"https://example.com/" + i
                    + "\",\"visible\":true}").andExpect(status().isCreated());
        }
        createLink("{\"platform\":\"OTHER\",\"label\":\"Uno más\",\"url\":\"https://example.com\",\"visible\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAX_ITEMS_REACHED"));
    }

    @Test
    void administrationRequiresSessionAndCsrfAndThePublicApiIsReadOnly() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN + "/identity").with(session(token)).contentType(APPLICATION_JSON)
                .content("{\"businessName\":\"x\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post(PUBLIC).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
    }
}
