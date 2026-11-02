import { useEffect, useState } from "react";
import { PackagePlus, X } from "lucide-react";
import { crearSolicitudProducto, listarSolicitudesProducto, obtenerClasificacion } from "../../api/maestro";
import type { Clasificacion, SolicitudProducto, TipoAtributo } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";
import { Skeleton } from "../ui/Skeleton";
import { EstadoSolicitud } from "./BandejaSolicitudes";
import { ETIQUETA_ATRIBUTO, SelectorFamilia, SelectorUnidad, unidadPorDefecto } from "./SelectoresMaestro";

/**
 * RF-004: "¿No encuentras el producto?" bajo el buscador del requerimiento.
 * Plegado por defecto (ley de Hick); la clasificacion se pide solo al abrirlo.
 */
export default function SolicitarProducto() {
  const [abierto, setAbierto] = useState(false);
  const [clasificacion, setClasificacion] = useState<Clasificacion | null>(null);
  const [mias, setMias] = useState<SolicitudProducto[]>([]);
  const [exito, setExito] = useState<string | null>(null);

  useEffect(() => {
    listarSolicitudesProducto().then((res) => res.ok && res.result && setMias(res.result));
  }, []);

  useEffect(() => {
    if (abierto && !clasificacion) {
      obtenerClasificacion().then((res) => res.ok && res.result && setClasificacion(res.result));
    }
  }, [abierto, clasificacion]);

  return (
    <div className="mt-4 border-t border-slate-100 pt-3">
      {!abierto ? (
        <button
          type="button"
          onClick={() => {
            setAbierto(true);
            setExito(null);
          }}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-brand-600 hover:text-brand-700"
        >
          <PackagePlus className="size-3.5" />
          ¿No encuentras el producto? Solicita su alta
        </button>
      ) : !clasificacion ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-9 w-full" />
        </div>
      ) : (
        <Formulario
          clasificacion={clasificacion}
          cerrar={() => setAbierto(false)}
          onEnviada={(s, mensaje) => {
            setMias((prev) => [s, ...prev]);
            setExito(mensaje);
            setAbierto(false);
          }}
        />
      )}

      {exito && <Alert tone="good" className="mt-3">{exito}</Alert>}

      {mias.length > 0 && (
        <details className="mt-3">
          <summary className="cursor-pointer list-none text-xs text-slate-500 hover:text-slate-800">
            Mis solicitudes de producto ({mias.length})
          </summary>
          <ul className="mt-2 flex flex-col gap-1.5">
            {mias.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-2 text-xs text-slate-600">
                <EstadoSolicitud estado={s.estado} />
                <span className="min-w-0 flex-1 truncate">{s.nombre}</span>
                {s.skuGenerado && <span className="font-mono text-slate-800">{s.skuGenerado}</span>}
                {s.comentarioRevision && <span className="text-slate-500">— {s.comentarioRevision}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function Formulario({
  clasificacion,
  cerrar,
  onEnviada,
}: {
  clasificacion: Clasificacion;
  cerrar: () => void;
  onEnviada: (s: SolicitudProducto, mensaje: string) => void;
}) {
  const [nombre, setNombre] = useState("");
  const [familyId, setFamilyId] = useState("");
  const [uomId, setUomId] = useState(unidadPorDefecto(clasificacion));
  const [atributoTipo, setAtributoTipo] = useState<TipoAtributo>("TALLA");
  const [atributoValor, setAtributoValor] = useState("");
  const [justificacion, setJustificacion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function onEnviar(e: React.FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const res = await crearSolicitudProducto({
        familyId,
        nombre,
        uomId,
        atributoTipo: atributoValor.trim() ? atributoTipo : undefined,
        atributoValor: atributoValor.trim() || undefined,
        justificacion,
      });
      if (res.ok && res.result) onEnviada(res.result, res.message);
      else setError(res.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={onEnviar} className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3 sm:grid-cols-2">
      <div className="flex items-center justify-between sm:col-span-2">
        <span className="text-sm font-medium text-slate-900">Solicitar alta de producto</span>
        <button type="button" onClick={cerrar} title="Cerrar" className="text-slate-400 hover:text-slate-700">
          <X className="size-4" />
        </button>
      </div>
      <Field label="Producto" required className="sm:col-span-2">
        <input
          required
          minLength={3}
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          placeholder="Nombre y características principales"
          className={`${inputClass} w-full`}
        />
      </Field>
      <Field label="Familia › Subfamilia" required>
        <SelectorFamilia clasificacion={clasificacion} value={familyId} onChange={setFamilyId} />
      </Field>
      <Field label="Unidad" required>
        <SelectorUnidad clasificacion={clasificacion} value={uomId} onChange={setUomId} />
      </Field>
      <Field label="Talla / color (opcional)">
        <div className="flex gap-2">
          <select
            value={atributoTipo}
            onChange={(e) => setAtributoTipo(e.target.value as TipoAtributo)}
            className={`${inputClass} w-28`}
            aria-label="Tipo de atributo"
          >
            {(Object.keys(ETIQUETA_ATRIBUTO) as TipoAtributo[]).map((t) => (
              <option key={t} value={t}>
                {ETIQUETA_ATRIBUTO[t]}
              </option>
            ))}
          </select>
          <input value={atributoValor} onChange={(e) => setAtributoValor(e.target.value)} className={`${inputClass} w-full`} />
        </div>
      </Field>
      <Field label="¿Para qué se necesita?" required>
        <input required value={justificacion} onChange={(e) => setJustificacion(e.target.value)} className={`${inputClass} w-full`} />
      </Field>
      {error && <Alert tone="danger" className="sm:col-span-2">{error}</Alert>}
      <div className="sm:col-span-2">
        <Button type="submit" size="sm" loading={enviando}>
          Enviar a Logística
        </Button>
      </div>
    </form>
  );
}
