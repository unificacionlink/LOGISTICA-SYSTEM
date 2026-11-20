import { useDeferredValue, useMemo, useState } from "react";
import { Search, UserPlus, Users } from "lucide-react";
import type { AdminDatos, CecoAdmin, UsuarioAdmin } from "../../api/types";
import { EMPRESAS } from "../empresas";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import { inputClass } from "../ui/Field";
import NuevoUsuario from "./NuevoUsuario";
import PanelUsuario, { EstadoAcceso, type Aplicar } from "./PanelUsuario";
import { ETIQUETA_ROL } from "./roles";

type Grupo = "todos" | "SOLICITANTE" | "APROBADOR" | "GERENCIA" | "PYC" | "LOGISTICA" | "ADMIN" | "sin-rol";
type Estado = "" | "activo" | "por-activar" | "sin-acceso" | "inactivo";

const GRUPOS: { id: Grupo; label: string }[] = [
  { id: "todos", label: "Todos" },
  { id: "SOLICITANTE", label: "Solicitantes" },
  { id: "APROBADOR", label: "Aprobadores" },
  { id: "GERENCIA", label: "Gerencia" },
  { id: "PYC", label: "PYC / Presupuestos" },
  { id: "LOGISTICA", label: "Logística" },
  { id: "ADMIN", label: "Administradores" },
  { id: "sin-rol", label: "Sin rol" },
];

const MARCA = new Map(EMPRESAS.map((e) => [e.code, e]));
const thClass = "px-5 py-3";

function estadoDe(u: UsuarioAdmin): Estado {
  if (!u.activo) return "inactivo";
  if (!u.tieneAcceso) return "sin-acceso";
  if (u.debeCambiarPassword) return "por-activar";
  return "activo";
}

/**
 * Rol principal: cada usuario aparece en una sola pestaña, la de su rol de
 * mayor alcance (quien tiene todos los roles no se repite en cada una). Sus
 * otros roles se muestran debajo de su correo.
 */
const JERARQUIA = ["ADMIN", "LOGISTICA", "GERENCIA", "PYC", "APROBADOR", "SOLICITANTE"] as const;
const TOTAL_ROLES = JERARQUIA.length;

function rolPrincipal(u: UsuarioAdmin): string | null {
  return JERARQUIA.find((r) => u.roles.includes(r)) ?? null;
}

function otrosRoles(u: UsuarioAdmin): string | null {
  const principal = rolPrincipal(u);
  const otros = u.roles.filter((r) => r !== principal);
  if (otros.length === 0) return null;
  if (u.roles.length >= TOTAL_ROLES) return "+ todos los roles";
  return "+ " + otros.map((r) => ETIQUETA_ROL[r] ?? r).join(" · ");
}

function enGrupo(u: UsuarioAdmin, g: Grupo): boolean {
  if (g === "todos") return true;
  if (g === "sin-rol") return u.roles.length === 0;
  return rolPrincipal(u) === g;
}

/** Codigos de CECO como chips cortos: los primeros y "+n". */
function ListaCecos({ ids, porId, vacio }: { ids: string[]; porId: Map<string, CecoAdmin>; vacio: React.ReactNode }) {
  if (ids.length === 0) return <>{vacio}</>;
  const visibles = ids.slice(0, 3);
  const titulo = ids.map((id) => {
    const c = porId.get(id);
    return c ? `${c.code} · ${c.nombre}` : "—";
  });
  return (
    <span className="flex flex-wrap items-center gap-1" title={titulo.join("\n")}>
      {visibles.map((id) => (
        <span key={id} className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 font-mono text-[0.6875rem] text-slate-700">
          {porId.get(id)?.code ?? "—"}
        </span>
      ))}
      {ids.length > 3 && <span className="text-[0.6875rem] text-slate-400">+{ids.length - 3}</span>}
    </span>
  );
}

/**
 * Usuarios por rol (Administracion): una pestaña por rol principal (cada
 * usuario en una sola) con su conteo y
 * columnas segun lo que importa de ese rol (CECOs para pedir, CECOs que
 * aprueba); la ficha completa se edita en un panel lateral.
 */
export default function PestanaUsuarios({ datos, aplicar }: { datos: AdminDatos; aplicar: Aplicar }) {
  const [grupo, setGrupo] = useState<Grupo>("todos");
  const [busqueda, setBusqueda] = useState("");
  // Filtrar no traba el teclado: la lista se actualiza apenas haya un respiro.
  const busquedaDiferido = useDeferredValue(busqueda);
  const [empresa, setEmpresa] = useState("");
  const [estado, setEstado] = useState<Estado>("");
  const [abierto, setAbierto] = useState<string | null>(null);
  const [creando, setCreando] = useState(false);
  const hayAltaManual = datos.empresas.some((e) => e.altaManual);

  const porId = useMemo(() => new Map(datos.cecos.map((c) => [c.id, c])), [datos.cecos]);
  const aprobados = useMemo(() => {
    const m = new Map<string, string[]>();
    for (const c of datos.cecos) {
      if (c.aprobadorId) m.set(c.aprobadorId, [...(m.get(c.aprobadorId) ?? []), c.id]);
    }
    return m;
  }, [datos.cecos]);

  // Empresa, busqueda y estado; el grupo se aplica aparte para contar cada pestaña.
  const base = useMemo(() => {
    const q = busquedaDiferido.trim().toLowerCase();
    return datos.usuarios
      .filter(
        (u) =>
          (!empresa || u.empresaCode === empresa || u.cecoIds.some((id) => porId.get(id)?.empresaCode === empresa)) &&
          (!estado || estadoDe(u) === estado) &&
          (!q || u.nombre.toLowerCase().includes(q) || u.correo.toLowerCase().includes(q) || (u.dni ?? "").includes(q)),
      )
      // Activos primero, luego por nombre.
      .toSorted((a, b) => Number(!a.activo) - Number(!b.activo) || a.nombre.localeCompare(b.nombre));
  }, [datos.usuarios, porId, busquedaDiferido, empresa, estado]);

  const conteo = useMemo(() => new Map(GRUPOS.map((g) => [g.id, base.filter((u) => enGrupo(u, g.id)).length])), [base]);
  const filtrados = base.filter((u) => enGrupo(u, grupo));
  const usuarioAbierto = datos.usuarios.find((u) => u.id === abierto) ?? null;
  // "Sin rol" solo aparece si hay alguno: es algo por corregir.
  const grupos = GRUPOS.filter((g) => g.id !== "sin-rol" || (conteo.get("sin-rol") ?? 0) > 0 || grupo === "sin-rol");

  const columnaExtra =
    grupo === "SOLICITANTE"
      ? "Pide para"
      : grupo === "APROBADOR" || grupo === "GERENCIA"
        ? "Aprueba"
        : grupo === "PYC"
          ? "Presupuestos"
          : "Roles";

  function celdaExtra(u: UsuarioAdmin) {
    // DEC-L077: PYC trabaja sobre todos los CECOs, no necesita asignarlos.
    if (grupo === "PYC") {
      return <span className="text-xs text-slate-600">Todos los CECOs</span>;
    }
    if (grupo === "SOLICITANTE") {
      return (
        <ListaCecos
          ids={u.cecoIds}
          porId={porId}
          vacio={<span className="text-xs text-amber-700">Sin CECO</span>}
        />
      );
    }
    if (grupo === "APROBADOR" || grupo === "GERENCIA") {
      return (
        <ListaCecos
          ids={aprobados.get(u.id) ?? []}
          porId={porId}
          vacio={<span className="text-xs text-slate-400">Ninguno asignado</span>}
        />
      );
    }
    if (u.roles.length >= TOTAL_ROLES) {
      return <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[0.6875rem] text-slate-700">Todos los roles</span>;
    }
    return u.roles.length ? (
      <span className="flex flex-wrap gap-1">
        {u.roles.map((r) => (
          <span key={r} className="rounded-full bg-slate-100 px-2 py-0.5 text-[0.6875rem] text-slate-700">
            {ETIQUETA_ROL[r] ?? r}
          </span>
        ))}
      </span>
    ) : (
      <span className="text-xs text-amber-700">Sin rol</span>
    );
  }

  return (
    <>
      <div role="tablist" aria-label="Rol" className="mb-3 flex gap-1 overflow-x-auto border-b border-slate-200">
        {grupos.map((g) => (
          <button
            key={g.id}
            type="button"
            role="tab"
            aria-selected={grupo === g.id}
            onClick={() => setGrupo(g.id)}
            className={`-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              grupo === g.id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {g.label}
            <span
              className={`rounded-md px-1.5 font-mono text-xs tabular-nums ${
                grupo === g.id
                  ? "bg-brand-50 text-brand-700"
                  : g.id === "sin-rol"
                    ? "bg-amber-50 text-amber-700"
                    : "text-slate-400"
              }`}
            >
              {conteo.get(g.id) ?? 0}
            </span>
          </button>
        ))}
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <label className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por nombre, correo o DNI"
            className={`${inputClass} w-full pl-9`}
          />
        </label>
        <select value={empresa} onChange={(e) => setEmpresa(e.target.value)} className={inputClass} aria-label="Empresa">
          <option value="">Todas las empresas</option>
          {datos.empresas.map((e) => (
            <option key={e.id} value={e.code}>
              {MARCA.get(e.code)?.label ?? e.code}
            </option>
          ))}
        </select>
        <select
          value={estado}
          onChange={(e) => setEstado(e.target.value as Estado)}
          className={inputClass}
          aria-label="Estado de acceso"
        >
          <option value="">Todos los estados</option>
          <option value="activo">Activos</option>
          <option value="por-activar">Por activar</option>
          <option value="sin-acceso">Sin acceso</option>
          <option value="inactivo">Inactivos</option>
        </select>
        {hayAltaManual && !creando && (
          <Button variant="secondary" icon={<UserPlus className="size-4" />} onClick={() => setCreando(true)}>
            Alta manual
          </Button>
        )}
      </div>

      {creando && <NuevoUsuario datos={datos} aplicar={aplicar} cerrar={() => setCreando(false)} />}

      <Card className="overflow-hidden">
        {filtrados.length === 0 ? (
          <EmptyState icon={Users} title={datos.usuarios.length === 0 ? "Aún no hay usuarios" : "Sin resultados"} />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 cabecera-tabla">
                <th className={thClass}>Usuario</th>
                <th className={`${thClass} col-sec`}>Empresa</th>
                <th className={thClass}>{columnaExtra}</th>
                <th className={`${thClass} text-right`}>Acceso</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtrados.map((u) => {
                const marca = MARCA.get(u.empresaCode ?? "");
                return (
                  <tr
                    key={u.id}
                    tabIndex={0}
                    onClick={() => setAbierto(u.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        setAbierto(u.id);
                      }
                    }}
                    className={`cursor-pointer focus-visible:bg-slate-50 focus-visible:outline-none ${
                      abierto === u.id ? "bg-brand-50/50" : "hover:bg-slate-50"
                    } ${u.activo ? "" : "opacity-60"}`}
                  >
                    <td className="col-flex px-5 py-3" title={u.nombre}>
                      <p className="truncate text-sm text-slate-800">{u.nombre}</p>
                      <p className="truncate text-xs text-slate-500">{u.correo}</p>
                      {grupo !== "todos" && otrosRoles(u) && (
                        <p className="truncate text-[0.6875rem] text-slate-400">{otrosRoles(u)}</p>
                      )}
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-600">
                      <span className="flex items-center gap-1.5">
                        <span className={`size-1.5 rounded-full ${marca?.marca ?? "bg-slate-300"}`} aria-hidden="true" />
                        {marca?.label ?? u.empresaCode ?? "—"}
                      </span>
                    </td>
                    <td className="px-5 py-3">{celdaExtra(u)}</td>
                    <td className="px-5 py-3 text-right">
                      <EstadoAcceso u={u} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      {usuarioAbierto && (
        <PanelUsuario
          key={usuarioAbierto.id}
          u={usuarioAbierto}
          roles={datos.roles}
          cecos={datos.cecos}
          usuarios={datos.usuarios}
          aplicar={aplicar}
          onCerrar={() => setAbierto(null)}
        />
      )}
    </>
  );
}
