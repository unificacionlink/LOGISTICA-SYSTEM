import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RealtimeProvider } from "./realtime/RealtimeProvider";
import CapaCarga from "./components/CapaCarga";
import { ConfirmacionProvider } from "./components/ui/Confirmacion";
import ProtectedLayout from "./components/ProtectedLayout";
import Login from "./pages/Login";
import Inicio from "./pages/Inicio";
import Requerimientos from "./pages/Requerimientos";
import RequerimientosNuevo from "./pages/RequerimientosNuevo";
import RequerimientoDetalle from "./pages/RequerimientoDetalle";
import Aprobaciones from "./pages/Aprobaciones";
import Presupuestos from "./pages/Presupuestos";
import PresupuestoDetalle from "./pages/PresupuestoDetalle";
import Stock from "./pages/Stock";
import StockAjustes from "./pages/StockAjustes";
import CatalogoProductos from "./pages/CatalogoProductos";
import Proveedores from "./pages/Proveedores";
import ProveedorForm from "./pages/ProveedorForm";
import Compras from "./pages/Compras";
import ComprasRfqNuevo from "./pages/ComprasRfqNuevo";
import ComprasRfqDetalle from "./pages/ComprasRfqDetalle";
import ComprasOrdenes from "./pages/ComprasOrdenes";
import ComprasOrdenDetalle from "./pages/ComprasOrdenDetalle";
import Almacen from "./pages/Almacen";
import AlmacenRecepcion from "./pages/AlmacenRecepcion";
import AlmacenCargaMasiva from "./pages/AlmacenCargaMasiva";
import AlmacenDespacho from "./pages/AlmacenDespacho";
import NotaInventarioDetalle from "./pages/NotaInventarioDetalle";
import Administracion from "./pages/Administracion";
import CambiarPassword from "./pages/CambiarPassword";

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
              <Route path="/catalogo" element={<CatalogoProductos />} />
              <Route path="/proveedores" element={<Proveedores />} />
              <Route path="/proveedores/nuevo" element={<ProveedorForm />} />
              <Route path="/proveedores/:id/editar" element={<ProveedorForm />} />
              <Route path="/compras" element={<Compras />} />
              <Route path="/compras/requerimientos/:id/rfq/nuevo" element={<ComprasRfqNuevo />} />
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
