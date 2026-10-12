package com.kinich.user;

import com.kinich.config.KinichProperties;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Crea la cuenta del fotógrafo en el primer arranque. No existe registro público de usuarios,
 * así que esta es la única vía de alta. Si ya hay usuarios, no hace nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    /** BCrypt ignora lo que pase de 72 bytes. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KinichProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        KinichProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (!StringUtils.hasText(admin.email()) || userRepository.count() > 0) {
            return;
        }
        String password = admin.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("KINICH_ADMIN_PASSWORD debe tener al menos "
                    + MIN_PASSWORD_LENGTH + " caracteres y no más de " + MAX_PASSWORD_BYTES + " bytes");
        }
        String fullName = StringUtils.hasText(admin.fullName()) ? admin.fullName().strip() : "Administrador";
        User user = userRepository.save(new User(admin.email(), passwordEncoder.encode(password), fullName));
        log.info("Administrador inicial creado (id={})", user.getId());
    }
}
