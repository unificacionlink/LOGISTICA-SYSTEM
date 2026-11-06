package com.tdem.logistica.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Sirve el SPA de React para rutas de navegador cuando el backend empaqueta
 * el build de frontend/ en resources/static (ver Dockerfile). Solo aplica
 * al despliegue de un unico Web Service; en desarrollo local el frontend
 * corre aparte con Vite (:5173).
 *
 * Rutas explicitas (no un regex catch-all): un catch-all demasiado amplio
 * puede terminar matcheando su propio destino ("/index.html") o rutas
 * reservadas como "/ws" (WebSocket), causando un forward recursivo hasta
 * StackOverflowError -- ya paso una vez en este proyecto.
 */
@Controller
public class SpaController {

    @RequestMapping({
            "/login",
            "/inicio",
            "/requerimientos",
            "/requerimientos/nuevo",
            "/requerimientos/{id}",
            "/requerimientos/{id}/editar",
            "/aprobaciones",
            "/presupuestos",
            "/presupuestos/{id}",
            "/stock",
            "/stock/ajustes",
            "/stock/carga",
            "/catalogo",
            "/proveedores",
            "/proveedores/nuevo",
            "/proveedores/{id}/editar",
            "/compras",
            "/compras/requerimientos/{id}/caja-chica",
            "/compras/consolidados/{id}",
            "/compras/comparativo",
            "/compras/rfq/{id}",
            "/compras/ordenes",
            "/compras/ordenes/{id}",
            "/almacen",
            "/almacen/carga-masiva",
            "/almacen/ordenes/{id}/recepcion",
            "/almacen/requerimientos/{id}/despacho",
            "/almacen/notas/{id}",
            "/admin",
            "/cambiar-password",
    })
    public String forward() {
        return "forward:/index.html";
    }
}
