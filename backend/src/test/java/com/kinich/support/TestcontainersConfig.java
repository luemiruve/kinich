package com.kinich.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL efímero con Testcontainers. Requiere Docker, por eso está desactivado por defecto:
 * mvn verify -Dkinich.test.testcontainers=true
 * Sin esa propiedad, las pruebas usan la base local definida en KINICH_TEST_DB_URL.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kinich.test.testcontainers", havingValue = "true")
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
