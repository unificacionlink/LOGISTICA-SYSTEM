package com.tdem.logistica.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.servlet.multipart.max-file-size}")
    private String tamanoMaximoArchivo;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getMessage(), e.getCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "El archivo supera el tamaño maximo permitido (" + tamanoMaximoArchivo + ").", "VALIDACION"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegridad(DataIntegrityViolationException e) {
        log.warn("Conflicto de integridad de datos", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Ocurrio un conflicto al guardar, intenta de nuevo.", "CONFLICTO"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Datos invalidos.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(mensaje, "VALIDACION"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Ocurrio un error inesperado.", "ERROR"));
    }
}
