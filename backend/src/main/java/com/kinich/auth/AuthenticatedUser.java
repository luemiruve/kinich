package com.kinich.auth;

import java.util.UUID;

/**
 * Principal de las peticiones autenticadas. Se construye a partir del usuario en base de datos,
 * no solo de los claims del token.
 */
public record AuthenticatedUser(UUID id, String email, String fullName) {
}
