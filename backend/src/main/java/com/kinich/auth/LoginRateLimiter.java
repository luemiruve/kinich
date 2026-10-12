package com.kinich.auth;

import com.kinich.config.KinichProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de inicio de sesión por correo y por IP con una ventana fija en memoria.
 * <p>
 * El intento se cuenta antes de comprobar la contraseña y de forma atómica, así que las peticiones
 * en paralelo no pueden superar el límite. El número de claves tiene un tope: con el mapa lleno se
 * descartan primero las ventanas vencidas y, si sigue lleno, se rechazan las claves nuevas (las ya
 * registradas siguen contando). Nunca se desalojan contadores vigentes, para que llenar el mapa no
 * sirva para borrar el contador de una cuenta atacada.
 * <p>
 * Suficiente para una sola instancia; si el backend se replica habrá que moverlo a un almacén compartido
 * o al proxy (p. ej. Cloudflare, como indica la documentación).
 */
@Slf4j(topic = "kinich.security")
@Component
public class LoginRateLimiter {

    private final Map<String, Window> attempts = new HashMap<>();
    private final KinichProperties.LoginRateLimit limits;
    private final Clock clock;
    /** Hasta cuándo no puede vencer ninguna ventana; evita recorrer el mapa en cada intento con el mapa lleno. */
    private Instant nextExpiry = Instant.MIN;
    private Instant lastSaturationWarning;

    public LoginRateLimiter(KinichProperties properties, Clock clock) {
        this.limits = properties.loginRateLimit();
        this.clock = clock;
    }

    public enum Admission {
        ADMITTED,
        /** El correo o la IP alcanzaron su límite en la ventana. */
        LIMITED,
        /** El mapa está lleno y el correo o la IP no tenían contador. */
        SATURATED
    }

    /**
     * Registra un intento de inicio de sesión si el correo y la IP están por debajo de su límite.
     */
    public synchronized Admission tryAcquire(String email, String ip) {
        Instant now = clock.instant();
        String emailKey = emailKey(email);
        String ipKey = ipKey(ip);
        if (count(emailKey, now) >= limits.maxFailuresPerEmail() || count(ipKey, now) >= limits.maxFailuresPerIp()) {
            return Admission.LIMITED;
        }
        if (!hasRoomFor(newKeys(emailKey, ipKey), now)) {
            warnSaturated(now);
            return Admission.SATURATED;
        }
        increment(emailKey, now);
        increment(ipKey, now);
        return Admission.ADMITTED;
    }

    /** Un acceso correcto limpia el contador del correo, no el de la IP. */
    public synchronized void reset(String email) {
        attempts.remove(emailKey(email));
    }

    public Duration window() {
        return limits.window();
    }

    synchronized int trackedKeys() {
        return attempts.size();
    }

    private int count(String key, Instant now) {
        Window window = attempts.get(key);
        return window == null || window.isExpired(now, limits.window()) ? 0 : window.count();
    }

    /** Una clave con su ventana vencida se reutiliza en su sitio, así que no ocupa una entrada nueva. */
    private int newKeys(String emailKey, String ipKey) {
        return (attempts.containsKey(emailKey) ? 0 : 1) + (attempts.containsKey(ipKey) ? 0 : 1);
    }

    private boolean hasRoomFor(int newKeys, Instant now) {
        if (attempts.size() + newKeys <= limits.maxTrackedKeys()) {
            return true;
        }
        if (!now.isBefore(nextExpiry)) {
            evictExpired(now);
        }
        return attempts.size() + newKeys <= limits.maxTrackedKeys();
    }

    private void evictExpired(Instant now) {
        attempts.values().removeIf(window -> window.isExpired(now, limits.window()));
        nextExpiry = attempts.values().stream()
                .map(window -> window.start().plus(limits.window()))
                .min(Instant::compareTo)
                .orElse(Instant.MIN);
    }

    private void increment(String key, Instant now) {
        attempts.compute(key, (k, current) -> current == null || current.isExpired(now, limits.window())
                ? new Window(now, 1)
                : new Window(current.start(), current.count() + 1));
    }

    private void warnSaturated(Instant now) {
        if (lastSaturationWarning == null || !now.isBefore(lastSaturationWarning.plus(limits.window()))) {
            lastSaturationWarning = now;
            log.warn("Limitador de inicio de sesión saturado: se rechazan correos e IP nuevos ({} claves)",
                    attempts.size());
        }
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private record Window(Instant start, int count) {
        boolean isExpired(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}
