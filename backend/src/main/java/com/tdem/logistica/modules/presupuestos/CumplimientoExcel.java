package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.modules.presupuestos.dto.CumplimientoDto;
import com.tdem.logistica.modules.presupuestos.dto.CumplimientoFilaDto;
import com.tdem.logistica.modules.presupuestos.dto.ResumenFamiliaDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * RES-02: Excel de cumplimiento de la V38.7 (bcComplianceSheets): hojas
 * "Resumen por familia" y "Detalle por SKU", cada una con su bloque de
 * metadatos, cabecera con autofiltro y montos en PEN neto. Sin precio se
 * escribe SIN PRECIO y un total con filas sin precio, INCOMPLETO.
 */
final class CumplimientoExcel {

    private static final List<String> MONTOS = List.of("Presupuesto (programado x precio)",
            "Real (consumido x precio)", "Pendiente (pendiente x precio)", "Saldo (saldo x precio)",
            "Disponible (disponible x precio)");

    private CumplimientoExcel() {
    }

    static byte[] generar(CumplimientoDto d) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle cabecera = wb.createCellStyle();
            Font negrita = wb.createFont();
            negrita.setBold(true);
            cabecera.setFont(negrita);
            CellStyle numero = wb.createCellStyle();
            numero.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            List<String> resumen = new ArrayList<>(List.of("Familia"));
            MONTOS.forEach(m -> resumen.add(m + " venta"));
            if (d.conCompra()) {
                MONTOS.forEach(m -> resumen.add(m + " compra"));
            }
            Sheet hojaResumen = hoja(wb, "Resumen por familia", d, resumen, cabecera);
            for (ResumenFamiliaDto r : d.resumen()) {
                Row row = hojaResumen.createRow(hojaResumen.getLastRowNum() + 1);
                row.createCell(0).setCellValue(r.familia());
                int c = 1;
                for (BigDecimal v : r.venta()) {
                    monto(row, c++, v, "INCOMPLETO", numero);
                }
                if (d.conCompra()) {
                    for (BigDecimal v : r.compra()) {
                        monto(row, c++, v, "INCOMPLETO", numero);
                    }
                }
            }
            cerrar(hojaResumen, resumen.size());

            List<String> detalle = new ArrayList<>(List.of("Familia", "Subfamilia", "SKU", "Producto", "Unidad",
                    "Programado", "Real consumido", "Pendiente", "Saldo", "Disponible", "Precio de venta"));
            MONTOS.forEach(m -> detalle.add(m + " venta"));
            if (d.conCompra()) {
                detalle.add("Precio de compra");
                MONTOS.forEach(m -> detalle.add(m + " compra"));
            }
            Sheet hojaDetalle = hoja(wb, "Detalle por SKU", d, detalle, cabecera);
            for (CumplimientoFilaDto f : d.filas()) {
                Row row = hojaDetalle.createRow(hojaDetalle.getLastRowNum() + 1);
                int c = 0;
                row.createCell(c++).setCellValue(f.familia());
                row.createCell(c++).setCellValue(f.subfamilia());
                row.createCell(c++).setCellValue(f.sku());
                row.createCell(c++).setCellValue(f.nombre() + (f.sinPresupuesto() ? " (SIN PRESUPUESTO)" : ""));
                row.createCell(c++).setCellValue(f.unidad());
                for (BigDecimal q : List.of(f.programado(), f.real(), f.pendiente(), f.saldo(), f.disponible())) {
                    row.createCell(c++).setCellValue(q.doubleValue());
                }
                monto(row, c++, f.precioVenta(), "SIN PRECIO", numero);
                for (BigDecimal v : f.venta()) {
                    monto(row, c++, v, "SIN PRECIO", numero);
                }
                if (d.conCompra()) {
                    monto(row, c++, f.precioCompra(), "SIN PRECIO", numero);
                    for (BigDecimal v : f.compra()) {
                        monto(row, c++, v, "SIN PRECIO", numero);
                    }
                }
            }
            cerrar(hojaDetalle, detalle.size());

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Hoja con metadatos, una fila en blanco y la cabecera. */
    private static Sheet hoja(Workbook wb, String nombre, CumplimientoDto d, List<String> columnas, CellStyle estilo) {
        Sheet hoja = wb.createSheet(nombre);
        String corte = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima")).format(d.corte());
        String[][] meta = {
                {"Presupuesto", d.codigo() + " v" + d.version()},
                {"Empresa", d.empresa()},
                {"CECO", d.cecoCode() + " " + d.cecoNombre()},
                {"Periodo", d.periodo()},
                {"Fecha de corte", corte},
                {"Base", "PEN neto; cantidades x precios congelados al aprobar"},
                {"Consumo", "Cantidad recepcionada en destino; lo no recibido no es consumo"},
                {"Saldo", "Programado - real; disponible = programado - real - pendiente"},
                {"Sin dato", "SIN PRECIO = falta precio congelado; no equivale a cero"}};
        for (int i = 0; i < meta.length; i++) {
            Row row = hoja.createRow(i);
            Cell clave = row.createCell(0);
            clave.setCellValue(meta[i][0]);
            clave.setCellStyle(estilo);
            row.createCell(1).setCellValue(meta[i][1]);
        }
        Row cab = hoja.createRow(meta.length + 1);
        for (int c = 0; c < columnas.size(); c++) {
            Cell cell = cab.createCell(c);
            cell.setCellValue(columnas.get(c));
            cell.setCellStyle(estilo);
        }
        return hoja;
    }

    private static void cerrar(Sheet hoja, int columnas) {
        int cabecera = 10; // 9 filas de metadatos + 1 en blanco
        hoja.setAutoFilter(new CellRangeAddress(cabecera, Math.max(cabecera, hoja.getLastRowNum()), 0, columnas - 1));
        for (int c = 0; c < columnas; c++) {
            hoja.setColumnWidth(c, (c < 4 ? 28 : 22) * 256);
        }
    }

    private static void monto(Row row, int col, BigDecimal valor, String sinDato, CellStyle numero) {
        Cell cell = row.createCell(col);
        if (valor == null) {
            cell.setCellValue(sinDato);
        } else {
            cell.setCellValue(valor.doubleValue());
            cell.setCellStyle(numero);
        }
    }
}
