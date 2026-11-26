import {
  Boxes,
  CheckSquare,
  ClipboardList,
  FolderArchive,
  Images,
  LayoutDashboard,
  PiggyBank,
  ShieldCheck,
  ShoppingCart,
  Truck,
  Wallet,
  Warehouse,
} from "lucide-react";

/** Menu principal: lo usan el sidebar y la busqueda rapida (Ctrl+K). */
export interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  roles: readonly string[] | null;
}

export const GESTION = ["LOGISTICA", "ADMIN"] as const;

/** Menu por secciones; una seccion sin items visibles para el rol no se muestra. */
export const NAV_GRUPOS: { id: string; titulo: string | null; items: NavItem[] }[] = [
  { id: "inicio", titulo: null, items: [{ to: "/inicio", label: "Inicio", icon: LayoutDashboard, roles: null }] },
  {
    id: "solicitudes",
    titulo: "Solicitudes",
    items: [
      { to: "/requerimientos", label: "Requerimientos", icon: ClipboardList, roles: null },
      { to: "/aprobaciones", label: "Aprobaciones", icon: CheckSquare, roles: ["APROBADOR", "GERENCIA", "LOGISTICA", "ADMIN"] },
      { to: "/presupuestos", label: "Presupuestos", icon: PiggyBank, roles: null },
      // DEC-L092: compras del proyecto con caja chica, con su factura.
      { to: "/caja-chica", label: "Caja chica", icon: Wallet, roles: null },
    ],
  },
  {
    id: "abastecimiento",
    titulo: "Abastecimiento",
    items: [
      { to: "/compras", label: "Compras", icon: ShoppingCart, roles: GESTION },
      { to: "/proveedores", label: "Proveedores", icon: Truck, roles: GESTION },
    ],
  },
  {
    id: "inventario",
    titulo: "Inventario",
    items: [
      { to: "/almacen", label: "Almacén", icon: Warehouse, roles: GESTION },
      { to: "/stock", label: "Stock", icon: Boxes, roles: GESTION },
      { to: "/catalogo", label: "Catálogo de productos", icon: Images, roles: [...GESTION, "PYC", "GERENCIA"] },
    ],
  },
  {
    id: "sistema",
    titulo: "Sistema",
    items: [
      // DEC-L091: repositorio de PDF y archivos por tipo, empresa y mes.
      { to: "/documentos", label: "Documentos", icon: FolderArchive, roles: [...GESTION, "GERENCIA"] },
      { to: "/admin", label: "Administración", icon: ShieldCheck, roles: ["ADMIN"] },
    ],
  },
];

export const EVENTO_BUSQUEDA = "busqueda-rapida:abrir";

/** Abre la busqueda rapida (Ctrl+K) desde cualquier boton: sidebar, cabecera movil. */
export function abrirBusquedaRapida() {
  window.dispatchEvent(new Event(EVENTO_BUSQUEDA));
}
