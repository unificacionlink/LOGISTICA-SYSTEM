import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import Dashboard from "../components/Dashboard";

const MODULOS = [
  {
    to: "/requerimientos",
    titulo: "Requerimientos",
    descripcion: "Crea solicitudes de productos y sigue su atención hasta la entrega.",
    roles: null,
  },
  {
    to: "/aprobaciones",
    titulo: "Aprobaciones",
    descripcion: "Revisa los requerimientos enviados y decide sobre cada uno.",
    roles: ["APROBADOR", "ADMIN"],
  },
  {
    to: "/compras",
    titulo: "Compras",
    descripcion: "Pendientes de compra, RFQ en curso, cotizaciones y órdenes.",
    roles: ["LOGISTICA", "ADMIN"],
  },
  {
    to: "/almacen",
    titulo: "Almacén",
    descripcion: "Recepción de órdenes, despachos, notas de inventario y envíos.",
    roles: ["LOGISTICA", "ADMIN"],
  },
  {
    to: "/stock",
    titulo: "Stock",
    descripcion: "Saldos por SKU: físico, reservado y disponible.",
    roles: ["LOGISTICA", "ADMIN"],
  },
  {
    to: "/proveedores",
    titulo: "Proveedores",
    descripcion: "Maestro de proveedores y proveedor habitual por producto.",
    roles: ["LOGISTICA", "ADMIN"],
  },
  {
    to: "/catalogo",
    titulo: "Catálogo de productos",
    descripcion: "Fichas de producto, variantes y fotos.",
    roles: ["LOGISTICA", "ADMIN"],
  },
] as const;

function saludo(): string {
  const h = new Date().getHours();
  if (h < 12) return "Buenos días";
  if (h < 19) return "Buenas tardes";
  return "Buenas noches";
}

export default function Inicio() {
  const { usuario, tieneAlgunRol } = useAuth();
  const modulos = MODULOS.filter((m) => !m.roles || tieneAlgunRol(...m.roles));
  const nombre = usuario?.nombre?.split(" ")[0];
  // El dashboard es para quien gestiona o aprueba; el solicitante ve sus accesos.
  const conDashboard = tieneAlgunRol("LOGISTICA", "ADMIN", "APROBADOR");

  return (
    <div>
      <header className={conDashboard ? "mb-6" : "mb-12"}>
        <p className="rotulo">
          {new Date().toLocaleDateString("es-PE", { weekday: "long", day: "numeric", month: "long" })}
        </p>
        <h1 className="mt-3 font-serif text-[36px] font-medium leading-[1.05] tracking-[-0.03em] text-slate-950 sm:text-[44px]">
          {saludo()}
          {nombre ? `, ${nombre}` : ""}.
        </h1>
      </header>

      {conDashboard ? (
        <Dashboard />
      ) : (
        <nav aria-label="Módulos">
          <ol className="border-t border-slate-200">
            {modulos.map((m, i) => (
              <li
                key={m.to}
                className="animate-pagina-in border-b border-slate-200"
                style={{ animationDelay: `${i * 60}ms` }}
              >
                <Link
                  to={m.to}
                  className="group grid grid-cols-[2.5rem_1fr_auto] items-baseline gap-x-4 py-5 transition-colors sm:grid-cols-[3rem_14rem_1fr_auto]"
                >
                  <span className="font-mono text-xs text-slate-400">{String(i + 1).padStart(2, "0")}</span>
                  <span className="text-[17px] font-medium tracking-[-0.01em] text-slate-900 transition-transform duration-300 ease-(--ease-suave) group-hover:translate-x-1">
                    {m.titulo}
                  </span>
                  <span className="col-start-2 mt-1 text-sm text-slate-500 sm:col-start-auto sm:mt-0">{m.descripcion}</span>
                  <ArrowUpRight
                    className="row-start-1 size-4 self-center text-slate-300 transition-[color,transform] duration-300 ease-(--ease-suave) group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-slate-900 sm:row-start-auto"
                    style={{ gridColumnStart: -2 }}
                  />
                </Link>
              </li>
            ))}
          </ol>
        </nav>
      )}
    </div>
  );
}
