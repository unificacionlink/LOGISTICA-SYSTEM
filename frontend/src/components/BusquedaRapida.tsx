import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Contact,
  CornerDownLeft,
  FilePlus2,
  FileQuestion,
  FileSpreadsheet,
  FileText,
  KeyRound,
  ListTodo,
  PackageCheck,
  Search,
  SlidersHorizontal,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { EVENTO_BUSQUEDA, GESTION, NAV_GRUPOS } from "./navegacion";

interface Opcion {
  id: string;
  label: string;
  grupo: string;
  to: string;
  icon: LucideIcon;
  /** Palabras extra para encontrarla (sin tildes). */
  claves?: string;
}

/** Atajos a pestanas y acciones frecuentes, ademas de las pantallas del menu. */
const ATAJOS: (Omit<Opcion, "id"> & { roles: readonly string[] | null })[] = [
  { label: "Nuevo requerimiento", grupo: "Acciones", to: "/requerimientos/nuevo", icon: FilePlus2, roles: null, claves: "crear pedir solicitar" },
  { label: "Compras · Necesidades", grupo: "Ir a", to: "/compras?tab=necesidades", icon: ListTodo, roles: GESTION, claves: "ruta faltante" },
  { label: "Compras · Solicitudes de cotización", grupo: "Ir a", to: "/compras?tab=solicitudes", icon: FileQuestion, roles: GESTION, claves: "rfq cotizar" },
  { label: "Compras · Caja chica", grupo: "Ir a", to: "/compras?tab=caja", icon: Wallet, roles: GESTION },
  { label: "Órdenes de compra", grupo: "Ir a", to: "/compras/ordenes", icon: FileText, roles: GESTION, claves: "oc" },
  { label: "Almacén · Por despachar", grupo: "Ir a", to: "/almacen?tab=despachar", icon: PackageCheck, roles: GESTION, claves: "nota inventario" },
  { label: "Almacén · Carga masiva", grupo: "Ir a", to: "/almacen/carga-masiva", icon: FileSpreadsheet, roles: GESTION, claves: "excel" },
  { label: "Inventario por Excel", grupo: "Ir a", to: "/stock/carga", icon: FileSpreadsheet, roles: GESTION, claves: "stock conteo carga masiva" },
  { label: "Ajuste manual de stock", grupo: "Ir a", to: "/stock/ajustes", icon: SlidersHorizontal, roles: GESTION, claves: "ingreso salida merma" },
  { label: "Administración · Personal", grupo: "Ir a", to: "/admin?tab=personal", icon: Contact, roles: ["ADMIN"], claves: "dar acceso trabajador" },
  { label: "Cambiar contraseña", grupo: "Cuenta", to: "/cambiar-password", icon: KeyRound, roles: null, claves: "clave" },
];

function normalizar(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/**
 * Busqueda rapida (Ctrl+K / Cmd+K), al estilo de Linear: un solo campo para
 * ir a cualquier pantalla o accion sin recorrer el menu. Solo ofrece lo que el
 * rol del usuario puede abrir.
 */
export default function BusquedaRapida() {
  const { tieneAlgunRol } = useAuth();
  const navigate = useNavigate();
  const [abierto, setAbierto] = useState(false);
  const [texto, setTexto] = useState("");
  const [activo, setActivo] = useState(0);
  const listaRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const abrir = () => {
      setTexto("");
      setActivo(0);
      setAbierto(true);
    };
    const teclado = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        abrir();
      }
    };
    window.addEventListener(EVENTO_BUSQUEDA, abrir);
    window.addEventListener("keydown", teclado);
    return () => {
      window.removeEventListener(EVENTO_BUSQUEDA, abrir);
      window.removeEventListener("keydown", teclado);
    };
  }, []);

  const opciones = useMemo<Opcion[]>(() => {
    const pantallas = NAV_GRUPOS.flatMap((g) =>
      g.items
        .filter((i) => !i.roles || tieneAlgunRol(...i.roles))
        .map((i) => ({ id: i.to, label: i.label, grupo: "Pantallas", to: i.to, icon: i.icon, claves: g.titulo ?? "" })),
    );
    const atajos = ATAJOS.filter((a) => !a.roles || tieneAlgunRol(...a.roles)).map((a) => ({ ...a, id: a.to }));
    return [...pantallas, ...atajos];
  }, [tieneAlgunRol]);

  const resultados = useMemo(() => {
    const q = normalizar(texto.trim());
    if (!q) return opciones;
    const palabras = q.split(/\s+/);
    return opciones.filter((o) => {
      const donde = normalizar(`${o.label} ${o.grupo} ${o.claves ?? ""}`);
      return palabras.every((p) => donde.includes(p));
    });
  }, [opciones, texto]);

  useEffect(() => {
    listaRef.current?.querySelector(`[data-indice="${activo}"]`)?.scrollIntoView({ block: "nearest" });
  }, [activo]);

  if (!abierto) return null;

  function ir(o: Opcion | undefined) {
    if (!o) return;
    setAbierto(false);
    navigate(o.to);
  }

  function onTecla(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActivo((i) => Math.min(i + 1, resultados.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActivo((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      ir(resultados[activo]);
    } else if (e.key === "Escape") {
      setAbierto(false);
    }
  }

  let grupoAnterior = "";
  return (
    <div className="animate-capa-in fixed inset-0 z-50 flex items-start justify-center bg-slate-900/25 px-4 pt-[12vh] backdrop-blur-[2px]">
      <button type="button" aria-label="Cerrar búsqueda" className="absolute inset-0 cursor-default" onClick={() => setAbierto(false)} />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Búsqueda rápida"
        className="relative w-full max-w-lg overflow-hidden rounded-xl border border-slate-200 bg-white shadow-flotante"
      >
        <div className="flex items-center gap-2.5 border-b border-slate-200 px-4">
          <Search className="size-4 shrink-0 text-slate-400" />
          <input
            autoFocus
            role="combobox"
            aria-expanded="true"
            aria-controls="busqueda-resultados"
            aria-activedescendant={resultados[activo] ? `busqueda-${activo}` : undefined}
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value);
              setActivo(0);
            }}
            onKeyDown={onTecla}
            placeholder="Ir a una pantalla o acción…"
            className="h-12 w-full bg-transparent text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none"
          />
          <kbd className="shrink-0 rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 font-mono text-[10px] text-slate-500">
            Esc
          </kbd>
        </div>
        <ul ref={listaRef} id="busqueda-resultados" role="listbox" className="max-h-[50vh] overflow-y-auto p-1.5">
          {resultados.length === 0 && <li className="px-3 py-6 text-center text-sm text-slate-500">Sin resultados</li>}
          {resultados.map((o, i) => {
            const titulo = o.grupo !== grupoAnterior ? o.grupo : null;
            grupoAnterior = o.grupo;
            const Icono = o.icon;
            return (
              <li key={`${o.grupo}-${o.id}`} role="presentation">
                {titulo && <div className="px-2.5 pb-1 pt-2.5 text-xs font-medium text-slate-400">{titulo}</div>}
                <div
                  id={`busqueda-${i}`}
                  role="option"
                  aria-selected={i === activo}
                  data-indice={i}
                  onMouseMove={() => setActivo(i)}
                  onClick={() => ir(o)}
                  className={`flex cursor-pointer items-center gap-3 rounded-md px-2.5 py-2 text-sm ${
                    i === activo ? "bg-brand-50 text-brand-800" : "text-slate-700"
                  }`}
                >
                  <Icono className={`size-4 shrink-0 ${i === activo ? "text-brand-600" : "text-slate-400"}`} />
                  <span className="flex-1 truncate">{o.label}</span>
                  {i === activo && <CornerDownLeft className="size-3.5 text-brand-500" />}
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
