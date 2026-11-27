package com.tdem.logistica.modules.reportes;

import com.tdem.logistica.modules.reportes.dto.ReporteObservacionDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Excel de los reportes de observaciones (DEC-L104), listo para pegar en la hoja de consolidacion. */
final class ReporteObservacionExcel {

    static final List<String> COLUMNAS = List.of(
            "N°", "FECHA", "USUARIO", "ROL", "MÓDULO / PANTALLA", "ZONA SEÑALADA",
            "DESCRIPCIÓN DE LA OBSERVACIÓN", "ESTADO", "RESPUESTA", "RUTA");

    static final Map<String, String> ESTADOS = Map.of(
            "PENDIENTE", "Pendiente",
            "EN_REVISION", "En revisión",
            "RESUELTO", "Resuelto",
            "DESCARTADO", "Descartado");

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

    private ReporteObservacionExcel() {
    }

    static byte[] generar(List<ReporteObservacionDto.Fila> filas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("OBSERVACIONES");
            CellStyle cabecera = cabecera(wb);
            CellStyle ajustado = wb.createCellStyle();
            ajustado.setWrapText(true);
            ajustado.setVerticalAlignment(VerticalAlignment.TOP);

            Row titulos = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = titulos.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(cabecera);
            }
            int fila = 1;
            for (ReporteObservacionDto.Fila r : filas) {
                Row row = hoja.createRow(fila++);
                int c = 0;
                texto(row, c++, codigo(r.numero()), ajustado);
                texto(row, c++, FECHA.format(r.en()), ajustado);
                texto(row, c++, r.usuario(), ajustado);
                texto(row, c++, r.roles(), ajustado);
                texto(row, c++, pantalla(r.modulo(), r.pantalla()), ajustado);
                texto(row, c++, r.zona() == null ? "Toda la pantalla" : r.zona(), ajustado);
                texto(row, c++, r.descripcion(), ajustado);
                texto(row, c++, ESTADOS.getOrDefault(r.estado(), r.estado()), ajustado);
                texto(row, c++, r.respuesta(), ajustado);
                texto(row, c, r.ruta(), ajustado);
            }
            hoja.createFreezePane(0, 1);
            if (fila > 1) {
                hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, COLUMNAS.size() - 1));
            }
            int[] anchos = {10, 16, 26, 18, 30, 40, 60, 13, 40, 30};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de los reportes.", e);
        }
    }

    static String codigo(int numero) {
        return String.format("OBS-%04d", numero);
    }

    /** "Compras · Orden de compra OC-0012": el modulo del menu y el titulo de la pantalla, sin repetir. */
    static String pantalla(String modulo, String pantalla) {
        if (modulo == null) {
            return pantalla;
        }
        if (pantalla == null || pantalla.equalsIgnoreCase(modulo)) {
            return modulo;
        }
        return modulo + " · " + pantalla;
    }

    private static void texto(Row row, int c, String valor, CellStyle estilo) {
        Cell cell = row.createCell(c);
        cell.setCellStyle(estilo);
        if (valor != null) {
            cell.setCellValue(valor);
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
