package com.kinich.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {

    /** Evita que la contraseña aparezca en logs o trazas si el objeto se imprime. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
