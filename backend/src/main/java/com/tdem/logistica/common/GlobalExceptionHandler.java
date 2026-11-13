package com.tdem.logistica.common;

import com.tdem.logistica.modules.registro.RegistroPlataforma;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.servlet.multipart.max-file-size}")
    private String tamanoMaximoArchivo;

    private final RegistroPlataforma registro;

    public GlobalExceptionHandler(RegistroPlataforma registro) {
        this.registro = registro;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException e, HttpServletRequest request) {
        // DEC-L060: por que se rechazo la operacion, para el registro de actividad.
        if (request.getAttribute(RegistroPlataforma.ATR_DETALLE) == null) {
            request.setAttribute(RegistroPlataforma.ATR_DETALLE, e.getMessage());
        }
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getMessage(), e.getCode()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "El archivo supera el tamaño maximo permitido (" + tamanoMaximoArchivo + ").", "VALIDACION"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegridad(DataIntegrityViolationException e,
                                                               HttpServletRequest request) {
        log.warn("Conflicto de integridad de datos", e);
        registro.errorServidor(request, usuario(), HttpStatus.CONFLICT.value(), e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Ocurrio un conflicto al guardar, intenta de nuevo.", "CONFLICTO"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e,
                                                                HttpServletRequest request) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Datos invalidos.");
        request.setAttribute(RegistroPlataforma.ATR_DETALLE, mensaje);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(mensaje, "VALIDACION"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Error inesperado", e);
        registro.errorServidor(request, usuario(), HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Ocurrio un error inesperado.", "ERROR"));
    }

    private static UUID usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p ? p.userId() : null;
    }
}
