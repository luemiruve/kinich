package com.kinich.shared.audit;

import com.kinich.auth.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing
class AuditConfig {

    /**
     * El auditor es el usuario autenticado. Las operaciones del sistema (p. ej. el arranque) quedan sin auditor.
     */
    @Bean
    AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .filter(AuthenticatedUser.class::isInstance)
                .map(principal -> ((AuthenticatedUser) principal).id());
    }
}
