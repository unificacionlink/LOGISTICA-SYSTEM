package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.modules.cajachica.dto.CompraCajaChicaDto;
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

/** Excel del consolidado de caja chica (DEC-L092): una fila por linea comprada, en soles. */
final class CajaChicaExcel {

    static final List<String> COLUMNAS = List.of(
            "CODIGO", "FECHA", "EMPRESA", "CECO", "NOMBRE CECO", "SOLICITANTE", "RUC", "PROVEEDOR",
            "COMPROBANTE", "SERIE", "NUMERO", "SKU", "DESCRIPCION", "CANTIDAD", "PRECIO UNIT. (PEN)",
            "TOTAL LINEA (PEN)", "TOTAL COMPROBANTE (PEN)", "ESTADO", "FACTURA");

    private CajaChicaExcel() {
    }

    static byte[] generar(List<CompraCajaChicaDto> compras) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("CAJA CHICA");
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
            for (CompraCajaChicaDto co : compras) {
                List<CompraCajaChicaDto.Linea> lineas = co.lineas().isEmpty()
                        ? List.of(new CompraCajaChicaDto.Linea(null, "", null, null, null)) : co.lineas();
                for (CompraCajaChicaDto.Linea l : lineas) {
                    Row row = hoja.createRow(fila++);
                    int c = 0;
                    texto(row, c++, co.codigo());
                    Cell f = row.createCell(c++);
                    f.setCellValue(co.fecha());
                    f.setCellStyle(fecha);
                    texto(row, c++, co.empresaCode());
                    texto(row, c++, co.cecoCode());
                    texto(row, c++, co.cecoNombre());
                    texto(row, c++, co.solicitante());
                    texto(row, c++, co.proveedorRuc());
                    texto(row, c++, co.proveedorNombre());
                    texto(row, c++, co.tipoComprobante());
                    texto(row, c++, co.serie());
                    texto(row, c++, co.numero());
                    texto(row, c++, l.sku());
                    texto(row, c++, l.descripcion());
                    numero(row, c++, l.cantidad(), null);
                    numero(row, c++, l.precioUnitario(), soles);
                    numero(row, c++, l.total(), soles);
                    numero(row, c++, co.total(), soles);
                    texto(row, c++, "ANULADA".equals(co.estado()) ? "Anulada: " + co.motivoAnulacion() : "Registrada");
                    texto(row, c, co.facturaArchivoUrl() != null ? co.facturaArchivoUrl() : co.facturaUrl());
                }
            }
            hoja.createFreezePane(0, 1);
            if (fila > 1) {
                hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, COLUMNAS.size() - 1));
            }
            int[] anchos = {24, 12, 11, 10, 26, 26, 13, 28, 12, 8, 12, 14, 40, 10, 14, 14, 16, 22, 40};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de caja chica.", e);
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
