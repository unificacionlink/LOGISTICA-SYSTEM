package com.tdem.logistica.common;

import com.tdem.logistica.modules.almacen.dto.RegistrarEnvioRequest;
import com.tdem.logistica.modules.requerimientos.dto.ItemRequerimientoRequest;
import com.tdem.logistica.modules.stock.dto.AjusteStockRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las cantidades son enteras (unidades contables) y los montos tienen tope de digitos. */
class ValidacionCantidadesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private int errores(Object o) {
        return validator.validate(o).size();
    }

    private ItemRequerimientoRequest item(String qty) {
        return new ItemRequerimientoRequest(UUID.randomUUID(), new BigDecimal(qty), null, null);
    }

    @Test
    void cantidadDelRequerimiento() {
        assertEquals(0, errores(item("3")));
        assertEquals(0, errores(item("999999")));
        assertTrue(errores(item("2.5")) > 0, "sin decimales");
        assertTrue(errores(item("0")) > 0, "al menos 1");
        assertTrue(errores(item("1234567")) > 0, "maximo 6 digitos");
    }

    @Test
    void ajusteDeStockYEnvio() {
        assertEquals(0, errores(new AjusteStockRequest(UUID.randomUUID(), new BigDecimal("-5"), "Merma")));
        assertTrue(errores(new AjusteStockRequest(UUID.randomUUID(), new BigDecimal("1.5"), "Merma")) > 0);
        RegistrarEnvioRequest bien = new RegistrarEnvioRequest("PROPIO", null, null, 1, new BigDecimal("12.345"), "PEN",
                new BigDecimal("45.50"), new BigDecimal("8.19"), LocalDate.now(), LocalDate.now());
        assertEquals(0, errores(bien));
        RegistrarEnvioRequest mal = new RegistrarEnvioRequest("PROPIO", null, null, 1, new BigDecimal("1.23456"), "PEN",
                new BigDecimal("45.555"), BigDecimal.ZERO, LocalDate.now(), LocalDate.now());
        assertEquals(2, errores(mal), "peso con 5 decimales y costo con 3");
    }
}
