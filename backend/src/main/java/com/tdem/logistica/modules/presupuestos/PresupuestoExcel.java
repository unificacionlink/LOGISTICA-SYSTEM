package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.modules.presupuestos.dto.LineaPresupuestoDto;
import com.tdem.logistica.modules.presupuestos.dto.PresupuestoDetalleDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel de la programacion de un presupuesto: bloque de metadatos y una fila por
 * SKU con sus 6 meses, total, precio e importe. Sin precio se escribe SIN PRECIO
 * (no equivale a cero) y el total general queda INCOMPLETO.
 */
final class PresupuestoExcel {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

    private PresupuestoExcel() {
    }

    static byte[] generar(PresupuestoDetalleDto d) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle negrita = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            negrita.setFont(font);
            CellStyle numero = wb.createCellStyle();
            numero.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle entero = wb.createCellStyle();
            entero.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            Sheet hoja = wb.createSheet("Presupuesto");
            String[][] meta = {
                    {"Presupuesto", d.codigo() + (d.versionVigente() != null ? " v" + d.versionVigente() : "")},
                    {"Empresa", d.empresa()},
                    {"CECO", d.cecoCode() + " " + d.cecoNombre()},
                    {"Periodo", d.anio() + "-S" + d.semestre()},
                    {"Estado", d.estado()},
                    {"Solicitante", texto(d.solicitante())},
                    {"Aprobado por", texto(d.aprobadoPor()) + (d.aprobadoEn() != null ? " · " + fecha(d.aprobadoEn()) : "")},
                    {"Generado", fecha(Instant.now())}};
            for (int i = 0; i < meta.length; i++) {
                Row row = hoja.createRow(i);
                Cell clave = row.createCell(0);
                clave.setCellValue(meta[i][0]);
                clave.setCellStyle(negrita);
                row.createCell(1).setCellValue(meta[i][1]);
            }

            List<String> columnas = new ArrayList<>(List.of("SKU", "Producto", "Familia", "Unidad"));
            for (int m = 1; m <= 6; m++) {
                columnas.add(MesesSemestre.nombre(d.semestre(), m));
            }
            columnas.addAll(List.of("Total", "Precio de venta", "Importe"));
            int filaCabecera = meta.length + 1;
            Row cab = hoja.createRow(filaCabecera);
            for (int c = 0; c < columnas.size(); c++) {
                Cell cell = cab.createCell(c);
                cell.setCellValue(columnas.get(c));
                cell.setCellStyle(negrita);
            }

            for (LineaPresupuestoDto l : d.lineas()) {
                Row row = hoja.createRow(hoja.getLastRowNum() + 1);
                int c = 0;
                row.createCell(c++).setCellValue(l.sku());
                row.createCell(c++).setCellValue(l.nombre());
                row.createCell(c++).setCellValue(texto(l.familia()));
                row.createCell(c++).setCellValue(texto(l.unidad()));
                for (BigDecimal q : l.meses()) {
                    cantidad(row, c++, q, entero);
                }
                cantidad(row, c++, l.cantidad(), entero);
                monto(row, c++, l.precio(), numero);
                monto(row, c++, l.importe(), numero);
            }
            int ultimaLinea = hoja.getLastRowNum();

            Row total = hoja.createRow(ultimaLinea + 1);
            Cell rotulo = total.createCell(columnas.size() - 2);
            rotulo.setCellValue("Total");
            rotulo.setCellStyle(negrita);
            Cell suma = total.createCell(columnas.size() - 1);
            if (d.lineasSinPrecio() > 0 || d.total() == null) {
                suma.setCellValue("INCOMPLETO");
            } else {
                suma.setCellValue(d.total().doubleValue());
                suma.setCellStyle(numero);
            }

            hoja.setAutoFilter(new CellRangeAddress(filaCabecera, Math.max(filaCabecera, ultimaLinea), 0,
                    columnas.size() - 1));
            hoja.createFreezePane(0, filaCabecera + 1);
            for (int c = 0; c < columnas.size(); c++) {
                hoja.setColumnWidth(c, (c == 1 ? 48 : c < 4 ? 18 : 14) * 256);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static String fecha(Instant i) {
        return FECHA.format(i);
    }

    private static void cantidad(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor == null ? 0 : valor.doubleValue());
        cell.setCellStyle(estilo);
    }

    private static void monto(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(col);
        if (valor == null) {
            cell.setCellValue("SIN PRECIO");
        } else {
            cell.setCellValue(valor.doubleValue());
            cell.setCellStyle(estilo);
        }
    }
}
