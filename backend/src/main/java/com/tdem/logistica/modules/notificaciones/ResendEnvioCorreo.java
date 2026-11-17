package com.tdem.logistica.modules.notificaciones;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio por la API HTTPS de Resend, el mismo proveedor y dominio verificado que
 * usa RR.HH. (DEC-L066). Por HTTPS y no SMTP: Render no deja enviar por SMTP en
 * todos sus planes. Sin RESEND_API_KEY queda apagado.
 */
@Component
public class ResendEnvioCorreo implements EnvioCorreo {

    private static final String URL = "https://api.resend.com/emails";

    record Respuesta(String id) {
    }

    private final String apiKey;
    private final String remitente;
    private final RestClient http;

    public ResendEnvioCorreo(@Value("${app.correo.resend-api-key:}") String apiKey,
                             @Value("${app.correo.from:no-reply@tdemperu.com}") String from,
                             @Value("${app.correo.from-name:Logistica TDEM}") String fromName) {
        this.apiKey = apiKey;
        this.remitente = fromName + " <" + from + ">";
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String enviar(Mensaje m) {
        try {
            Respuesta r = http.post()
                    .uri(URL)
                    .header("Authorization", "Bearer " + apiKey)
                    // Un reintento con el mismo id no se envia dos veces.
                    .header("Idempotency-Key", m.id().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", remitente, "to", List.of(m.para()), "subject", m.asunto(), "html", m.html()))
                    .retrieve()
                    .body(Respuesta.class);
            return r != null ? r.id() : null;
        } catch (RestClientResponseException e) {
            throw new CorreoNoEnviado("Resend respondio " + e.getStatusCode().value() + ": "
                    + recortar(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new CorreoNoEnviado("No se pudo conectar con Resend: " + recortar(e.getMessage()));
        }
    }

    private static String recortar(String s) {
        return s == null ? "" : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
