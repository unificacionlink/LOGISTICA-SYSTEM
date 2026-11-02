package com.tdem.logistica.modules.identidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tdem.logistica.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Cliente de solo lectura de RRHH-SYSTEM (DEC-L002/DEC-L024), con header
 * X-Service-Token: {@code GET /api/integraciones/personal-activo}
 * (dni/nombre/correo/empresa/ceco/cargo del personal ACTIVO) y
 * {@code GET /api/integraciones/cecos} (todos los CECO activos, DEC-L031).
 * RRHH responde con su contrato {ok, result, message}.
 */
@Component
public class RrhhClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CecoRrhh(String numero, String nombre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonalRrhh(
            String dni,
            @JsonProperty("nombre_completo") String nombreCompleto,
            String correo,
            String empresa,
            CecoRrhh ceco,
            String cargo) {
    }

    /** CECO del catalogo de RRHH (tenga o no personal). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CecoCatalogoRrhh(String numero, String nombre, String empresa) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(boolean ok, List<PersonalRrhh> result, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaCecos(boolean ok, List<CecoCatalogoRrhh> result, String message) {
    }

    private final String apiUrl;
    private final String token;
    private final RestClient http;

    public RrhhClient(@Value("${app.rrhh.api-url:}") String apiUrl,
                      @Value("${app.rrhh.service-token:}") String token) {
        this.apiUrl = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
        this.token = token;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean configurado() {
        return !apiUrl.isBlank() && token != null && !token.isBlank();
    }

    /** Todo el personal activo de todas las empresas (una sola llamada: RRHH limita a 30/min). */
    public List<PersonalRrhh> personalActivo() {
        if (!configurado()) {
            throw ApiException.badRequest("La sincronizacion con RR.HH. no esta configurada (RRHH_API_URL / RRHH_SYNC_SERVICE_TOKEN).");
        }
        Respuesta r;
        try {
            r = http.get()
                    .uri(apiUrl + "/api/integraciones/personal-activo")
                    .header("X-Service-Token", token)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException e) {
            throw ApiException.badRequest("No se pudo consultar RR.HH.: " + e.getMessage());
        }
        if (r == null || !r.ok() || r.result() == null) {
            throw ApiException.badRequest("RR.HH. respondio con error: " + (r != null ? r.message() : "sin respuesta"));
        }
        return r.result();
    }

    /**
     * Todos los CECO activos de RRHH. Devuelve null si RRHH aun no publica el
     * endpoint (404): el sync sigue como antes, solo con los CECO del personal.
     */
    public List<CecoCatalogoRrhh> cecosActivos() {
        RespuestaCecos r;
        try {
            r = http.get()
                    .uri(apiUrl + "/api/integraciones/cecos")
                    .header("X-Service-Token", token)
                    .retrieve()
                    .body(RespuestaCecos.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw ApiException.badRequest("No se pudo consultar los CECO de RR.HH.: " + e.getMessage());
        }
        if (r == null || !r.ok() || r.result() == null) {
            throw ApiException.badRequest("RR.HH. respondio con error: " + (r != null ? r.message() : "sin respuesta"));
        }
        return r.result();
    }
}
