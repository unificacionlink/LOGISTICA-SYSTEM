package com.tdem.logistica.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Direccion publica de la plataforma, para los enlaces que salen de ella
 * (enlace del proveedor DEC-L052, correos DEC-L066). Sin APP_PUBLIC_URL se usa
 * el primer origen permitido (en produccion, el dominio).
 */
@Component
public class UrlPublica {

    private final String base;

    public UrlPublica(@Value("${app.public-url:}") String urlPublica,
                      @Value("${app.cors.allowed-origins}") String origenes) {
        String b = urlPublica != null && !urlPublica.isBlank() ? urlPublica : origenes.split(",")[0];
        this.base = b.trim().replaceAll("/+$", "");
    }

    /** {@code ruta} empieza con "/". */
    public String de(String ruta) {
        return base + ruta;
    }
}
