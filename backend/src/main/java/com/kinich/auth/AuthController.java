package com.kinich.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;
    private final CsrfTokenRepository csrfTokenRepository;

    @PostMapping("/login")
    ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                       HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(), http.getRemoteAddr());
        renewCsrfToken(http, response);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.session(result.token()).toString())
                .body(UserResponse.from(result.user()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        renewCsrfToken(http, response);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.cleared().toString())
                .build();
    }

    /** Al cambiar de identidad, el token CSRF anterior deja de servir; durante la sesión no se rota. */
    private void renewCsrfToken(HttpServletRequest http, HttpServletResponse response) {
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(http), http, response);
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserResponse.from(user);
    }
}
