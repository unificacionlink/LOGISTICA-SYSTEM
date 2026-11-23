package com.tdem.logistica.modules.dashboard;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Excel de la pestana Demanda (DEC-L083): las columnas del detalle del
 * dashboard operativo de la V38.7 (dashboard-operativo.js) mas el CECO.
 */
final class DemandaExcel {

    static final List<String> COLUMNAS = List.of(
            "REQUERIMIENTO", "EMPRESA", "CECO", "FECHA", "ESTADO",
            "FAMILIA", "SUBFAMILIA", "SKU", "PRODUCTO", "CANTIDAD");

    /** Mismas etiquetas que EstadoBadge del frontend. */
    private static final Map<String, String> ESTADOS = Map.of(
            "PENDIENTE_APROBACION", "Pendiente de aprobación",
            "OBSERVADO", "Observado",
            "VERIFICACION_STOCK", "Verificación de stock",
            "EN_ATENCION", "En atención",
            "ATENCION_PARCIAL", "Atención parcial",
            "ATENCION_COMPLETA", "Atención completa",
            "RECHAZADO", "Rechazado");

    private DemandaExcel() {
    }

    static byte[] generar(List<DemandaDto.Linea> lineas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("DEMANDA");
            CellStyle cabecera = cabecera(wb);
            CellStyle fecha = wb.createCellStyle();
            fecha.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));

            Row titulos = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = titulos.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(cabecera);
            }
            int fila = 1;
            for (DemandaDto.Linea l : lineas) {
                Row row = hoja.createRow(fila++);
                int c = 0;
                texto(row, c++, l.requerimiento());
                texto(row, c++, l.empresa());
                texto(row, c++, l.ceco());
                Cell f = row.createCell(c++);
                if (l.fecha() != null) {
                    f.setCellValue(l.fecha());
                    f.setCellStyle(fecha);
                }
                texto(row, c++, ESTADOS.getOrDefault(l.estado(), l.estado()));
                texto(row, c++, l.familia());
                texto(row, c++, l.subfamilia());
                texto(row, c++, l.sku());
                texto(row, c++, l.producto());
                if (l.cantidad() != null) {
                    row.createCell(c).setCellValue(l.cantidad().doubleValue());
                }
            }
            hoja.createFreezePane(0, 1);
            if (fila > 1) {
                hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, COLUMNAS.size() - 1));
            }
            int[] anchos = {18, 12, 12, 12, 22, 24, 28, 16, 48, 11};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de la demanda.", e);
        }
    }

    private static void texto(Row row, int c, String valor) {
        if (valor != null) {
            row.createCell(c).setCellValue(valor);
        }
    }

    private static CellStyle cabecera(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(font);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
