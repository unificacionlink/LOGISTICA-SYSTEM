package com.tdem.logistica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V20 (DEC-L039) sobre datos previos al Lote 16: cada linea de RFQ de un solo
 * REQ pasa a ser un consolidado con su necesidad, las OC quedan enlazadas y el
 * faltante que no estaba en ninguna RFQ queda por decidir ruta. Tambien V31
 * (DEC-L061) sobre esas OC previas.
 */
class MigracionLote16Test {

    private static final String ESQUEMA = "logistica";
    private static EmbeddedPostgres pg;
    private static JdbcTemplate jdbc;
    private static UUID itemA;
    private static UUID itemB;
    private static UUID itemC;
    private static UUID proveedor;
    private static UUID productoC;

    @BeforeAll
    static void migrarConDatosPrevios() throws Exception {
        pg = BdRrhhSimulada.iniciar();
        flyway("19").migrate();
        // Una sola conexion: el search_path fijado vale para todas las consultas del test.
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                pg.getDatabase(BdRrhhSimulada.USUARIO_APP, "postgres").getConnection(), true));
        jdbc.execute("set search_path to " + ESQUEMA);
        sembrarLegado();
        flyway(null).migrate();
    }

    @AfterAll
    static void detener() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void cadaLineaDeRfqPasaAConsolidadoConSuNecesidad() {
        Map<String, Object> a = jdbc.queryForMap("""
                select c.status, c.total_qty, n.status as need, n.qty from rfq_item ri
                join consolidacion c on c.consolidation_id = ri.consolidation_id
                join necesidad_compra n on n.consolidation_id = c.consolidation_id
                where n.request_item_id = ?""", itemA);
        assertEquals("EN_COTIZACION", a.get("status"));
        assertEquals("COMPRA", a.get("need"));
        assertEquals(0, new BigDecimal("6").compareTo((BigDecimal) a.get("qty")));

        Map<String, Object> c = jdbc.queryForMap("""
                select c.status, c.awarded_quotation_item_id from consolidacion c
                join necesidad_compra n on n.consolidation_id = c.consolidation_id where n.request_item_id = ?""", itemC);
        assertEquals("ORDENADA", c.get("status"));
        assertNotNull(c.get("awarded_quotation_item_id"));
        assertEquals(0, jdbc.queryForObject("select count(*) from orden_item where consolidation_id is null", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from rfq_item where consolidation_id is null", Integer.class));
    }

    @Test
    void elFaltanteFueraDeRfqQuedaPorDecidirRuta() {
        assertEquals(0, new BigDecimal("4").compareTo(jdbc.queryForObject("""
                select qty from necesidad_compra where request_item_id = ? and status = 'PENDIENTE_RUTA'""",
                BigDecimal.class, itemA)), "10 pedidos - 6 en RFQ");
        assertEquals(0, new BigDecimal("5").compareTo(jdbc.queryForObject("""
                select qty from necesidad_compra where request_item_id = ? and status = 'PENDIENTE_RUTA'""",
                BigDecimal.class, itemB)));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from necesidad_compra where request_item_id = ? and status = 'PENDIENTE_RUTA'""",
                Integer.class, itemC), "lo ordenado no vuelve a ruta");
    }

    @Test
    void elProveedorHabitualPasaALaCobertura() {
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from proveedor_producto where product_id = ? and supplier_id = ?""",
                Integer.class, productoC, proveedor));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'logistica' and table_name = 'producto' and column_name = 'preferred_supplier_id'""",
                Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.tables where table_schema = 'logistica' and table_name = '_v20_legado'""",
                Integer.class));
    }

    /** V31 (DEC-L061): las OC previas al Lote 17 quedan aprobadas y liberadas, sin pago registrado. */
    @Test
    void v31DejaLasOcExistentesAprobadasYLiberadas() {
        Map<String, Object> oc = jdbc.queryForMap("""
                select status, payment_status, approved_at = created_at as aprobada, released_at = created_at as liberada,
                       approved_by = created_by as por_su_emisor
                  from orden_abastecimiento where order_code = 'OC-LEGADO'""");
        assertEquals("EMITIDA", oc.get("status"));
        assertEquals("PENDIENTE", oc.get("payment_status"));
        assertEquals(Boolean.TRUE, oc.get("aprobada"));
        assertEquals(Boolean.TRUE, oc.get("liberada"));
        assertEquals(Boolean.TRUE, oc.get("por_su_emisor"));
        assertEquals("'POR_APROBAR'::character varying", jdbc.queryForObject("""
                select column_default from information_schema.columns
                 where table_schema = 'logistica' and table_name = 'orden_abastecimiento' and column_name = 'status'""",
                String.class));
    }

    /** V32 (DEC-L062): las NI previas al Lote 18 quedan piqueadas y embaladas, para no frenar su envio. */
    @Test
    void v32DejaLasNotasExistentesPiqueadasYEmbaladas() {
        Map<String, Object> ni = jdbc.queryForMap("""
                select status, picked_at = created_at as piqueada, packed_at = created_at as embalada,
                       picked_by = created_by as por_su_emisor, packing_document_id
                  from nota_inventario where inventory_note_code = 'NI-LEGADO'""");
        assertEquals("EMITIDA", ni.get("status"));
        assertEquals(Boolean.TRUE, ni.get("piqueada"));
        assertEquals(Boolean.TRUE, ni.get("embalada"));
        assertEquals(Boolean.TRUE, ni.get("por_su_emisor"));
        assertNull(ni.get("packing_document_id"), "sin evidencia: no se inventa un archivo");
    }

    /** V33 (DEC-L063): lo recepcionado antes del Lote 18b queda aceptado y conforme. */
    @Test
    void v33DejaLoRecepcionadoComoAceptado() {
        Map<String, Object> si = jdbc.queryForMap("""
                select si.qty, si.dest_received_qty, si.dest_accepted_qty, si.dest_observed_qty, v.conformity_status
                  from salida_item si join envio v on v.issue_id = si.issue_id
                  join nota_inventario n on n.issue_id = si.issue_id
                 where n.inventory_note_code = 'NI-LEGADO-2'""");
        assertEquals(0, ((BigDecimal) si.get("qty")).compareTo((BigDecimal) si.get("dest_received_qty")));
        assertEquals(0, ((BigDecimal) si.get("qty")).compareTo((BigDecimal) si.get("dest_accepted_qty")));
        assertEquals(0, ((BigDecimal) si.get("dest_observed_qty")).signum());
        assertEquals("CONFORME", si.get("conformity_status"));
    }

    // ------------------------------------------------------------------

    private static void sembrarLegado() {
        UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
        UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
        UUID usuario = jdbc.queryForObject("""
                insert into usuario (corporate_email, display_name, company_id, fuente)
                values ('legado@logistica.test', 'Legado', ?, 'MANUAL') returning user_id""", UUID.class, empresa);
        proveedor = jdbc.queryForObject("""
                insert into proveedor (ruc, legal_name) values ('20999888777', 'Proveedor Legado SAC') returning supplier_id""",
                UUID.class);
        UUID req = jdbc.queryForObject("""
                insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
                  purpose, justification, status)
                values ('REQ-LEGADO', ?, ?, ?, 'INTERNO', 'CONSUMO', 'legado', 'EN_ATENCION') returning request_id""",
                UUID.class, empresa, ceco, usuario);
        itemA = item(req, "AC01001-00", 10);
        itemB = item(req, "AC01002-00", 5);
        itemC = item(req, "AC01003-00", 3);
        productoC = jdbc.queryForObject("select product_id from requerimiento_item where request_item_id = ?", UUID.class, itemC);
        jdbc.update("update producto set preferred_supplier_id = ? where product_id = ?", proveedor, productoC);

        // RFQ abierta: 6 de las 10 u. del item A.
        UUID rfq1 = rfq("RFQ-LEG-1", empresa, req, usuario, "ENVIADA");
        rfqItem(rfq1, itemA, 6);

        // RFQ cerrada con cotizacion elegida y OC emitida por el item C.
        UUID rfq2 = rfq("RFQ-LEG-2", empresa, req, usuario, "CERRADA");
        UUID li = rfqItem(rfq2, itemC, 3);
        UUID cot = jdbc.queryForObject("""
                insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
                values (?, ?, 'PEN', 30, 30, 'SELECCIONADA') returning quotation_id""", UUID.class, rfq2, proveedor);
        jdbc.update("""
                insert into cotizacion_item (quotation_id, rfq_item_id, product_id, variant_id, qty, unit_cost, line_total)
                select ?, ?, product_id, variant_id, 3, 10, 30 from rfq_item where rfq_item_id = ?""", cot, li, li);
        UUID orden = jdbc.queryForObject("""
                insert into orden_abastecimiento (order_code, company_id, supplier_id, quotation_id, payment_condition,
                  delivery_mode, currency_code, subtotal, total, status, created_by)
                values ('OC-LEGADO', ?, ?, ?, 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', 30, 30, 'EMITIDA', ?) returning order_id""",
                UUID.class, empresa, proveedor, cot, usuario);
        jdbc.update("""
                insert into orden_item (order_id, request_item_id, product_id, variant_id, description, uom_id,
                  qty_ordered, unit_cost, line_subtotal, line_total)
                select ?, request_item_id, product_id, variant_id, 'Legado', uom_id, 3, 10, 30, 30
                from requerimiento_item where request_item_id = ?""", orden, itemC);

        // NI emitida y todavia sin envio (anterior a V32).
        UUID salida = jdbc.queryForObject("""
                insert into salida (company_id, warehouse_id, issue_type, ceco_id, request_id, created_by)
                values (?, (select warehouse_id from almacen limit 1), 'CONSUMO_INTERNO', ?, ?, ?) returning issue_id""",
                UUID.class, empresa, ceco, req, usuario);
        jdbc.update("""
                insert into nota_inventario (note_number, inventory_note_code, company_id, request_id, issue_id,
                  origin_warehouse_id, movement_type, picking_user_id, created_by)
                select 1, 'NI-LEGADO', ?, ?, ?, warehouse_id, 'CONSUMO', ?, ? from salida where issue_id = ?""",
                empresa, req, salida, usuario, usuario, salida);

        // NI ya recepcionada en destino (anterior a V33): 3 u. del item C.
        UUID salida2 = jdbc.queryForObject("""
                insert into salida (company_id, warehouse_id, issue_type, ceco_id, request_id, created_by)
                values (?, (select warehouse_id from almacen limit 1), 'CONSUMO_INTERNO', ?, ?, ?) returning issue_id""",
                UUID.class, empresa, ceco, req, usuario);
        jdbc.update("insert into salida_item (issue_id, product_id, qty, request_item_id) values (?, ?, 3, ?)",
                salida2, productoC, itemC);
        UUID nota2 = jdbc.queryForObject("""
                insert into nota_inventario (note_number, inventory_note_code, company_id, request_id, issue_id,
                  origin_warehouse_id, movement_type, picking_user_id, created_by, status)
                select 2, 'NI-LEGADO-2', ?, ?, ?, warehouse_id, 'CONSUMO', ?, ?, 'RECEPCIONADA'
                  from salida where issue_id = ? returning inventory_note_id""",
                UUID.class, empresa, req, salida2, usuario, usuario, salida2);
        jdbc.update("""
                insert into envio (shipment_type, request_id, issue_id, inventory_note_id, delivery_mode, status, created_by)
                values ('DESPACHO_REQUERIMIENTO', ?, ?, ?, 'PROPIO', 'RECEPCIONADO', ?)""", req, salida2, nota2, usuario);
    }

    private static UUID item(UUID req, String variante, int qty) {
        return jdbc.queryForObject("""
                insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
                select ?, v.product_id, v.variant_id, p.base_uom_id, ?
                from producto_variante v join producto p on p.product_id = v.product_id
                where v.internal_variant_code = ? returning request_item_id""", UUID.class, req, qty, variante);
    }

    private static UUID rfq(String codigo, UUID empresa, UUID req, UUID usuario, String estado) {
        return jdbc.queryForObject("""
                insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
                values (?, ?, ?, ?, ?, ?) returning rfq_id""", UUID.class, codigo, empresa, req, proveedor, estado, usuario);
    }

    private static UUID rfqItem(UUID rfq, UUID requestItem, int qty) {
        return jdbc.queryForObject("""
                insert into rfq_item (rfq_id, request_item_id, product_id, variant_id, uom_id, qty)
                select ?, request_item_id, product_id, variant_id, uom_id, ? from requerimiento_item
                where request_item_id = ? returning rfq_item_id""", UUID.class, rfq, qty, requestItem);
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(pg.getDatabase(BdRrhhSimulada.USUARIO_APP, "postgres"))
                .locations("classpath:db/migration")
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .createSchemas(false)
                .placeholders(Map.of("schema", ESQUEMA));
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }
}
