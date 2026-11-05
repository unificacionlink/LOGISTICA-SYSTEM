package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Almacen;
import com.tdem.logistica.modules.stock.dto.CargaStockDto;
import com.tdem.logistica.modules.stock.dto.SaldoStockDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inventario fisico por Excel (DEC-L045): la plantilla trae todos los SKU del
 * catalogo con su stock en el sistema; Logistica escribe el "Stock contado"
 * (lo que hay de verdad) y, si quiere, el "Stock minimo". Cada diferencia se
 * registra como un AJUSTE (reference_type CARGA_STOCK) de una carga INV-n.
 * Subir el mismo archivo dos veces no duplica: la segunda vez no hay
 * diferencias. Igual que las otras cargas: validar no escribe y confirmar
 * re-valida y aplica todo o nada.
 */
@Service
@Transactional(readOnly = true)
public class CargaStockService {

    static final String HOJA = "INVENTARIO";
    static final String COL_CODIGO = "Código";
    static final String COL_SKU = "SKU base";
    static final String COL_PRODUCTO = "Producto";
    static final String COL_ATRIBUTO = "Atributo";
    static final String COL_FAMILIA = "Familia";
    static final String COL_UNIDAD = "Unidad";
    static final String COL_SISTEMA = "Stock sistema";
    static final String COL_RESERVADO = "Reservado";
    static final String COL_CONTADO = "Stock contado";
    static final String COL_MINIMO = "Stock mínimo";
    static final List<String> COLUMNAS = List.of(COL_CODIGO, COL_SKU, COL_PRODUCTO, COL_ATRIBUTO, COL_FAMILIA,
            COL_UNIDAD, COL_SISTEMA, COL_RESERVADO, COL_CONTADO, COL_MINIMO);
    private static final int MAX_FILAS = 5000;

    private final StockService stockService;
    private final AlmacenCentral almacenCentral;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public CargaStockService(StockService stockService, AlmacenCentral almacenCentral, JdbcTemplate jdbc,
                             Auditoria auditoria) {
        this.stockService = stockService;
        this.almacenCentral = almacenCentral;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- plantilla

    public byte[] plantilla(UsuarioPrincipal principal) {
        StockService.exigirLogisticaOAdmin(principal);
        List<SaldoStockDto> saldos = stockService.saldos(almacenCentral.obtener().getWarehouseId());
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet(HOJA);
            DataFormat formato = wb.createDataFormat();
            CellStyle cabecera = estilo(wb, IndexedColors.GREY_25_PERCENT);
            CellStyle cabeceraEditable = estilo(wb, IndexedColors.LIGHT_YELLOW);
            CellStyle numero = wb.createCellStyle();
            numero.setDataFormat(formato.getFormat("#,##0.###"));
            CellStyle editable = wb.createCellStyle();
            editable.setDataFormat(formato.getFormat("#,##0.###"));
            editable.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            editable.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            editable.setLocked(false);

            Row h = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                boolean esEditable = COLUMNAS.get(c).equals(COL_CONTADO) || COLUMNAS.get(c).equals(COL_MINIMO);
                cell.setCellStyle(esEditable ? cabeceraEditable : cabecera);
            }
            int fila = 1;
            for (SaldoStockDto s : saldos) {
                Row row = hoja.createRow(fila++);
                row.createCell(0).setCellValue(s.codigoVariante());
                row.createCell(1).setCellValue(s.skuBase());
                row.createCell(2).setCellValue(s.nombreProducto());
                row.createCell(3).setCellValue(s.atributo() != null ? s.atributo() : "");
                row.createCell(4).setCellValue(s.tipoNombre() + " > " + s.familiaNombre());
                row.createCell(5).setCellValue(s.unidad());
                celdaNumero(row, 6, s.fisico(), numero);
                celdaNumero(row, 7, s.reservado(), numero);
                Cell contado = row.createCell(8);
                contado.setCellStyle(editable);
                Cell minimo = row.createCell(9);
                minimo.setCellStyle(editable);
                if (s.minimo() != null) {
                    minimo.setCellValue(s.minimo().doubleValue());
                }
            }
            // Solo numeros >= 0 en las columnas editables.
            DataValidationHelper dv = hoja.getDataValidationHelper();
            DataValidation v = dv.createValidation(
                    dv.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null),
                    new CellRangeAddressList(1, Math.max(1, saldos.size()), 8, 9));
            v.setShowErrorBox(true);
            v.createErrorBox("Cantidad inválida", "Escribe un número mayor o igual a 0.");
            hoja.addValidationData(v);
            hoja.createFreezePane(1, 1);
            hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, saldos.size()), 0, COLUMNAS.size() - 1));
            int[] anchos = {14, 11, 44, 14, 30, 8, 13, 11, 14, 13};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CellStyle estilo(Workbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        Font negrita = wb.createFont();
        negrita.setBold(true);
        s.setFont(negrita);
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static void celdaNumero(Row row, int c, BigDecimal valor, CellStyle estilo) {
        Cell cell = row.createCell(c);
        cell.setCellValue(valor.doubleValue());
        cell.setCellStyle(estilo);
    }

    // ---------------------------------------------------------------- validar / aplicar

    public CargaStockDto validar(MultipartFile archivo, UsuarioPrincipal principal) {
        StockService.exigirLogisticaOAdmin(principal);
        Almacen almacen = almacenCentral.obtener();
        return analizar(archivo, almacen).resultado(null, false);
    }

    @Transactional
    public CargaStockDto confirmar(MultipartFile archivo, UsuarioPrincipal principal) {
        StockService.exigirLogisticaOAdmin(principal);
        // Lock del almacen: el conteo se compara con el saldo de este instante y nadie mas lo mueve en medio.
        Almacen almacen = almacenCentral.bloquear();
        Analisis a = analizar(archivo, almacen);
        if (a.conError() > 0) {
            throw ApiException.badRequest("El archivo tiene " + a.conError()
                    + " fila(s) con error. Corrigelas y vuelve a validar; no se aplico ningun cambio.");
        }
        List<Plan> ajustes = a.planes.stream().filter(p -> p.diferencia != null && p.diferencia.signum() != 0).toList();
        List<Plan> minimos = a.planes.stream().filter(p -> p.cambiaMinimo).toList();
        if (ajustes.isEmpty() && minimos.isEmpty()) {
            throw ApiException.badRequest("El archivo no tiene cambios: el stock contado coincide con el sistema.");
        }

        Integer ultimo = jdbc.queryForObject("select coalesce(max(load_number), 0) from carga_stock", Integer.class);
        int numero = (ultimo == null ? 0 : ultimo) + 1;
        String codigo = "INV-" + numero;
        UUID cargaId = UUID.randomUUID();
        jdbc.update("""
                insert into carga_stock (stock_load_id, load_number, code, warehouse_id, file_name, rows_counted,
                                         adjustments, minimums_changed, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                cargaId, numero, codigo, almacen.getWarehouseId(), a.archivo, a.contadas(), ajustes.size(),
                minimos.size(), principal.userId());

        UUID empresa = jdbc.queryForObject("select company_id from almacen where warehouse_id = ?", UUID.class,
                almacen.getWarehouseId());
        List<Object[]> movimientos = new ArrayList<>(ajustes.size());
        for (Plan p : ajustes) {
            movimientos.add(new Object[] {UUID.randomUUID(), empresa, almacen.getWarehouseId(), p.saldo.productId(),
                    p.saldo.variantId(), p.diferencia, cargaId, principal.userId(), "Inventario " + codigo});
        }
        jdbc.batchUpdate("""
                insert into movimiento_inventario (inventory_movement_id, company_id, warehouse_id, product_id,
                       variant_id, movement_type, qty, reference_type, reference_id, created_by, notes)
                values (?, ?, ?, ?, ?, 'AJUSTE', ?, 'CARGA_STOCK', ?, ?, ?)""", movimientos);
        for (Plan p : minimos) {
            stockService.guardarMinimo(almacen.getWarehouseId(), p.saldo.variantId(), p.minimoNuevo, principal.userId());
        }

        Map<String, Object> despues = new HashMap<>();
        despues.put("codigo", codigo);
        despues.put("archivo", a.archivo);
        despues.put("ajustes", ajustes.size());
        despues.put("minimos", minimos.size());
        auditoria.registrar("CARGA_STOCK", cargaId, "APLICAR", null, despues, principal.userId());
        return a.resultado(codigo, true);
    }

    /** Lo que se hara con una fila valida. diferencia null = sin conteo; minimoNuevo null = quitar el minimo. */
    private static final class Plan {
        final SaldoStockDto saldo;
        final BigDecimal diferencia;
        final boolean cambiaMinimo;
        final BigDecimal minimoNuevo;

        Plan(SaldoStockDto saldo, BigDecimal diferencia, boolean cambiaMinimo, BigDecimal minimoNuevo) {
            this.saldo = saldo;
            this.diferencia = diferencia;
            this.cambiaMinimo = cambiaMinimo;
            this.minimoNuevo = minimoNuevo;
        }
    }

    private static final class Analisis {
        final String archivo;
        int filas;
        final List<Plan> planes = new ArrayList<>();
        final List<CargaStockDto.Fila> detalle = new ArrayList<>();

        Analisis(String archivo) {
            this.archivo = archivo;
        }

        int conError() {
            return (int) detalle.stream().filter(f -> f.error() != null).count();
        }

        int contadas() {
            return (int) planes.stream().filter(p -> p.diferencia != null).count();
        }

        CargaStockDto resultado(String codigo, boolean aplicada) {
            int ajustes = (int) planes.stream().filter(p -> p.diferencia != null && p.diferencia.signum() != 0).count();
            int minimos = (int) planes.stream().filter(p -> p.cambiaMinimo).count();
            int sinCambios = (int) planes.stream()
                    .filter(p -> (p.diferencia == null || p.diferencia.signum() == 0) && !p.cambiaMinimo).count();
            return new CargaStockDto(archivo, codigo, filas, contadas(), ajustes, minimos, sinCambios, conError(),
                    aplicada, detalle);
        }
    }

    private Analisis analizar(MultipartFile archivo, Almacen almacen) {
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "inventario.xlsx";
        Analisis a = new Analisis(nombre);
        Map<String, SaldoStockDto> porCodigo = stockService.saldos(almacen.getWarehouseId()).stream()
                .collect(Collectors.toMap(s -> s.codigoVariante().toUpperCase(Locale.ROOT), Function.identity(), (x, y) -> x));
        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheet(HOJA) != null ? wb.getSheet(HOJA) : wb.getSheetAt(0);
            Map<String, Integer> col = columnas(hoja.getRow(hoja.getFirstRowNum()));
            if (!col.containsKey(norm(COL_CODIGO)) || (!col.containsKey(norm(COL_CONTADO)) && !col.containsKey(norm(COL_MINIMO)))) {
                throw ApiException.badRequest("El archivo no tiene el formato de la plantilla (columnas \"" + COL_CODIGO
                        + "\" y \"" + COL_CONTADO + "\"). Descarga la plantilla desde el sistema.");
            }
            if (hoja.getLastRowNum() > MAX_FILAS) {
                throw ApiException.badRequest("El archivo supera el maximo de " + MAX_FILAS + " filas.");
            }
            DataFormatter formato = new DataFormatter(Locale.US);
            FormulaEvaluator evaluador = wb.getCreationHelper().createFormulaEvaluator();
            Set<String> vistos = new HashSet<>();
            for (int r = hoja.getFirstRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) {
                    continue;
                }
                String codigo = texto(row, col.get(norm(COL_CODIGO)), formato, evaluador).toUpperCase(Locale.ROOT);
                Lectura contado = numero(row, col.get(norm(COL_CONTADO)), formato, evaluador);
                Lectura minimo = numero(row, col.get(norm(COL_MINIMO)), formato, evaluador);
                if (codigo.isEmpty() && contado.vacio() && minimo.vacio()) {
                    continue;
                }
                a.filas++;
                validarFila(r + 1, codigo, contado, minimo, porCodigo, vistos, a);
            }
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw ApiException.badRequest("No se pudo leer el archivo. Sube el Excel (.xlsx) de la plantilla.");
        }
        if (a.filas == 0) {
            throw ApiException.badRequest("El archivo no tiene filas.");
        }
        return a;
    }

    private static void validarFila(int numero, String codigo, Lectura contado, Lectura minimo,
                                    Map<String, SaldoStockDto> porCodigo, Set<String> vistos, Analisis a) {
        SaldoStockDto s = porCodigo.get(codigo);
        String producto = s != null ? s.nombreProducto() + (s.atributo() != null ? " · " + s.atributo() : "") : null;
        String error = null;
        if (codigo.isEmpty()) {
            error = "Falta el codigo.";
        } else if (s == null) {
            error = "El codigo " + codigo + " no existe en el catalogo.";
        } else if (!vistos.add(codigo)) {
            error = "El codigo " + codigo + " esta repetido en el archivo.";
        } else if (contado.error() != null) {
            error = "Stock contado: " + contado.error();
        } else if (minimo.error() != null) {
            error = "Stock minimo: " + minimo.error();
        }
        if (error != null) {
            a.detalle.add(new CargaStockDto.Fila(numero, codigo, producto, s != null ? s.fisico() : null,
                    contado.valor(), null, s != null ? s.minimo() : null, minimo.valor(), null, error));
            return;
        }
        BigDecimal diferencia = contado.vacio() ? null : contado.valor().subtract(s.fisico());
        // Minimo: en blanco = no se cambia; 0 = se quita.
        BigDecimal minimoNuevo = minimo.vacio() ? s.minimo()
                : minimo.valor().signum() == 0 ? null : minimo.valor();
        boolean cambiaMinimo = !minimo.vacio() && !mismoValor(minimoNuevo, s.minimo());
        String aviso = !contado.vacio() && contado.valor().compareTo(s.reservado()) < 0
                ? "Lo contado es menor que lo reservado (" + s.reservado().stripTrailingZeros().toPlainString()
                        + "): habra requerimientos sin stock para despachar."
                : null;
        a.planes.add(new Plan(s, diferencia, cambiaMinimo, minimoNuevo));
        boolean hayCambio = (diferencia != null && diferencia.signum() != 0) || cambiaMinimo;
        if (hayCambio || aviso != null) {
            a.detalle.add(new CargaStockDto.Fila(numero, s.codigoVariante(), producto, s.fisico(), contado.valor(),
                    diferencia, s.minimo(), cambiaMinimo ? minimoNuevo : s.minimo(), aviso, null));
        }
    }

    private static boolean mismoValor(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    // ---------------------------------------------------------------- lectura de celdas

    /** Numero leido de una celda: vacio, valor >= 0 con hasta 3 decimales, o error. */
    private record Lectura(BigDecimal valor, String error) {
        boolean vacio() {
            return valor == null && error == null;
        }
    }

    private static Lectura numero(Row row, Integer c, DataFormatter formato, FormulaEvaluator evaluador) {
        if (c == null) {
            return new Lectura(null, null);
        }
        Cell cell = row.getCell(c);
        if (cell == null) {
            return new Lectura(null, null);
        }
        BigDecimal valor;
        try {
            CellType tipo = cell.getCellType() == CellType.FORMULA ? evaluador.evaluateFormulaCell(cell) : cell.getCellType();
            if (tipo == CellType.NUMERIC) {
                valor = BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String t = formato.formatCellValue(cell, evaluador).trim().replace(" ", "");
                if (t.isEmpty()) {
                    return new Lectura(null, null);
                }
                // "12,5" (Excel en espanol) -> 12.5; "1,250.5" -> 1250.5
                t = t.contains(".") ? t.replace(",", "") : t.replace(",", ".");
                valor = new BigDecimal(t);
            }
        } catch (NumberFormatException | IllegalStateException e) {
            return new Lectura(null, "no es un numero.");
        }
        valor = valor.stripTrailingZeros();
        if (valor.signum() < 0) {
            return new Lectura(null, "no puede ser negativo.");
        }
        if (valor.scale() > 3) {
            return new Lectura(null, "usa como maximo 3 decimales.");
        }
        return new Lectura(valor.scale() < 0 ? valor.setScale(0) : valor, null);
    }

    private static String texto(Row row, Integer c, DataFormatter formato, FormulaEvaluator evaluador) {
        if (c == null || row.getCell(c) == null) {
            return "";
        }
        return formato.formatCellValue(row.getCell(c), evaluador).trim();
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

    /** Sin tildes ni mayusculas: "Código" y "Stock mínimo" tambien valen. */
    private static String norm(String s) {
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
