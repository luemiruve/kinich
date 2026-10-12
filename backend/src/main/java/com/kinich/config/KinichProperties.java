package com.kinich.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia de Kinich. Los valores sensibles llegan siempre por variables de entorno.
 */
@Validated
@ConfigurationProperties(prefix = "kinich")
public record KinichProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookie cookie,
        @NotNull List<String> corsAllowedOrigins,
        @Valid @NotNull LoginRateLimit loginRateLimit,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin) {

    /**
     * @param secret clave HMAC en Base64; al menos 256 bits
     * @param ttl    vigencia del token; no hay refresh token, al vencer se vuelve a iniciar sesión
     */
    public record Jwt(@NotBlank String secret, @NotNull Duration ttl, @NotBlank String issuer) {
    }

    /**
     * @param secure debe ser {@code true} fuera de desarrollo local (HTTPS obligatorio en producción)
     */
    public record Cookie(@NotBlank String name, boolean secure) {
    }

    /**
     * Intentos de inicio de sesión permitidos dentro de la ventana, por correo y por IP.
     *
     * @param maxTrackedKeys tope de claves (correos + IP) en memoria; al alcanzarlo se rechazan claves nuevas
     */
    public record LoginRateLimit(
            @Min(1) int maxFailuresPerEmail,
            @Min(1) int maxFailuresPerIp,
            @NotNull Duration window,
            @Min(2) int maxTrackedKeys) {
    }

    /**
     * Crea el primer administrador si la tabla de usuarios está vacía. Sin correo, no hace nada.
     */
    public record BootstrapAdmin(String email, String password, @Size(max = 120) String fullName) {
    }
}
