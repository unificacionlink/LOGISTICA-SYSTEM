import { useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { asignarCecosAprobados } from "../../api/admin";
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
 * CECOs que aprueba el usuario (Aprobador o Gerencia; DEC-L037, como Accesos /
 * usuarios de la V38.7). Un CECO tiene un solo aprobador: elegir uno que ya
 * tenia otro se lo reasigna a este usuario.
 */
export default function CecosAprobados({
  u,
  cecos,
  usuarios,
  aplicar,
}: {
  u: UsuarioAdmin;
  cecos: CecoAdmin[];
  usuarios: UsuarioAdmin[];
  aplicar: (res: { ok: boolean; result: AdminDatos | null; message: string }) => boolean;
}) {
  const actuales = useMemo(() => cecos.filter((c) => c.aprobadorId === u.id).map((c) => c.id), [cecos, u.id]);
  const [seleccion, setSeleccion] = useState<string[]>(actuales);
  const caja = useRef<HTMLDivElement>(null);
  const [busqueda, setBusqueda] = useState("");
  const [guardando, setGuardando] = useState(false);
  const porId = useMemo(() => new Map(cecos.map((c) => [c.id, c])), [cecos]);
  const nombre = useMemo(() => new Map(usuarios.map((x) => [x.id, x.nombre])), [usuarios]);
  const cambiaron = [...seleccion].sort().join() !== [...actuales].sort().join();

  /** Aprobador actual del CECO si es otro usuario (se le quitara al guardar). */
  const otroAprobador = (c: CecoAdmin) => (c.aprobadorId && c.aprobadorId !== u.id ? nombre.get(c.aprobadorId) : null);

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
      aplicar(await asignarCecosAprobados(u.id, seleccion));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="mt-4">
      <div className="rotulo mb-2">CECOs que aprueba</div>
      {seleccion.length === 0 ? (
        <p className="text-xs text-slate-500">No es aprobador asignado de ningún CECO.</p>
      ) : (
        <ul className="flex flex-wrap gap-1.5">
          {seleccion.map((id) => {
            const c = porId.get(id);
            const otro = c && otroAprobador(c);
            return (
              <li
                key={id}
                className={`inline-flex items-center gap-1.5 rounded-md border py-0.5 pl-2 pr-1 text-xs text-slate-700 ${
                  otro ? "border-amber-200 bg-amber-50" : "border-slate-200 bg-slate-50"
                }`}
              >
                <span className="font-mono text-[10px] text-slate-500">{c ? etiquetaEmpresa(c.empresaCode) : "—"}</span>
                <span>
                  {c ? `${c.code} · ${c.nombre}` : "CECO eliminado"}
                  {otro && <span className="text-amber-700"> (se le quita a {otro})</span>}
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
          placeholder="Agregar CECO que aprueba"
          aria-label="Agregar CECO que aprueba"
          className={`${inputClass} w-full pl-9 text-sm`}
        />
        {busqueda.trim() && (
          <Flotante ancla={caja} anchoAncla altoMaximo={288} className="rounded-md border border-slate-200 bg-white shadow-lg">
            <ul>
              {resultados.length === 0 ? (
                <li className="px-3 py-2 text-xs text-slate-500">Sin resultados</li>
              ) : (
                resultados.map((c) => {
                  const otro = otroAprobador(c);
                  return (
                    <li key={c.id}>
                      <button
                        type="button"
                        onClick={() => {
                          setSeleccion((prev) => [...prev, c.id]);
                          setBusqueda("");
                        }}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                      >
                        <span className="w-14 shrink-0 font-mono text-[10px] text-slate-500">
                          {etiquetaEmpresa(c.empresaCode)}
                        </span>
                        <span className="min-w-0 flex-1 truncate">
                          {c.code} · {c.nombre}
                        </span>
                        {otro && <span className="shrink-0 text-[11px] text-amber-700">Hoy: {otro}</span>}
                      </button>
                    </li>
                  );
                })
              )}
            </ul>
          </Flotante>
        )}
      </div>

      {cambiaron && (
        <div className="mt-3 flex gap-2">
          <Button size="sm" loading={guardando} onClick={onGuardar}>
            Guardar CECOs que aprueba
          </Button>
          <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setSeleccion(actuales)}>
            Descartar
          </Button>
        </div>
      )}
    </div>
  );
}
