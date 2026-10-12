package com.kinich.content;

import static com.kinich.support.ApiRequests.csrf;
import static com.kinich.support.ApiRequests.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.auth.TokenService;
import com.kinich.shared.error.ApiProblemException;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ContentCategoryApiIntegrationTest {

    private static final String URL = "/api/admin/content-categories";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokenService;
    @Autowired private ContentCategoryService service;

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

    private org.springframework.test.web.servlet.ResultActions create(String json) throws Exception {
        return mvc.perform(post(URL).with(session(token)).with(csrf()).contentType(APPLICATION_JSON).content(json));
    }

    @Test
    void createsAndListsSortedByLabel() throws Exception {
        create("{\"label\":\"  Infantil & familia \"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Infantil & familia"));
        create("{\"label\":\"Cobertura social\"}").andExpect(status().isCreated());
        mvc.perform(get(URL).with(session(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Cobertura social"))
                .andExpect(jsonPath("$[1].label").value("Infantil & familia"));
    }

    @Test
    void rejectsDuplicatesIgnoringCaseBlankAndTooLong() throws Exception {
        create("{\"label\":\"Boda\"}").andExpect(status().isCreated());
        create("{\"label\":\"BODA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors.label").exists());
        create("{\"label\":\"   \"}").andExpect(status().isBadRequest());
        create("{\"label\":\"" + "a".repeat(61) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void requiresSessionAndCsrf() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).with(session(token)).contentType(APPLICATION_JSON).content("{\"label\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentDuplicatesNeverFailWith500() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.create("Retrato", UUID.randomUUID());
                        return true;
                    } catch (ApiProblemException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (Future<Boolean> result : results) {
                created += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(created).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
