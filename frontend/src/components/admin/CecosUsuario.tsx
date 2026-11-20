import { useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { asignarCecos } from "../../api/admin";
import type { AdminDatos, CecoAdmin, UsuarioAdmin } from "../../api/types";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import { EMPRESAS } from "../empresas";
import Flotante from "../ui/Flotante";

const MAX_RESULTADOS = 8;

function etiquetaEmpresa(code: string) {
  return EMPRESAS.find((e) => e.code === code)?.label ?? code;
}

/**
 * CECOs para los que el usuario puede pedir, de cualquier empresa (DEC-L029).
 * Aplica tambien a los de RR.HH.: su CECO de planilla llega con el sync y aqui se agregan otros.
 */
export default function CecosUsuario({
  u,
  cecos,
  aplicar,
}: {
  u: UsuarioAdmin;
  cecos: CecoAdmin[];
  aplicar: (res: { ok: boolean; result: AdminDatos | null; message: string }) => boolean;
}) {
  const [seleccion, setSeleccion] = useState<string[]>(u.cecoIds);
  const caja = useRef<HTMLDivElement>(null);
  const [busqueda, setBusqueda] = useState("");
  const [guardando, setGuardando] = useState(false);
  const porId = useMemo(() => new Map(cecos.map((c) => [c.id, c])), [cecos]);
  // Gerencia usa estos CECOs como su ambito: decide sus requerimientos
  // (DEC-L030) y sus presupuestos (DEC-L036). PYC no los necesita: trabaja
  // sobre todos los CECOs de las 3 empresas (DEC-L077). El rotulo dice para
  // que le sirven segun sus roles.
  const gestiona = u.roles.includes("GERENCIA");
  const esPyc = u.roles.includes("PYC");
  const usos = [
    u.roles.includes("SOLICITANTE") && "pide",
    gestiona && "aprueba como Gerencia",
    gestiona && "presupuestos",
  ].filter(Boolean);
  const cambiaron = seleccion.toSorted().join() !== u.cecoIds.toSorted().join();

  const resultados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    if (!q) return [];
    return cecos
      .filter(
        (c) =>
          c.activo &&
          !seleccion.includes(c.id) &&
          `${c.code} ${c.nombre} ${c.empresaCode} ${etiquetaEmpresa(c.empresaCode)}`.toLowerCase().includes(q),
      )
      .slice(0, MAX_RESULTADOS);
  }, [busqueda, cecos, seleccion]);

  async function onGuardar() {
    setGuardando(true);
    try {
      aplicar(await asignarCecos(u.id, seleccion));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="mt-4">
      <div className="mb-2 flex flex-wrap items-baseline gap-x-2">
        <span className="rotulo">{gestiona ? "CECOs de su ámbito" : "CECOs para los que puede pedir"}</span>
        {gestiona && usos.length > 0 && <span className="text-xs text-slate-500">{usos.join(" · ")}</span>}
      </div>
      {esPyc && <p className="mb-2 text-xs text-slate-500">Como PYC ve los presupuestos de todos los CECOs.</p>}
      {seleccion.length === 0 ? (
        !esPyc || u.roles.includes("SOLICITANTE") || gestiona ? (
          <p className={`text-xs ${u.roles.includes("SOLICITANTE") ? "text-amber-700" : "text-slate-500"}`}>
            {u.roles.includes("SOLICITANTE")
              ? "Sin CECOs: no puede crear requerimientos."
              : gestiona
                ? "Sin CECOs: como Gerencia no aprueba por ámbito."
                : "Sin CECOs asignados."}
          </p>
        ) : null
      ) : (
        <ul className="flex flex-wrap gap-1.5">
          {seleccion.map((id) => {
            const c = porId.get(id);
            return (
              <li
                key={id}
                className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-slate-50 py-0.5 pl-2 pr-1 text-xs text-slate-700"
              >
                <span className="font-mono text-[0.625rem] text-slate-500">{c ? etiquetaEmpresa(c.empresaCode) : "—"}</span>
                <span>
                  {c ? `${c.code} · ${c.nombre}` : "CECO eliminado"}
                  {c && !c.activo && <span className="text-slate-400"> (inactivo)</span>}
                </span>
                <button
                  type="button"
                  aria-label={`Quitar ${c?.code ?? "CECO"}`}
                  onClick={() => setSeleccion((prev) => prev.filter((x) => x !== id))}
                  className="rounded p-0.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700"
                >
                  <X className="size-3" />
                </button>
              </li>
            );
          })}
        </ul>
      )}

      <div ref={caja} className="relative mt-2 max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
        <input
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Escape") setBusqueda("");
          }}
          placeholder="Agregar CECO (código, nombre o empresa)"
          aria-label="Agregar CECO"
          className={`${inputClass} w-full pl-9 text-sm`}
        />
        {busqueda.trim() && (
          <Flotante ancla={caja} anchoAncla altoMaximo={288} className="rounded-md border border-slate-200 bg-white shadow-lg">
            <ul>
              {resultados.length === 0 ? (
                <li className="px-3 py-2 text-xs text-slate-500">Sin resultados</li>
              ) : (
                resultados.map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => {
                        setSeleccion((prev) => [...prev, c.id]);
                        setBusqueda("");
                      }}
                      className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                    >
                      <span className="w-14 shrink-0 font-mono text-[0.625rem] text-slate-500">
                        {etiquetaEmpresa(c.empresaCode)}
                      </span>
                      <span className="truncate">
                        {c.code} · {c.nombre}
                      </span>
                    </button>
                  </li>
                ))
              )}
            </ul>
          </Flotante>
        )}
      </div>

      {cambiaron && (
        <div className="mt-3 flex gap-2">
          <Button size="sm" loading={guardando} onClick={onGuardar}>
            Guardar CECOs
          </Button>
          <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setSeleccion(u.cecoIds)}>
            Descartar
          </Button>
        </div>
      )}
    </div>
  );
}
