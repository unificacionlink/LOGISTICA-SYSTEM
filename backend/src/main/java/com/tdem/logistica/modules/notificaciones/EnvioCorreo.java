package com.tdem.logistica.modules.notificaciones;

import java.util.UUID;

/** Proveedor de correo (DEC-L066). En produccion, Resend; en los tests, uno falso. */
public interface EnvioCorreo {

    /** {@code id} = correo_id: el proveedor lo usa para no duplicar un reintento. */
    record Mensaje(UUID id, String para, String asunto, String html) {
    }

    boolean configurado();

    /** Envia el correo y devuelve el id que le dio el proveedor; lanza {@link CorreoNoEnviado} si falla. */
    String enviar(Mensaje mensaje);

    class CorreoNoEnviado extends RuntimeException {
        public CorreoNoEnviado(String mensaje) {
            super(mensaje);
        }
    }
}
