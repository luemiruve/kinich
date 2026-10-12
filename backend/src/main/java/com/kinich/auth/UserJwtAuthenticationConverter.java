package com.kinich.auth;

import com.kinich.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT válido en el usuario autenticado consultando la base de datos.
 * Así, desactivar o eliminar un usuario invalida sus tokens de inmediato.
 */
@Component
@RequiredArgsConstructor
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Token inválido");
        }
        AuthenticatedUser principal = userRepository.findById(userId)
                .filter(user -> user.isEnabled())
                .map(AuthService::toPrincipal)
                .orElseThrow(() -> new InvalidBearerTokenException("Token inválido"));
        // Sin roles en esta etapa: el único usuario es el fotógrafo (ver docs/analisis-inicial.md).
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, List.of());
    }
}
