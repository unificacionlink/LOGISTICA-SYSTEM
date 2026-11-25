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
import java.math.BigDecimal;
import java.util.List;

/** Excel de las ventas de TDEM a GOYDEL y METRIN (DEC-L086): una fila por linea vendida, en PEN. */
final class VentasExcel {

    static final List<String> COLUMNAS = List.of(
            "FECHA", "DOCUMENTO", "REQUERIMIENTO", "EMPRESA", "CECO", "NOMBRE CECO",
            "SKU", "PRODUCTO", "CANTIDAD", "PRECIO VENTA (PEN)", "TOTAL (PEN)");

    private VentasExcel() {
    }

    static byte[] generar(List<VentasDto.Linea> lineas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("VENTAS");
            CellStyle cabecera = cabecera(wb);
            CellStyle fecha = wb.createCellStyle();
            fecha.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle soles = wb.createCellStyle();
            soles.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

            Row titulos = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = titulos.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(cabecera);
            }
            int fila = 1;
            for (VentasDto.Linea l : lineas) {
                Row row = hoja.createRow(fila++);
                int c = 0;
                Cell f = row.createCell(c++);
                if (l.fecha() != null) {
                    f.setCellValue(l.fecha());
                    f.setCellStyle(fecha);
                }
                texto(row, c++, l.documento());
                texto(row, c++, l.requerimiento());
                texto(row, c++, l.empresa());
                texto(row, c++, l.ceco());
                texto(row, c++, l.cecoNombre());
                texto(row, c++, l.sku());
                texto(row, c++, l.producto());
                numero(row, c++, l.cantidad(), null);
                if (l.precio() == null) {
                    texto(row, c++, "Sin precio");
                } else {
                    numero(row, c++, l.precio(), soles);
                }
                numero(row, c, l.total(), soles);
            }
            hoja.createFreezePane(0, 1);
            if (fila > 1) {
                hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, COLUMNAS.size() - 1));
            }
            int[] anchos = {12, 18, 26, 12, 10, 28, 16, 48, 11, 18, 14};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de las ventas.", e);
        }
    }

    private static void texto(Row row, int c, String valor) {
        if (valor != null) {
            row.createCell(c).setCellValue(valor);
        }
    }

    private static void numero(Row row, int c, BigDecimal valor, CellStyle estilo) {
        if (valor != null) {
            Cell cell = row.createCell(c);
            cell.setCellValue(valor.doubleValue());
            if (estilo != null) {
                cell.setCellStyle(estilo);
            }
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
