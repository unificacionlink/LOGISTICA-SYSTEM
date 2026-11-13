package com.tdem.logistica.modules.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Fin de un recorrido guiado: COMPLETADO o OMITIDO (DEC-L055). */
public record TutorialRequest(
        @NotBlank @Pattern(regexp = "COMPLETADO|OMITIDO", message = "Estado no valido.") String estado) {
}
