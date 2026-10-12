package com.kinich.media;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.auth.TokenService;
import com.kinich.media.image.ImageFixtures;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.support.MediaDirExtension;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class MediaApiIntegrationTest {

    private static final String CSP = "default-src 'none'; frame-ancestors 'none'; sandbox";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;

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

    private static MockMultipartFile file(byte[] content) {
        // Nombre y tipo engañosos a propósito: el servidor no debe usarlos.
        return new MockMultipartFile("file", "../../etc/foto.svg", "image/svg+xml", content);
    }

    /** Sube y devuelve el id del archivo web JPEG más pequeño (identidad HTTP de lo que se sirve). */
    private String uploadWebFile(byte[] content) throws Exception {
        String json = mvc.perform(multipart("/api/admin/media").file(file(content)).with(session(token)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json)
                .at("/renditions/fallback/candidates/0/url").asText();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    void uploadRequiresSessionAndCsrfHeader() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.png(2, 2))).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.png(2, 2))).with(session(token)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.png(2, 2))).param("_csrf", "x")
                        .with(session(token)))
                .andExpect(status().isForbidden()); // el parámetro _csrf ya no se acepta
        mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.png(2, 2))))
                .andExpect(status().isForbidden()); // anónimo sin cabecera
    }

    @Test
    void uploadReturnsMetadataWithoutInternalDetails() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.jpeg(30, 20))).with(session(token)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(30))
                .andExpect(jsonPath("$.height").value(20))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.renditions.sources[0].type").value("image/avif"))
                .andExpect(jsonPath("$.renditions.sources[0].candidates[0].width").value(30))
                .andExpect(jsonPath("$.renditions.fallback.type").value("image/jpeg"))
                .andExpect(jsonPath("$.renditions.fallback.candidates[0].url")
                        .value(org.hamcrest.Matchers.startsWith("/api/admin/media/files/")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("webp"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("etc"))));
    }

    @Test
    void invalidContentIs400AndTooLargeIs413BothWithValidationFailed() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(file("<svg/>".getBytes(StandardCharsets.UTF_8)))
                        .with(session(token)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.file").exists());
        mvc.perform(multipart("/api/admin/media").file(file(new byte[(int) MediaService.MAX_BYTES + 1]))
                        .with(session(token)).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(multipart("/api/admin/media").with(session(token)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void adminContentHasControlledHeadersAndRevalidates() throws Exception {
        String id = uploadWebFile(ImageFixtures.png(3, 3));
        var response = mvc.perform(get("/api/admin/media/files/" + id).with(session(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("ETag", "\"" + id + "\""))
                .andReturn().getResponse();
        assertThat(response.getHeaders("Content-Security-Policy")).containsExactly(CSP);
        mvc.perform(get("/api/admin/media/files/" + id).with(session(token)).header("If-None-Match", "\"" + id + "\""))
                .andExpect(status().isNotModified());
        mvc.perform(get("/api/admin/media/files/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicMediaIsA404UnlessPublished() throws Exception {
        String id = uploadWebFile(ImageFixtures.png(3, 3));
        mvc.perform(get("/api/public/media/files/" + id)).andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/public/media/files/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/media/files/no-es-uuid")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/media/files/../../etc/passwd")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/public/media/files/" + id)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listsUploadedMediaNewestFirst() throws Exception {
        String first = uploadWebFile(ImageFixtures.png(3, 3));
        String second = uploadWebFile(ImageFixtures.png(4, 4));
        mvc.perform(get("/api/admin/media").with(session(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].renditions.fallback.candidates[0].url").value("/api/admin/media/files/" + second))
                .andExpect(jsonPath("$[1].renditions.fallback.candidates[0].url").value("/api/admin/media/files/" + first));
    }

    @Test
    void aRealStorageFailureLogsNoPath(CapturedOutput output) throws Exception {
        var original = Files.getPosixFilePermissions(MediaDirExtension.DIR);
        Files.setPosixFilePermissions(MediaDirExtension.DIR, PosixFilePermissions.fromString("r-x------"));
        try {
            mvc.perform(multipart("/api/admin/media").file(file(ImageFixtures.png(2, 2))).with(session(token)).with(csrf()))
                    .andExpect(status().isInternalServerError());
        } finally {
            Files.setPosixFilePermissions(MediaDirExtension.DIR, original);
        }
        assertThat(output).doesNotContain(MediaDirExtension.DIR.toString()).doesNotContain("upload-");
    }
}
