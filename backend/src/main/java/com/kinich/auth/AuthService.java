package com.kinich.auth;

import com.kinich.user.User;
import com.kinich.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = "kinich.security")
@Service
public class AuthService {

    /** BCrypt solo considera los primeros 72 bytes; algo más largo no puede ser una contraseña válida. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final TokenService tokenService;
    /** Hash señuelo para que un correo inexistente tarde lo mismo que una contraseña incorrecta. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       LoginRateLimiter rateLimiter, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("kinich-dummy-password");
    }

    /**
     * Verifica credenciales y emite un token. Cualquier fallo responde igual para no revelar
     * si el correo existe.
     */
    @Transactional(readOnly = true)
    public LoginResult login(String rawEmail, String password, String clientIp) {
        String email = User.normalizeEmail(rawEmail);
        // El intento se cuenta antes de BCrypt: las peticiones en paralelo no pueden superar el límite.
        LoginRateLimiter.Admission admission = rateLimiter.tryAcquire(email, clientIp);
        if (admission != LoginRateLimiter.Admission.ADMITTED) {
            // La saturación ya se avisa una vez por ventana en el limitador; aquí solo los límites alcanzados.
            if (admission == LoginRateLimiter.Admission.LIMITED) {
                log.warn("Inicio de sesión bloqueado por exceso de intentos (ip={})", clientIp);
            }
            throw new TooManyLoginAttemptsException(rateLimiter.window());
        }

        Optional<User> user = userRepository.findByEmail(email).filter(User::isEnabled);
        boolean valid = password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES
                && passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash))
                && user.isPresent();

        if (!valid) {
            log.warn("Inicio de sesión fallido (ip={})", clientIp);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        User authenticated = user.get();
        rateLimiter.reset(email);
        log.info("Inicio de sesión correcto (userId={}, ip={})", authenticated.getId(), clientIp);
        return new LoginResult(tokenService.issue(authenticated.getId()), toPrincipal(authenticated));
    }

    static AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName());
    }

    public record LoginResult(String token, AuthenticatedUser user) {
    }
}
