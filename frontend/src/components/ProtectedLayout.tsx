import { useState } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import {
  Boxes,
  CheckSquare,
  ClipboardList,
  Images,
  KeyRound,
  LayoutDashboard,
  ShieldCheck,
  LogOut,
  Menu,
  ShoppingCart,
  Truck,
  Warehouse,
  X,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import CambiarPassword from "../pages/CambiarPassword";
import PantallaCarga from "./PantallaCarga";
import Toast from "./Toast";

interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  roles: readonly string[] | null;
}

const GESTION = ["LOGISTICA", "ADMIN"] as const;

/** Menu por secciones; una seccion sin items visibles para el rol no se muestra. */
const NAV_GRUPOS: { id: string; titulo: string | null; items: NavItem[] }[] = [
  { id: "inicio", titulo: null, items: [{ to: "/inicio", label: "Inicio", icon: LayoutDashboard, roles: null }] },
  {
    id: "solicitudes",
    titulo: "Solicitudes",
    items: [
      { to: "/requerimientos", label: "Requerimientos", icon: ClipboardList, roles: null },
      { to: "/aprobaciones", label: "Aprobaciones", icon: CheckSquare, roles: ["APROBADOR", "GERENCIA", "ADMIN"] },
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
    items: [{ to: "/admin", label: "Administración", icon: ShieldCheck, roles: ["ADMIN"] }],
  },
];

// Los textos del menu siempre estan; en escritorio los oculta/revela el recorte del sidebar.
const ETIQUETA = "sidebar-texto whitespace-nowrap";

function iniciales(nombre: string): string {
  const partes = nombre.trim().split(/\s+/);
  return ((partes[0]?.[0] ?? "") + (partes[1]?.[0] ?? "")).toUpperCase();
}

export default function ProtectedLayout() {
  const { usuario, cargando, cerrarSesion, tieneAlgunRol } = useAuth();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const { pathname } = useLocation();

  if (cargando) {
    return <PantallaCarga texto="Verificando sesión" />;
  }
  if (!usuario) return <Navigate to="/login" replace />;
  // Tras una contrasena temporal no se opera nada hasta cambiarla (DEC-L024).
  if (usuario.debeCambiarPassword) return <CambiarPassword obligatorio />;

  const grupos = NAV_GRUPOS.map((g) => ({
    ...g,
    items: g.items.filter((item) => !item.roles || tieneAlgunRol(...item.roles)),
  })).filter((g) => g.items.length > 0);

  return (
    <div className="min-h-dvh bg-slate-100">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-slate-900 focus:px-3 focus:py-2 focus:text-sm focus:text-white"
      >
        Saltar al contenido
      </a>
      {menuAbierto && (
        <button
          type="button"
          aria-label="Cerrar menú"
          className="fixed inset-0 z-30 bg-slate-900/30 backdrop-blur-[2px] lg:hidden"
          onClick={() => setMenuAbierto(false)}
        />
      )}

      {/*
        Movil: cajon que entra desde la izquierda (boton de menu).
        Escritorio: riel de iconos de 64px que se despliega a 240px al pasar el
        mouse (o al navegar con Tab) por ENCIMA del contenido, sin empujarlo.
      */}
      <aside
        aria-label="Menú principal"
        className={`sidebar fixed inset-y-0 left-0 z-40 flex w-60 flex-col gap-7 overflow-hidden border-r border-slate-200 bg-slate-50 px-2.5 py-5 text-slate-700 transition-transform duration-300 ease-(--ease-suave) lg:translate-x-0 ${
          menuAbierto ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="flex items-start justify-between">
          <Link to="/inicio" className="flex min-w-0 items-center gap-3 px-1.5" onClick={() => setMenuAbierto(false)}>
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-brand-700 font-serif text-sm font-semibold text-white">
              T
            </span>
            <span className={`leading-tight ${ETIQUETA}`}>
              <span className="block text-sm font-semibold tracking-[-0.01em] text-slate-900">Logística</span>
              <span className="block text-[11px] text-slate-500">TDEM · ERP corporativo</span>
            </span>
          </Link>
          <button
            type="button"
            className="-mr-1 rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900 lg:hidden"
            onClick={() => setMenuAbierto(false)}
            aria-label="Cerrar menú"
          >
            <X className="size-5" />
          </button>
        </div>

        <nav className="flex flex-1 flex-col gap-4 overflow-y-auto" aria-label="Principal">
          {grupos.map((g) => (
            <div
              key={g.id}
              role={g.titulo ? "group" : undefined}
              aria-labelledby={g.titulo ? `nav-${g.id}` : undefined}
              className="flex flex-col gap-1"
            >
              {g.titulo && (
                <div className="relative flex h-5 items-center px-[11px]">
                  <span
                    id={`nav-${g.id}`}
                    className={`text-[10.5px] font-medium uppercase tracking-[0.08em] text-slate-400 ${ETIQUETA}`}
                  >
                    {g.titulo}
                  </span>
                  {/* Riel plegado: el titulo no se ve; una linea fina marca la seccion. */}
                  <span aria-hidden="true" className="sidebar-separador absolute left-[10px] top-1/2 hidden h-px w-5 bg-slate-300" />
                </div>
              )}
              {g.items.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  title={label}
                  onClick={() => setMenuAbierto(false)}
                  className={({ isActive }) =>
                    `flex h-9 items-center gap-3 rounded-md border px-[11px] text-[13.5px] transition-colors duration-150 ${
                      isActive
                        ? "border-slate-200 bg-white font-medium text-slate-900"
                        : "border-transparent text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                    }`
                  }
                >
                  <Icon className="size-4.5 shrink-0" strokeWidth={2.1} />
                  <span className={ETIQUETA}>{label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="flex flex-col gap-1 border-t border-slate-200 pt-4">
          <div className="flex h-10 items-center gap-3 px-1.5" title={usuario.nombre}>
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white text-[11px] font-semibold text-slate-700">
              {iniciales(usuario.nombre)}
            </span>
            <span className={`min-w-0 leading-tight ${ETIQUETA}`}>
              <span className="block truncate text-[13px] font-medium text-slate-900">{usuario.nombre}</span>
              <span className="block truncate font-mono text-[10.5px] uppercase tracking-[0.04em] text-slate-500">
                {usuario.roles.join(" · ") || "sin rol"}
              </span>
            </span>
          </div>
          <NavLink
            to="/cambiar-password"
            title="Cambiar contraseña"
            onClick={() => setMenuAbierto(false)}
            className="flex h-9 items-center gap-3 rounded-md px-[11px] text-[13px] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            <KeyRound className="size-4.5 shrink-0" strokeWidth={2.1} />
            <span className={ETIQUETA}>Cambiar contraseña</span>
          </NavLink>
          <button
            type="button"
            title="Cerrar sesión"
            className="flex h-9 items-center gap-3 rounded-md px-[11px] text-[13px] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
            onClick={() => cerrarSesion()}
          >
            <LogOut className="size-4.5 shrink-0" strokeWidth={2.1} />
            <span className={ETIQUETA}>Cerrar sesión</span>
          </button>
        </div>
      </aside>

      <div className="contenido-app flex min-h-dvh min-w-0 flex-col lg:pl-16">
        <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-slate-200 bg-slate-100/85 px-4 py-3 backdrop-blur-md lg:hidden">
          <button
            type="button"
            className="rounded-md p-1.5 text-slate-600 hover:bg-slate-100"
            onClick={() => setMenuAbierto(true)}
            aria-label="Abrir menú"
          >
            <Menu className="size-5" />
          </button>
          <Link to="/inicio" className="text-sm font-semibold text-slate-900">
            Logística <span className="font-normal text-slate-500">· TDEM</span>
          </Link>
        </header>

        {/* Todas las pantallas usan todo el ancho, con un margen lateral minimo. */}
        <main id="contenido" className="w-full flex-1 px-3 pb-16 pt-6 sm:px-4 lg:px-6 lg:pt-10">
          {/* key: cada cambio de pantalla repite la entrada suave. */}
          <div key={pathname} className="animate-pagina-in">
            <Outlet />
          </div>
        </main>
      </div>

      <Toast />
    </div>
  );
}
