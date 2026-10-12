package com.kinich.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void versionConflictCarriesItsCode() {
        ProblemDetail problem = handler.handleApiProblem(ApiProblemException.versionConflict());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "VERSION_CONFLICT");
    }

    @Test
    void validationCarriesFieldErrorsWithoutValues() {
        ProblemDetail problem = handler.handleApiProblem(
                ApiProblemException.validation(Map.of("body", "contiene caracteres no permitidos")));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties())
                .containsEntry("code", "VALIDATION_FAILED")
                .containsEntry("errors", Map.of("body", "contiene caracteres no permitidos"));
    }

    @Test
    void notFoundHasNoCode() {
        ProblemDetail problem = handler.handleApiProblem(ApiProblemException.notFound());

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties()).isNull();
    }

    @Test
    void optimisticLockFailureIsAVersionConflict() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Section", "id"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "VERSION_CONFLICT");
        assertThat(problem.getDetail()).doesNotContain("Section");
    }

    @Test
    void pessimisticLockFailureIsAVersionConflict() {
        ProblemDetail problem = handler.handlePessimisticLock(
                new CannotAcquireLockException("deadlock detected; SQL [update section]"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "VERSION_CONFLICT");
        assertThat(problem.getDetail()).doesNotContain("SQL");
    }

    @Test
    void tooLargeMultipartIs413WithValidationFailed() throws Exception {
        ResponseEntity<Object> response = handler.handleException(new MaxUploadSizeExceededException(1),
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("code", "VALIDATION_FAILED");
    }
}
