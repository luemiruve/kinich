package com.kinich.shared.error;

import com.kinich.auth.TooManyLoginAttemptsException;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores en formato ProblemDetail (RFC 9457). Nunca se devuelven trazas, SQL ni mensajes internos.
 */
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";

    @ExceptionHandler(ApiProblemException.class)
    ProblemDetail handleApiProblem(ApiProblemException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (ex.getCode() != null) {
            problem.setProperty(CODE, ex.getCode().name());
        }
        if (!ex.getFieldErrors().isEmpty()) {
            problem.setProperty("errors", ex.getFieldErrors());
        }
        return problem;
    }

    /** Dos escrituras concurrentes sobre la misma fila (p. ej. la sección): gana la primera. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return handleApiProblem(ApiProblemException.versionConflict());
    }

    /** Interbloqueo o fallo de bloqueo entre transacciones (40P01 y similares): se trata como conflicto de versión. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail handlePessimisticLock(PessimisticLockingFailureException ex) {
        return handleApiProblem(ApiProblemException.versionConflict());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyLoginAttemptsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos. Espera unos minutos antes de volver a intentarlo.");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfter().toSeconds()))
                .body(problem);
    }

    /** Lista los campos inválidos sin devolver los valores enviados. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La solicitud contiene datos inválidos.");
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(e -> e.getField(), e -> String.valueOf(e.getDefaultMessage()), (a, b) -> a));
        problem.setProperty("errors", errors);
        problem.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
        return ResponseEntity.badRequest().body(problem);
    }

    /** Errores de formato y similares de Spring MVC: se conserva el estado pero sin el detalle técnico. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null) {
                problem.setDetail("La solicitud no se pudo procesar.");
            }
            // Todo 400 de Spring MVC (JSON mal formado, parámetro o parte ausente, tipo erróneo) y todo 413 (multipart
            // demasiado grande) son errores de validación.
            if (status.value() == HttpStatus.BAD_REQUEST.value() || status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
                problem.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
            }
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    /** Se delegan a Spring Security para que respondan 401/403 en lugar de 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
    }
}
