package com.tdem.logistica.modules.maestro;

import com.tdem.logistica.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DEC-L075: codigo de la variante segun el tipo de atributo. */
class NomenclaturaVarianteTest {

    @Test
    void tallaTalCualComoElCatalogo() {
        assertEquals("L", NomenclaturaVariante.codigo("TALLA", "l"));
        assertEquals("42", NomenclaturaVariante.codigo("TALLA", "42"));
        assertEquals("XXL", NomenclaturaVariante.codigo("TALLA", "Talla XXL"));
    }

    @Test
    void claseConCSinDuplicarla() {
        assertEquals("C0", NomenclaturaVariante.codigo("CLASE", "0"));
        assertEquals("C0", NomenclaturaVariante.codigo("CLASE", "C0"));
        assertEquals("C2", NomenclaturaVariante.codigo("CLASE", "Clase 2"));
    }

    @Test
    void colorModeloYOtroConSuPrefijo() {
        assertEquals("CLAZUL", NomenclaturaVariante.codigo("COLOR", "azul"));
        assertEquals("CLAZULMARINO", NomenclaturaVariante.codigo("COLOR", "Color azul marino"));
        assertEquals("CLLIMON", NomenclaturaVariante.codigo("COLOR", "Limón"));
        assertEquals("MXR200", NomenclaturaVariante.codigo("MODELO", "XR-200"));
        assertEquals("OT12", NomenclaturaVariante.codigo("OTRO", "1/2\""));
    }

    @Test
    void rechazaVaciosLargosYTiposDesconocidos() {
        assertThrows(ApiException.class, () -> NomenclaturaVariante.codigo("COLOR", "--"));
        assertThrows(ApiException.class, () -> NomenclaturaVariante.codigo("COLOR", "AZUL MARINO OSCURO METALICO"));
        assertThrows(ApiException.class, () -> NomenclaturaVariante.codigo("SABOR", "FRESA"));
    }
}
