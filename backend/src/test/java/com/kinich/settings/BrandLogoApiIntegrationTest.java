package com.kinich.settings;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.auth.TokenService;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaDirExtension;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Logotipo SVG como recurso de identidad (V10), separado de MediaAsset: subida, reemplazo, retirada, entrega
 * pública segura y ausencia de archivos huérfanos.
 */
@IntegrationTest
class BrandLogoApiIntegrationTest {

    private static final String ADMIN = "/api/admin/settings";
    private static final String LOGOS = ADMIN + "/logos/";
    private static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; sandbox";
    private static final String SIMPLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- editor -->"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 240 60\"><path d=\"M0 0h240v60H0z\"/></svg>";
    private static final String THEMED = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 120 40\"><style>"
            + ".m{fill:#111}@media (prefers-color-scheme: dark){.m{fill:#eee}}</style>"
            + "<path class=\"m\" d=\"M0 0h120v40H0z\"/></svg>";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper json;

    private String token;
    private Set<String> filesBefore;

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        token = tokenService.issue(users.save(new User("admin@example.com", "{noop}x", "Administración")).getId());
        filesBefore = storedFiles();
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    private static Set<String> storedFiles() {
        try (Stream<Path> files = Files.list(MediaDirExtension.DIR)) {
            return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString())
                    .filter(name -> !name.startsWith("upload-")).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Los archivos nuevos del directorio son exactamente los de los recursos de marca referenciados: sin huérfanos. */
    private void assertNoOrphans() {
        Set<String> created = new HashSet<>(storedFiles());
        created.removeAll(filesBefore);
        Set<String> referenced = new HashSet<>(jdbc.queryForList("SELECT a.storage_key FROM brand_assets a "
                + "JOIN business_settings s ON a.id IN (s.logo_asset_id, s.logo_on_dark_asset_id)", String.class));
        assertThat(created).isEqualTo(referenced);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM brand_assets", Integer.class)).isEqualTo(referenced.size());
    }

    private long version() throws Exception {
        return settings().get("version").asLong();
    }

    private JsonNode settings() throws Exception {
        return json.readTree(mvc.perform(get(ADMIN).with(session(token))).andReturn().getResponse().getContentAsString());
    }

    private MockMultipartHttpServletRequestBuilder logoRequest(String variant, String svg, long version) {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT, LOGOS + variant);
        request.file(new MockMultipartFile("file", "../logo.png", "image/png", svg.getBytes(StandardCharsets.UTF_8)));
        request.param("version", Long.toString(version));
        return request;
    }

    private ResultActions upload(String variant, String svg) throws Exception {
        return mvc.perform(logoRequest(variant, svg, version()).with(session(token)).with(csrf()));
    }

    private String logoUrl(String variant, String svg) throws Exception {
        String body = upload(variant, svg).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("primary".equals(variant) ? "/identity/logo/url" : "/identity/logoOnDark/url").asText();
    }

    @Test
    void aPrimaryLogoIsStoredSanitizedAndCompletesTheIdentity() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(ADMIN + "/identity")
                .with(session(token)).with(csrf()).contentType("application/json")
                .content("{\"businessName\":\"Estudio de Ejemplo\",\"version\":" + version() + "}")).andExpect(status().isOk());

        upload("primary", SIMPLE).andExpect(status().isOk())
                .andExpect(jsonPath("$.identityConfigured").value(true))
                .andExpect(jsonPath("$.identity.logo.url", startsWith("/api/public/brand-assets/")))
                .andExpect(jsonPath("$.identity.logo.width").value(240.0))
                .andExpect(jsonPath("$.identity.logo.height").value(60.0))
                .andExpect(jsonPath("$.identity.logoOnDark").isEmpty());

        String stored = Files.readString(MediaDirExtension.DIR.resolve(
                jdbc.queryForObject("SELECT storage_key FROM brand_assets", String.class)));
        assertThat(stored).isEqualTo("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 240 60\">"
                + "<path d=\"M0 0h240v60H0z\"></path></svg>");
        mvc.perform(get("/api/public/site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.logo.url", startsWith("/api/public/brand-assets/")))
                .andExpect(jsonPath("$.logoOnDark").isEmpty());
        assertNoOrphans();
    }

    @Test
    void thePublicLogoIsServedAsAnImmutableSandboxedImage() throws Exception {
        String url = logoUrl("primary", THEMED);

        byte[] body = mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/svg+xml"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(content().string(containsString("prefers-color-scheme: dark")))
                .andReturn().getResponse().getContentAsByteArray();
        String etag = "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)) + "\"";
        mvc.perform(get(url)).andExpect(header().string(HttpHeaders.ETAG, etag));
        mvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());
    }

    @Test
    void theOnDarkVariantIsOptionalAndRequiresThePrimary() throws Exception {
        upload("on-dark", SIMPLE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors.logo").exists());
        assertNoOrphans();

        logoUrl("primary", SIMPLE);
        upload("on-dark", THEMED).andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.logo.width").value(240.0))
                .andExpect(jsonPath("$.identity.logoOnDark.width").value(120.0))
                .andExpect(jsonPath("$.identity.logoOnDark.height").value(40.0));
        mvc.perform(get("/api/public/site"))
                .andExpect(jsonPath("$.logoOnDark.url", startsWith("/api/public/brand-assets/")));

        mvc.perform(delete(LOGOS + "primary").param("version", Long.toString(version())).with(session(token)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.logo").exists());
        mvc.perform(delete(LOGOS + "on-dark").param("version", Long.toString(version())).with(session(token)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identity.logoOnDark").isEmpty());
        mvc.perform(delete(LOGOS + "primary").param("version", Long.toString(version())).with(session(token)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identity.logo").isEmpty())
                .andExpect(jsonPath("$.identityConfigured").value(false));
        assertNoOrphans();
    }

    @Test
    void replacingALogoCreatesANewUrlAndRetiresThePreviousOneAndItsFile() throws Exception {
        String first = logoUrl("primary", SIMPLE);
        String firstKey = jdbc.queryForObject("SELECT storage_key FROM brand_assets", String.class);

        String second = logoUrl("primary", THEMED);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(status().isOk());
        assertThat(MediaDirExtension.DIR.resolve(firstKey)).doesNotExist();
        assertNoOrphans();
    }

    @Test
    void onlyAReferencedBrandAssetIsServedAndNothingRevealsTheStorage() throws Exception {
        String url = logoUrl("primary", SIMPLE);
        String key = jdbc.queryForObject("SELECT storage_key FROM brand_assets", String.class);
        UUID loose = UUID.randomUUID();
        Files.writeString(MediaDirExtension.DIR.resolve(loose.toString()), SIMPLE);
        try {
            jdbc.update("INSERT INTO brand_assets (id, content_type, storage_key, size_bytes, sha256, view_box_width, "
                    + "view_box_height, created_at, updated_at) VALUES (?, 'image/svg+xml', ?, 10, ?, 1, 1, now(), now())",
                    loose, loose.toString(), "0".repeat(64));

            mvc.perform(get("/api/public/brand-assets/" + loose)).andExpect(status().isNotFound());
            mvc.perform(get("/api/public/brand-assets/" + UUID.randomUUID())).andExpect(status().isNotFound());
            mvc.perform(get("/api/public/brand-assets/no-es-un-id")).andExpect(status().isNotFound());
            mvc.perform(get("/api/public/brand-assets/" + key)).andExpect(status().isNotFound());
            for (String path : List.of("/api/public/site", ADMIN)) {
                mvc.perform(get(path).with(session(token)))
                        .andExpect(content().string(not(containsString(key))))
                        .andExpect(content().string(not(containsString(MediaDirExtension.DIR.toString()))));
            }
            assertThat(url).isEqualTo("/api/public/brand-assets/"
                    + jdbc.queryForObject("SELECT logo_asset_id FROM business_settings", UUID.class));
        } finally {
            jdbc.update("DELETE FROM brand_assets WHERE id = ?", loose);
            Files.deleteIfExists(MediaDirExtension.DIR.resolve(loose.toString()));
        }
    }

    @Test
    void anInvalidOrTooLargeSvgIsRejectedWithoutLeavingFiles() throws Exception {
        upload("primary", SIMPLE.replace("<path", "<script>alert(1)</script><path")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.file", containsString("script")))
                .andExpect(content().string(not(containsString("alert"))));
        upload("primary", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><title>"
                + "a".repeat(524_288) + "</title></svg>").andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        upload("primary", "").andExpect(status().isBadRequest());
        assertNoOrphans();
    }

    @Test
    void aStaleVersionIsAConflictAndLeavesNoFiles() throws Exception {
        long stale = version();
        logoUrl("primary", SIMPLE);

        mvc.perform(logoRequest("primary", THEMED, stale).with(session(token)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(delete(LOGOS + "primary").param("version", Long.toString(stale)).with(session(token)).with(csrf()))
                .andExpect(status().isConflict());
        assertNoOrphans();
    }

    @Test
    void concurrentReplacementsWithTheSameVersionLetOnlyOneWinAndLeaveNoOrphans() throws Exception {
        long version = version();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> replace = () -> {
            start.await();
            return mvc.perform(logoRequest("primary", THEMED, version).with(session(token)).with(csrf()))
                    .andReturn().getResponse().getStatus();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(pool.submit(replace), pool.submit(replace));
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertNoOrphans();
    }

    @Test
    void logosNeedSessionAndCsrfAndUnknownVariantsDoNotExist() throws Exception {
        mvc.perform(logoRequest("primary", SIMPLE, 0).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(logoRequest("primary", SIMPLE, 0).with(session(token))).andExpect(status().isForbidden());
        mvc.perform(logoRequest("dark", SIMPLE, version()).with(session(token)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/public/brand-assets/" + UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
        assertNoOrphans();
    }

    @Test
    void theGeneralMediaLibraryStillRejectsSvg() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                        SIMPLE.getBytes(StandardCharsets.UTF_8))).with(session(token)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_assets", Integer.class)).isZero();
    }
}
