package com.tdem.logistica.common;

/**
 * Respuesta controlada {ok, result, message} — mismo contrato usado en el
 * stack anterior (Next.js) y en el proyecto hermano RRHH-SYSTEM.
 */
public record ApiResponse<T>(boolean ok, T result, String message, String code, String error) {

    public static <T> ApiResponse<T> ok(T result, String message) {
        return new ApiResponse<>(true, result, message, null, null);
    }

    public static <T> ApiResponse<T> ok(T result) {
        return ok(result, "Operacion realizada correctamente.");
    }

    public static <T> ApiResponse<T> fail(String message, String code) {
        return new ApiResponse<>(false, null, message, code, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(message, "ERROR");
    }
}
