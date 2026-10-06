package com.tdem.logistica.modules.identidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tdem.logistica.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Cliente de solo lectura de RRHH-SYSTEM (DEC-L002/DEC-L024):
 * {@code GET /api/integraciones/personal-activo} con header X-Service-Token.
 * RRHH responde con su contrato {ok, result, message} y solo expone
 * dni/nombre/correo/empresa/ceco/cargo del personal ACTIVO.
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(boolean ok, List<PersonalRrhh> result, String message) {
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
}
