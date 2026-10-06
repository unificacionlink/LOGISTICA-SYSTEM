package com.tdem.logistica.common;

import org.springframework.http.HttpStatus;

/** Excepcion de negocio controlada: el GlobalExceptionHandler la traduce a ApiResponse.fail(). */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String message, String code, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ApiException forbidden(String message) {
        return new ApiException(message, "NO_AUTORIZADO", HttpStatus.FORBIDDEN);
    }

    public static ApiException notFound(String message) {
        return new ApiException(message, "NO_ENCONTRADO", HttpStatus.NOT_FOUND);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(message, "VALIDACION", HttpStatus.BAD_REQUEST);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(message, "NO_AUTORIZADO", HttpStatus.UNAUTHORIZED);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
