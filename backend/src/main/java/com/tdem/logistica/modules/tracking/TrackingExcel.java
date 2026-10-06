package com.tdem.logistica.modules.tracking;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.modules.tracking.dto.EntregaDto;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-081: Excel del Tracking con las columnas del prototipo V38
 * (20_Tracking_REQ_NI_V37): la cabecera del REQ se repite en cada fila y hay
 * una fila por Nota de Inventario / envio.
 */
final class TrackingExcel {

    static final List<String> COLUMNAS = List.of(
            "ID DE SEGUIMIENTO",
            "FECHA DE ENTREGA PROGRAMADA",
            "NIVEL DE URGENCIA",
            "CENTRO DE COSTOS QUE RECEPCIONA",
            "DIRECCION DE ENVIO",
            "VALOR DEL PEDIDO",
            "SOLICITANTE",
            "POS.",
            "NOTA DE INVENTARIO",
            "FECHA DE ENVIO",
            "LINK GUIA DE REMISION",
            "LINK TICKET DE ENVIO",
            "VALOR DE NOTA DE INVENTARIO",
            "COSTO DE ENVIO (SIN IGV)",
            "ITEMS DESPACHADOS",
            "LINK CONFORMIDAD GUIA Y NOTA DE INVENTARIO",
            "ESTADO DE ENTREGA");

    private TrackingExcel() {
    }

    static byte[] generar(TrackingDto t) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("TRACKING");
            Estilos estilos = new Estilos(wb);

            Row cabecera = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = cabecera.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(estilos.cabecera);
            }

            RequerimientoDto r = t.requerimiento();
            String ceco = "(" + t.cecoCodigo() + ") " + r.cecoNombre();
            int fila = 1;
            for (EntregaDto e : t.entregas()) {
                Row row = hoja.createRow(fila++);
                int c = 0;
                texto(row, c++, r.code());
                fecha(row, c++, t.entregaProgramada(), estilos);
                texto(row, c++, r.priority());
                texto(row, c++, ceco);
                texto(row, c++, t.direccionEnvio());
                monto(row, c++, t.valorPedido(), estilos);
                texto(row, c++, r.requesterNombre());
                row.createCell(c++).setCellValue(e.posicion());
                texto(row, c++, e.notaCodigo());
                fecha(row, c++, e.fechaEnvio(), estilos);
                link(row, c++, e.urlGuia(), wb, estilos);
                link(row, c++, e.urlTicket(), wb, estilos);
                monto(row, c++, e.valorNota(), estilos);
                monto(row, c++, e.costoEnvioNeto(), estilos);
                texto(row, c++, e.itemsDespachados());
                link(row, c++, e.urlConformidad(), wb, estilos);
                texto(row, c, e.estadoEntrega());
            }

            hoja.createFreezePane(0, 1);
            if (fila > 1) {
                hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, fila - 1, 0, COLUMNAS.size() - 1));
            }
            for (int c = 0; c < COLUMNAS.size(); c++) {
                // autoSizeColumn necesita fuentes del sistema; ancho fijo por largo de la cabecera.
                hoja.setColumnWidth(c, Math.min(60, Math.max(14, COLUMNAS.get(c).length() + 4)) * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel del Tracking.", e);
        }
    }

    private record Estilos(CellStyle cabecera, CellStyle fecha, CellStyle monto, CellStyle link) {
        Estilos(Workbook wb) {
            this(crearCabecera(wb), crearFormato(wb, "dd/mm/yyyy"), crearFormato(wb, "#,##0.00"), crearLink(wb));
        }

        private static CellStyle crearCabecera(Workbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setWrapText(true);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }

        private static CellStyle crearFormato(Workbook wb, String formato) {
            CellStyle s = wb.createCellStyle();
            s.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat(formato));
            return s;
        }

        private static CellStyle crearLink(Workbook wb) {
            Font font = wb.createFont();
            font.setUnderline(Font.U_SINGLE);
            font.setColor(IndexedColors.BLUE.getIndex());
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            return s;
        }
    }

    private static void texto(Row row, int c, String valor) {
        row.createCell(c).setCellValue(valor != null ? valor : "");
    }

    private static void fecha(Row row, int c, LocalDate valor, Estilos estilos) {
        Cell cell = row.createCell(c);
        if (valor != null) {
            cell.setCellValue(valor);
            cell.setCellStyle(estilos.fecha);
        }
    }

    private static void monto(Row row, int c, BigDecimal valor, Estilos estilos) {
        Cell cell = row.createCell(c);
        cell.setCellValue(valor != null ? valor.doubleValue() : 0);
        cell.setCellStyle(estilos.monto);
    }

    private static void link(Row row, int c, String url, Workbook wb, Estilos estilos) {
        Cell cell = row.createCell(c);
        if (url == null || url.isBlank()) {
            return;
        }
        cell.setCellValue(url);
        Hyperlink h = wb.getCreationHelper().createHyperlink(HyperlinkType.URL);
        h.setAddress(url);
        cell.setHyperlink(h);
        cell.setCellStyle(estilos.link);
    }
}
