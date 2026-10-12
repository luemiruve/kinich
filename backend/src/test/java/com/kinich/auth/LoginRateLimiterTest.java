package com.kinich.auth;

import static com.kinich.auth.LoginRateLimiter.Admission.ADMITTED;
import static com.kinich.auth.LoginRateLimiter.Admission.LIMITED;
import static com.kinich.auth.LoginRateLimiter.Admission.SATURATED;
import static org.assertj.core.api.Assertions.assertThat;

import com.kinich.config.KinichProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoginRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String SATURATION_LOG = "Limitador de inicio de sesión saturado";

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-09T12:00:00Z"));
        limiter = limiter(3, 5, 10_000);
    }

    @Test
    void blocksEmailAfterMaxAttempts() {
        acquire("a@example.com", "1.1.1.1", 3);

        assertThat(limiter.tryAcquire("a@example.com", "2.2.2.2")).isEqualTo(LIMITED);
        assertThat(limiter.tryAcquire("b@example.com", "2.2.2.2")).isEqualTo(ADMITTED);
    }

    @Test
    void blocksIpAfterMaxAttemptsAcrossEmails() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("user" + i + "@example.com", "9.9.9.9")).isEqualTo(ADMITTED);
        }

        assertThat(limiter.tryAcquire("nuevo@example.com", "9.9.9.9")).isEqualTo(LIMITED);
    }

    @Test
    void rejectedAttemptsDoNotKeepCounting() {
        acquire("a@example.com", "1.1.1.1", 3);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a@example.com", "1.1.1.1");
        }

        // La IP solo lleva los 3 intentos admitidos: aún le quedan 2 con otros correos.
        assertThat(limiter.tryAcquire("b@example.com", "1.1.1.1")).isEqualTo(ADMITTED);
        assertThat(limiter.tryAcquire("c@example.com", "1.1.1.1")).isEqualTo(ADMITTED);
        assertThat(limiter.tryAcquire("d@example.com", "1.1.1.1")).isEqualTo(LIMITED);
    }

    @Test
    void windowExpiresExactlyAtItsEnd() {
        acquire("a@example.com", "1.1.1.1", 3);

        clock.advance(WINDOW.minusNanos(1));
        assertThat(limiter.tryAcquire("a@example.com", "1.1.1.1")).isEqualTo(LIMITED);

        clock.advance(Duration.ofNanos(1));
        assertThat(limiter.tryAcquire("a@example.com", "1.1.1.1")).isEqualTo(ADMITTED);
    }

    @Test
    void successfulLoginResetsEmailButNotIp() {
        acquire("a@example.com", "1.1.1.1", 3);
        limiter.reset("a@example.com");

        assertThat(limiter.tryAcquire("a@example.com", "3.3.3.3")).isEqualTo(ADMITTED);
        acquire("c@example.com", "1.1.1.1", 2);
        assertThat(limiter.tryAcquire("d@example.com", "1.1.1.1")).isEqualTo(LIMITED);
    }

    @Test
    void newKeysAreRejectedWhenFullAndNothingExpired() {
        limiter = limiter(3, 5, 4);
        fill(2);

        assertThat(limiter.tryAcquire("nuevo@example.com", "8.8.8.8")).isEqualTo(SATURATED);
        // Basta con que una de las dos claves sea nueva.
        assertThat(limiter.tryAcquire("user0@example.com", "8.8.8.8")).isEqualTo(SATURATED);
        assertThat(limiter.trackedKeys()).isEqualTo(4);
    }

    @Test
    void trackedKeysKeepWorkingWhenFull() {
        limiter = limiter(3, 5, 4);
        fill(2);

        assertThat(limiter.tryAcquire("user0@example.com", "10.0.0.0")).isEqualTo(ADMITTED);
        assertThat(limiter.tryAcquire("user0@example.com", "10.0.0.0")).isEqualTo(ADMITTED);
        assertThat(limiter.tryAcquire("user0@example.com", "10.0.0.0")).isEqualTo(LIMITED);
    }

    @Test
    void fullMapAdmitsNewKeysOnceOldWindowsExpire() {
        limiter = limiter(3, 5, 4);
        fill(2);
        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("nuevo@example.com", "8.8.8.8")).isEqualTo(ADMITTED);
        // Se descartaron las 4 claves vencidas; solo quedan las 2 nuevas.
        assertThat(limiter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void expiredKeysAreReusedWithoutGrowingTheMap() {
        limiter = limiter(3, 5, 4);
        fill(2);
        clock.advance(WINDOW);

        assertThat(limiter.tryAcquire("user0@example.com", "10.0.0.0")).isEqualTo(ADMITTED);
        assertThat(limiter.trackedKeys()).isEqualTo(4);
    }

    @Test
    void sizeNeverExceedsTheCap() {
        limiter = limiter(3, 5, 50);

        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire("user" + i + "@example.com", "10.0." + (i / 250) + "." + (i % 250));
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(50);
    }

    @Test
    void saturationIsLoggedOncePerWindow(CapturedOutput output) {
        limiter = limiter(3, 5, 4);
        fill(2);

        limiter.tryAcquire("nuevo1@example.com", "8.8.8.1");
        limiter.tryAcquire("nuevo2@example.com", "8.8.8.2");
        assertThat(occurrences(output.getOut(), SATURATION_LOG)).isEqualTo(1);

        // Pasada la ventana se vacía y vuelve a saturarse: se avisa de nuevo.
        clock.advance(WINDOW);
        fill(2);
        limiter.tryAcquire("nuevo3@example.com", "8.8.8.3");
        assertThat(occurrences(output.getOut(), SATURATION_LOG)).isEqualTo(2);
    }

    @Test
    void concurrentAttemptsOnTheSameEmailAdmitExactlyTheLimit() throws Exception {
        limiter = limiter(5, 1_000, 10_000);

        assertThat(concurrently(50, i -> limiter.tryAcquire("admin@example.com", "10.0.0." + i) == ADMITTED)).isEqualTo(5);
    }

    @Test
    void concurrentAttemptsFromTheSameIpAdmitExactlyTheLimit() throws Exception {
        limiter = limiter(1_000, 20, 10_000);

        assertThat(concurrently(100, i -> limiter.tryAcquire("user" + i + "@example.com", "9.9.9.9") == ADMITTED)).isEqualTo(20);
    }

    @Test
    void concurrentNewKeysNeverExceedTheCap() throws Exception {
        limiter = limiter(5, 20, 10);

        concurrently(200, i -> limiter.tryAcquire("user" + i + "@example.com", "10.1.0." + i) == ADMITTED);

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);
    }

    private LoginRateLimiter limiter(int perEmail, int perIp, int maxTrackedKeys) {
        KinichProperties properties = new KinichProperties(null, null, List.of(),
                new KinichProperties.LoginRateLimit(perEmail, perIp, WINDOW, maxTrackedKeys), null);
        return new LoginRateLimiter(properties, clock);
    }

    private void acquire(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.tryAcquire(email, ip)).isEqualTo(ADMITTED);
        }
    }

    /** Ocupa {@code pairs * 2} claves: un correo y una IP por intento. */
    private void fill(int pairs) {
        for (int i = 0; i < pairs; i++) {
            assertThat(limiter.tryAcquire("user" + i + "@example.com", "10.0.0." + i)).isEqualTo(ADMITTED);
        }
    }

    /** Lanza todos los intentos a la vez y devuelve cuántos se admitieron. */
    private static int concurrently(int attempts, IntFunction<Boolean> attempt) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                int n = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    if (attempt.apply(n)) {
                        admitted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return admitted.get();
    }

    private static int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
