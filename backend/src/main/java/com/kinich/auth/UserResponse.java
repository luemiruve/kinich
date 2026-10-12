package com.kinich.auth;

/**
 * Datos del usuario expuestos al frontend. No incluye el id ni nada relacionado con credenciales.
 */
public record UserResponse(String email, String fullName) {

    static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(user.email(), user.fullName());
    }
}
