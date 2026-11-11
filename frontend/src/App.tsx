import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthProvider";
import { RealtimeProvider } from "./realtime/RealtimeProvider";
import CapaCarga from "./components/CapaCarga";
import { ConfirmacionProvider } from "./components/ui/ConfirmacionProvider";
import ProtectedLayout from "./components/ProtectedLayout";
import Login from "./pages/Login";
import Inicio from "./pages/Inicio";
import CambiarPassword from "./pages/CambiarPassword";
import {
  Requerimientos,
  RequerimientosNuevo,
  RequerimientoDetalle,
  Aprobaciones,
  Presupuestos,
  PresupuestoDetalle,
  Stock,
  StockAjustes,
  StockCarga,
  CatalogoProductos,
  Proveedores,
  ProveedorForm,
  Compras,
  ComprasCajaChica,
  ComprasConsolidado,
  ComprasComparativo,
  ComprasRfqDetalle,
  ComprasOrdenes,
  ComprasOrdenDetalle,
  Almacen,
  AlmacenRecepcion,
  AlmacenCargaMasiva,
  AlmacenDespacho,
  NotaInventarioDetalle,
  Administracion,
} from "./paginas";

export default function App() {
  return (
    <AuthProvider>
      <RealtimeProvider>
        <ConfirmacionProvider>
          <CapaCarga />
          <Routes>
            <Route path="/" element={<Navigate to="/inicio" replace />} />
            <Route path="/login" element={<Login />} />
            <Route element={<ProtectedLayout />}>
              <Route path="/inicio" element={<Inicio />} />
              <Route path="/requerimientos" element={<Requerimientos />} />
              <Route path="/requerimientos/nuevo" element={<RequerimientosNuevo />} />
              <Route path="/requerimientos/:id/editar" element={<RequerimientosNuevo />} />
              <Route path="/requerimientos/:id" element={<RequerimientoDetalle />} />
              <Route path="/aprobaciones" element={<Aprobaciones />} />
              <Route path="/presupuestos" element={<Presupuestos />} />
              <Route path="/presupuestos/:id" element={<PresupuestoDetalle />} />
              <Route path="/stock" element={<Stock />} />
              <Route path="/stock/ajustes" element={<StockAjustes />} />
              <Route path="/stock/carga" element={<StockCarga />} />
              <Route path="/catalogo" element={<CatalogoProductos />} />
              <Route path="/proveedores" element={<Proveedores />} />
              <Route path="/proveedores/nuevo" element={<ProveedorForm />} />
              <Route path="/proveedores/:id/editar" element={<ProveedorForm />} />
              <Route path="/compras" element={<Compras />} />
              <Route path="/compras/requerimientos/:id/caja-chica" element={<ComprasCajaChica />} />
              <Route path="/compras/consolidados/:id" element={<ComprasConsolidado />} />
              <Route path="/compras/comparativo" element={<ComprasComparativo />} />
              <Route path="/compras/rfq/:id" element={<ComprasRfqDetalle />} />
              <Route path="/compras/ordenes" element={<ComprasOrdenes />} />
              <Route path="/compras/ordenes/:id" element={<ComprasOrdenDetalle />} />
              <Route path="/almacen" element={<Almacen />} />
              <Route path="/almacen/carga-masiva" element={<AlmacenCargaMasiva />} />
              <Route path="/almacen/ordenes/:id/recepcion" element={<AlmacenRecepcion />} />
              <Route path="/almacen/requerimientos/:id/despacho" element={<AlmacenDespacho />} />
              <Route path="/almacen/notas/:id" element={<NotaInventarioDetalle />} />
              <Route path="/admin" element={<Administracion />} />
              <Route path="/cambiar-password" element={<CambiarPassword />} />
            </Route>
          </Routes>
        </ConfirmacionProvider>
      </RealtimeProvider>
    </AuthProvider>
  );
}
