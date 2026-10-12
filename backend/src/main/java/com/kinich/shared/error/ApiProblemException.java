package com.kinich.shared.error;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Error esperado de la API. El mensaje es el {@code detail} que ve el cliente: nunca debe contener datos internos. */
@Getter
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public ApiProblemException(HttpStatus status, ErrorCode code, String detail, Map<String, String> fieldErrors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, null, "El recurso solicitado no existe.", Map.of());
    }

    public static ApiProblemException versionConflict() {
        return new ApiProblemException(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
                "El contenido cambió desde que lo cargaste. Recarga e inténtalo de nuevo.", Map.of());
    }

    public static ApiProblemException maxItemsReached() {
        return new ApiProblemException(HttpStatus.CONFLICT, ErrorCode.MAX_ITEMS_REACHED,
                "Esta sección no admite más elementos.", Map.of());
    }

    public static ApiProblemException validation(Map<String, String> fieldErrors) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "La solicitud contiene datos inválidos.", fieldErrors);
    }

    public static ApiProblemException payloadTooLarge() {
        return new ApiProblemException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.VALIDATION_FAILED,
                "El archivo supera el tamaño máximo de 10 MB.", Map.of("file", "supera el máximo de 10 MB"));
    }
}
