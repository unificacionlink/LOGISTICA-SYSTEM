package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.entity.OrdenItem;
import com.tdem.logistica.modules.almacen.dto.CargaMasivaDto;
import com.tdem.logistica.modules.almacen.dto.CargaMasivaFilaDto;
import com.tdem.logistica.modules.almacen.dto.GuiaInput;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.repository.GuiaRemisionRepository;
import com.tdem.logistica.repository.OrdenItemRepository;
import com.tdem.logistica.repository.RecepcionItemRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF-031: carga masiva de ingresos a almacen. Logistica descarga una
 * plantilla con todo lo pendiente de recibir de las OC abiertas, llena
 * "Cantidad a ingresar" en lo que llego y la sube. Primero se valida el
 * archivo completo sin tocar stock; al confirmar se vuelve a validar y se
 * registra una recepcion por OC (origen CARGA_MASIVA) en una sola
 * transaccion: o entran todas las filas o ninguna.
 *
 * Incidencias (dano, faltante, producto errado) no se cargan por Excel: se
 * registran en la pantalla de recepcion de la OC.
 */
@Service
@Transactional(readOnly = true)
public class CargaMasivaService {

    static final String HOJA = "INGRESOS_PENDIENTES";
    static final String COL_ORDEN = "Orden de compra";
    static final String COL_EMPRESA = "Empresa";
    static final String COL_PROVEEDOR = "Proveedor";
    static final String COL_REQUERIMIENTO = "Requerimiento";
    static final String COL_SKU = "SKU";
    static final String COL_PRODUCTO = "Producto";
    static final String COL_UNIDAD = "Unidad";
    static final String COL_PEDIDO = "Pedido";
    static final String COL_RECIBIDO = "Recibido";
    static final String COL_PENDIENTE = "Pendiente";
    static final String COL_CANTIDAD = "Cantidad a ingresar";
    static final String COL_ESTADO = "Estado producto";
    static final String COL_OBSERVACION = "Observacion";
    static final String COL_GUIA_SERIE = "Guia serie";
    static final String COL_GUIA_NUMERO = "Guia numero";
    static final String COL_GUIA_FECHA = "Guia fecha";
    static final String COL_ID = "ID item (no modificar)";

    static final List<String> COLUMNAS = List.of(COL_ORDEN, COL_EMPRESA, COL_PROVEEDOR, COL_REQUERIMIENTO, COL_SKU,
            COL_PRODUCTO, COL_UNIDAD, COL_PEDIDO, COL_RECIBIDO, COL_PENDIENTE, COL_CANTIDAD, COL_ESTADO,
            COL_OBSERVACION, COL_GUIA_SERIE, COL_GUIA_NUMERO, COL_GUIA_FECHA, COL_ID);
    private static final Set<String> EDITABLES = Set.of(COL_CANTIDAD, COL_ESTADO, COL_OBSERVACION,
            COL_GUIA_SERIE, COL_GUIA_NUMERO, COL_GUIA_FECHA);
    private static final int MAX_FILAS = 2000;
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d-M-yyyy"));

    private final OrdenItemRepository ordenItemRepository;
    private final RecepcionItemRepository recepcionItemRepository;
    private final GuiaRemisionRepository guiaRepository;
    private final RecepcionService recepcionService;

    public CargaMasivaService(OrdenItemRepository ordenItemRepository, RecepcionItemRepository recepcionItemRepository,
                              GuiaRemisionRepository guiaRepository, RecepcionService recepcionService) {
        this.ordenItemRepository = ordenItemRepository;
        this.recepcionItemRepository = recepcionItemRepository;
        this.guiaRepository = guiaRepository;
        this.recepcionService = recepcionService;
    }

    // ---------------------------------------------------------------- plantilla

    public byte[] plantilla(UsuarioPrincipal principal) {
        RecepcionService.exigirLogisticaOAdmin(principal);
        List<OrdenItem> items = ordenItemRepository.findPorRecibir(RecepcionService.ESTADOS_POR_RECIBIR);
        Map<UUID, BigDecimal> recibido = recibidoPorItem(items);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet(HOJA);
            CellStyle cabecera = estiloCabecera(wb, IndexedColors.GREY_25_PERCENT);
            CellStyle cabeceraEditable = estiloCabecera(wb, IndexedColors.LIGHT_YELLOW);
            CellStyle editable = wb.createCellStyle();
            editable.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            editable.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle fechaEditable = wb.createCellStyle();
            fechaEditable.cloneStyleFrom(editable);
            fechaEditable.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));

            Row h = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(EDITABLES.contains(COLUMNAS.get(c)) ? cabeceraEditable : cabecera);
            }

            int fila = 1;
            for (OrdenItem oi : items) {
                BigDecimal rec = recibido.getOrDefault(oi.getOrderItemId(), BigDecimal.ZERO);
                BigDecimal pendiente = oi.getQtyOrdered().subtract(rec);
                if (pendiente.signum() <= 0) {
                    continue;
                }
                OrdenAbastecimiento o = oi.getOrden();
                Row row = hoja.createRow(fila++);
                int c = 0;
                row.createCell(c++).setCellValue(o.getOrderCode());
                row.createCell(c++).setCellValue(o.getEmpresa().getCode());
                row.createCell(c++).setCellValue(o.getProveedor().getLegalName());
                row.createCell(c++).setCellValue(o.getCotizacion().getRfq().getRequerimiento().getRequestCode());
                row.createCell(c++).setCellValue(sku(oi));
                row.createCell(c++).setCellValue(producto(oi));
                row.createCell(c++).setCellValue(oi.getUom() != null ? oi.getUom().getCode() : "");
                row.createCell(c++).setCellValue(oi.getQtyOrdered().doubleValue());
                row.createCell(c++).setCellValue(rec.doubleValue());
                row.createCell(c++).setCellValue(pendiente.doubleValue());
                for (int e = 0; e < 5; e++) {
                    row.createCell(c++).setCellStyle(editable);
                }
                row.getCell(COLUMNAS.indexOf(COL_ESTADO)).setCellValue("CONFORME");
                row.createCell(c++).setCellStyle(fechaEditable);
                row.createCell(c).setCellValue(oi.getOrderItemId().toString());
            }

            if (fila > 1) {
                DataValidationHelper dv = hoja.getDataValidationHelper();
                int colEstado = COLUMNAS.indexOf(COL_ESTADO);
                DataValidation v = dv.createValidation(
                        dv.createExplicitListConstraint(RecepcionService.CONDICIONES.stream().sorted().toArray(String[]::new)),
                        new CellRangeAddressList(1, fila - 1, colEstado, colEstado));
                v.setShowErrorBox(true);
                hoja.addValidationData(v);
            }
            hoja.createFreezePane(0, 1);
            int[] anchos = {16, 11, 30, 14, 16, 42, 9, 10, 10, 11, 12, 15, 26, 11, 13, 12, 38};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- validar / aplicar

    public CargaMasivaDto validar(MultipartFile archivo, UsuarioPrincipal principal) {
        RecepcionService.exigirLogisticaOAdmin(principal);
        return analizar(archivo).resultado(false);
    }

    @Transactional
    public CargaMasivaDto confirmar(MultipartFile archivo, UsuarioPrincipal principal) {
        RecepcionService.exigirLogisticaOAdmin(principal);
        Analisis a = analizar(archivo);
        if (a.conError() > 0) {
            throw ApiException.badRequest("El archivo tiene " + a.conError()
                    + " fila(s) con error. Corrigelas y vuelve a validar; no se registro ningun ingreso.");
        }
        if (a.validas.isEmpty()) {
            throw ApiException.badRequest("El archivo no tiene cantidades a ingresar.");
        }
        Map<UUID, List<FilaValida>> porOrden = a.validas.stream()
                .collect(Collectors.groupingBy(f -> f.item().getOrden().getOrderId(), LinkedHashMap::new, Collectors.toList()));
        for (List<FilaValida> filas : porOrden.values()) {
            List<RecepcionItemInput> items = filas.stream()
                    .map(f -> new RecepcionItemInput(f.item().getOrderItemId(), f.cantidad(), f.estado(), f.observacion(), null))
                    .toList();
            GuiaInput guia = filas.stream().map(FilaValida::guia).filter(Objects::nonNull).findFirst().orElse(null);
            recepcionService.registrarRecepcion(filas.get(0).item().getOrden().getOrderId(),
                    new RegistrarRecepcionRequest(items, "Carga masiva: " + a.archivo, guia), principal, "CARGA_MASIVA");
        }
        return a.resultado(true);
    }

    private record FilaValida(OrdenItem item, BigDecimal cantidad, String estado, String observacion, GuiaInput guia) {
    }

    private static final class Analisis {
        final String archivo;
        final List<CargaMasivaFilaDto> filas = new ArrayList<>();
        final List<FilaValida> validas = new ArrayList<>();
        int omitidas;

        Analisis(String archivo) {
            this.archivo = archivo;
        }

        int conError() {
            return (int) filas.stream().filter(f -> f.error() != null).count();
        }

        CargaMasivaDto resultado(boolean aplicada) {
            long ordenes = validas.stream().map(f -> f.item().getOrden().getOrderId()).distinct().count();
            return new CargaMasivaDto(archivo, omitidas, filas.size() - conError(), conError(), (int) ordenes,
                    aplicada, filas);
        }
    }

    /** Lee el Excel completo y valida cada fila sin escribir nada. */
    private Analisis analizar(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "archivo.xlsx";
        Analisis a = new Analisis(nombre);
        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheet(HOJA) != null ? wb.getSheet(HOJA) : wb.getSheetAt(0);
            Map<String, Integer> col = columnas(hoja.getRow(hoja.getFirstRowNum()));
            if (!col.containsKey(norm(COL_ID)) || !col.containsKey(norm(COL_CANTIDAD))) {
                throw ApiException.badRequest("El archivo no tiene el formato de la plantilla (faltan las columnas \""
                        + COL_CANTIDAD + "\" o \"" + COL_ID + "\"). Descarga la plantilla desde el sistema.");
            }
            if (hoja.getLastRowNum() > MAX_FILAS) {
                throw ApiException.badRequest("El archivo supera el maximo de " + MAX_FILAS + " filas.");
            }
            DataFormatter formato = new DataFormatter(Locale.US);
            FormulaEvaluator evaluador = wb.getCreationHelper().createFormulaEvaluator();
            Lector lector = new Lector(col, formato, evaluador);

            List<Row> conCantidad = new ArrayList<>();
            for (int r = hoja.getFirstRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null || lector.texto(row, COL_CANTIDAD).isEmpty()) {
                    if (row != null && !lector.texto(row, COL_ID).isEmpty()) {
                        a.omitidas++;
                    }
                    continue;
                }
                conCantidad.add(row);
            }

            Set<UUID> ids = new HashSet<>();
            for (Row row : conCantidad) {
                uuid(lector.texto(row, COL_ID)).ifPresent(ids::add);
            }
            Map<UUID, OrdenItem> items = ordenItemRepository.findPorRecibir(RecepcionService.ESTADOS_POR_RECIBIR).stream()
                    .filter(i -> ids.contains(i.getOrderItemId()))
                    .collect(Collectors.toMap(OrdenItem::getOrderItemId, Function.identity()));
            Map<UUID, BigDecimal> recibido = recibidoPorItem(items.values());

            Set<UUID> vistos = new HashSet<>();
            Map<UUID, String> guiaPorOrden = new HashMap<>();
            Map<String, UUID> ordenPorGuia = new HashMap<>();
            for (Row row : conCantidad) {
                validarFila(row, lector, items, recibido, vistos, guiaPorOrden, ordenPorGuia, a);
            }
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw ApiException.badRequest("No se pudo leer el archivo. Sube el Excel (.xlsx) de la plantilla.");
        }
        return a;
    }

    private void validarFila(Row row, Lector lector, Map<UUID, OrdenItem> items, Map<UUID, BigDecimal> recibido,
                             Set<UUID> vistos, Map<UUID, String> guiaPorOrden, Map<String, UUID> ordenPorGuia,
                             Analisis a) {
        int numero = row.getRowNum() + 1;
        String textoCantidad = lector.texto(row, COL_CANTIDAD);
        BigDecimal cantidad = decimal(textoCantidad);
        String estado = lector.texto(row, COL_ESTADO).toUpperCase(Locale.ROOT);
        if (estado.isEmpty()) {
            estado = "CONFORME";
        }
        String observacion = lector.texto(row, COL_OBSERVACION);
        String serie = lector.texto(row, COL_GUIA_SERIE).toUpperCase(Locale.ROOT);
        String numeroGuia = lector.texto(row, COL_GUIA_NUMERO);
        String textoFechaGuia = lector.texto(row, COL_GUIA_FECHA);
        LocalDate fechaGuia = lector.fecha(row, COL_GUIA_FECHA);
        boolean hayGuia = !serie.isEmpty() || !numeroGuia.isEmpty();
        String guiaTexto = hayGuia ? serie + "-" + numeroGuia : null;

        Optional<UUID> id = uuid(lector.texto(row, COL_ID));
        OrdenItem oi = id.map(items::get).orElse(null);
        BigDecimal pendiente = oi == null ? null
                : oi.getQtyOrdered().subtract(recibido.getOrDefault(oi.getOrderItemId(), BigDecimal.ZERO));

        String error = null;
        if (cantidad != null && cantidad.signum() == 0) {
            a.omitidas++;
            return;
        }
        if (id.isEmpty()) {
            error = "Falta el ID del item o fue modificado.";
        } else if (oi == null) {
            error = "El item no existe o su orden ya no admite recepciones.";
        } else if (!vistos.add(oi.getOrderItemId())) {
            error = "El item esta repetido en el archivo.";
        } else if (cantidad == null) {
            error = "Cantidad a ingresar invalida (\"" + textoCantidad + "\").";
        } else if (cantidad.signum() < 0) {
            error = "La cantidad no puede ser negativa.";
        } else if (cantidad.compareTo(pendiente) > 0) {
            error = "Supera lo pendiente (" + plano(pendiente) + ").";
        } else if (!RecepcionService.CONDICIONES.contains(estado)) {
            error = "Estado producto invalido (CONFORME u OBSERVADO).";
        } else if (hayGuia && (serie.isEmpty() || numeroGuia.isEmpty() || fechaGuia == null)) {
            error = !textoFechaGuia.isEmpty() && fechaGuia == null
                    ? "Fecha de guia invalida (usa dd/mm/aaaa)."
                    : "Para registrar la guia completa serie, numero y fecha.";
        } else if (!hayGuia && !textoFechaGuia.isEmpty()) {
            error = "Hay fecha de guia pero falta la serie y el numero.";
        } else if (hayGuia) {
            UUID orderId = oi.getOrden().getOrderId();
            String previa = guiaPorOrden.putIfAbsent(orderId, guiaTexto);
            UUID otraOrden = ordenPorGuia.putIfAbsent(oi.getOrden().getProveedor().getRuc() + "|" + guiaTexto, orderId);
            if (previa != null && !previa.equals(guiaTexto)) {
                error = "Las filas de la " + oi.getOrden().getOrderCode() + " tienen guias distintas (" + previa + ").";
            } else if (otraOrden != null && !otraOrden.equals(orderId)) {
                error = "La guia " + guiaTexto + " ya se uso en otra orden del archivo; registra una guia por orden.";
            } else if (previa == null && guiaRepository.existsByIssuerRucAndSeriesAndNumber(
                    oi.getOrden().getProveedor().getRuc(), serie, numeroGuia)) {
                error = "La guia " + guiaTexto + " ya fue registrada.";
            }
        }

        a.filas.add(new CargaMasivaFilaDto(numero,
                oi != null ? oi.getOrden().getOrderCode() : lector.texto(row, COL_ORDEN),
                oi != null ? oi.getOrden().getProveedor().getLegalName() : lector.texto(row, COL_PROVEEDOR),
                oi != null ? sku(oi) : lector.texto(row, COL_SKU),
                oi != null ? producto(oi) : lector.texto(row, COL_PRODUCTO),
                cantidad, pendiente, estado, guiaTexto, error));
        if (error == null) {
            a.validas.add(new FilaValida(oi, cantidad, estado, observacion.isEmpty() ? null : observacion,
                    hayGuia ? new GuiaInput(serie, numeroGuia, fechaGuia) : null));
        }
    }

    // ---------------------------------------------------------------- utilidades

    private Map<UUID, BigDecimal> recibidoPorItem(Collection<OrdenItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> recibido = new HashMap<>();
        for (Object[] fila : recepcionItemRepository.sumarRecibidoPorItems(
                items.stream().map(OrdenItem::getOrderItemId).toList())) {
            recibido.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        return recibido;
    }

    /** Lee celdas por nombre de columna (el usuario puede reordenarlas). */
    private record Lector(Map<String, Integer> columnas, DataFormatter formato, FormulaEvaluator evaluador) {
        String texto(Row row, String columna) {
            Integer c = columnas.get(norm(columna));
            if (c == null) {
                return "";
            }
            Cell cell = row.getCell(c);
            return cell == null ? "" : formato.formatCellValue(cell, evaluador).trim();
        }

        LocalDate fecha(Row row, String columna) {
            Integer c = columnas.get(norm(columna));
            Cell cell = c == null ? null : row.getCell(c);
            if (cell == null) {
                return null;
            }
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String t = texto(row, columna);
            for (DateTimeFormatter f : FORMATOS_FECHA) {
                try {
                    return LocalDate.parse(t, f);
                } catch (DateTimeParseException ignored) {
                    // se prueba el siguiente formato
                }
            }
            return null;
        }
    }

    private static Map<String, Integer> columnas(Row cabecera) {
        Map<String, Integer> col = new HashMap<>();
        if (cabecera == null) {
            return col;
        }
        for (Cell cell : cabecera) {
            if (cell.getCellType() == CellType.STRING) {
                col.putIfAbsent(norm(cell.getStringCellValue()), cell.getColumnIndex());
            }
        }
        return col;
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<UUID> uuid(String s) {
        try {
            return s.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal decimal(String s) {
        String t = s.replace(" ", "");
        if (t.contains(",") && !t.contains(".")) {
            t = t.replace(',', '.');
        }
        try {
            return new BigDecimal(t.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plano(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    private static String sku(OrdenItem oi) {
        if (oi.getVariante() != null && oi.getVariante().getInternalVariantCode() != null) {
            return oi.getVariante().getInternalVariantCode();
        }
        return oi.getProducto() != null ? oi.getProducto().getSkuBase() : "";
    }

    private static String producto(OrdenItem oi) {
        if (oi.getProducto() == null) {
            return oi.getDescription() != null ? oi.getDescription() : "";
        }
        String atributo = oi.getVariante() != null ? oi.getVariante().getAttributeValue() : null;
        return atributo != null && !atributo.isBlank()
                ? oi.getProducto().getName() + " (" + atributo + ")"
                : oi.getProducto().getName();
    }

    private static CellStyle estiloCabecera(Workbook wb, IndexedColors fondo) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(fondo.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }
}
