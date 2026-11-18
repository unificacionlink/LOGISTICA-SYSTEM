package com.tdem.logistica.modules.requerimientos;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** DEC-L068: dias habiles sin fines de semana ni feriados nacionales de Peru. */
class SlaAtencionTest {

    @Test
    void plazosPorPrioridad() {
        assertEquals(2, SlaAtencion.plazo("URGENTE").diasHabiles());
        assertEquals(10, SlaAtencion.plazo("APROVISIONAMIENTO").diasHabiles());
        assertEquals(20, SlaAtencion.plazo("IMPLEMENTACION").diasHabiles());
        // Una prioridad desconocida (o de un cliente anterior) cae en la de por defecto.
        assertEquals("APROVISIONAMIENTO", SlaAtencion.normalizar("CRITICA"));
        assertEquals("APROVISIONAMIENTO", SlaAtencion.normalizar(null));
        assertEquals("Implementación", SlaAtencion.etiqueta("IMPLEMENTACION"));
    }

    @Test
    void saltaFinesDeSemana() {
        // Jueves 2026-11-19 + 2 habiles = lunes 23.
        assertEquals(LocalDate.of(2026, 11, 23), SlaAtencion.fechaTentativa("URGENTE", LocalDate.of(2026, 11, 19)));
        // Enviado un sabado: cuenta desde el lunes.
        assertEquals(LocalDate.of(2026, 11, 24), SlaAtencion.fechaTentativa("URGENTE", LocalDate.of(2026, 11, 21)));
        // Miercoles 2026-11-18 + 10 habiles = miercoles 2 de diciembre.
        assertEquals(LocalDate.of(2026, 12, 2), SlaAtencion.fechaTentativa("APROVISIONAMIENTO", LocalDate.of(2026, 11, 18)));
    }

    @Test
    void saltaFeriados() {
        // 2026-12-07 (lunes) + 2: el 8 y el 9 son feriados -> jueves 10 y viernes 11.
        assertEquals(LocalDate.of(2026, 12, 11), SlaAtencion.fechaTentativa("URGENTE", LocalDate.of(2026, 12, 7)));
        // Semana Santa 2027: Pascua el 28 de marzo; jueves 25 y viernes 26 no son habiles.
        assertEquals(LocalDate.of(2027, 3, 28), SlaAtencion.domingoDePascua(2027));
        assertEquals(LocalDate.of(2027, 3, 30), SlaAtencion.fechaTentativa("URGENTE", LocalDate.of(2027, 3, 24)));
        assertFalse(SlaAtencion.esHabil(LocalDate.of(2027, 7, 28)));
        assertFalse(SlaAtencion.esHabil(LocalDate.of(2027, 7, 29)));
    }

    @Test
    void habilesTranscurridos() {
        assertEquals(0, SlaAtencion.habilesEntre(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18)));
        // Viernes 20 -> lunes 23: un habil.
        assertEquals(1, SlaAtencion.habilesEntre(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)));
        assertEquals(10, SlaAtencion.habilesEntre(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 12, 2)));
    }
}
