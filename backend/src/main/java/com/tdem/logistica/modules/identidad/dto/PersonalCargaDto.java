package com.tdem.logistica.modules.identidad.dto;

import java.util.List;

/** Resultado de validar o aplicar el Excel de personal (DEC-L028). */
public record PersonalCargaDto(
        String archivo,
        int nuevos,
        int actualizados,
        int desactivados,
        int sinCambios,
        int conError,
        boolean aplicada,
        List<Fila> filas) {

    /** accion: NUEVO, ACTUALIZADO, DESACTIVADO o SIN_CAMBIOS; null si la fila tiene error. */
    public record Fila(int fila, String empresa, String dni, String nombre, String correo, String accion, String error) {
    }
}
