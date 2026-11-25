import { useMemo, useRef, useState } from "react";
import { ChevronsUpDown, Plus } from "lucide-react";
import { crearPuntoEntrega, listarPuntosEntrega } from "../../api/compras";
import type { PuntoEntrega, TipoPlazo } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import { buscador } from "../busqueda";
import Button from "../ui/Button";
import CampoNumero from "../ui/CampoNumero";
import { inputClass } from "../ui/Field";
import Flotante from "../ui/Flotante";

const TIPOS_PLAZO: { id: TipoPlazo; label: string }[] = [
  { id: "HABILES", label: "días hábiles" },
  { id: "CALENDARIO", label: "días calendario" },
];

/** Plazo de entrega de la OC (DEC-L089): número de días y si son hábiles o calendario. */
export function PlazoEntregaCampo({
  dias,
  tipo,
  onDias,
  onTipo,
}: {
  dias: string;
  tipo: TipoPlazo;
  onDias: (v: string) => void;
  onTipo: (t: TipoPlazo) => void;
}) {
  return (
    <div className="flex gap-2">
      <CampoNumero
        decimales={0}
        maxEnteros={3}
        max={365}
        value={dias}
        onChange={onDias}
        placeholder="Días"
        aria-label="Días de plazo"
        className={`${inputClass} w-20`}
      />
      <select
        aria-label="Tipo de días"
        value={tipo}
        onChange={(e) => onTipo(e.target.value as TipoPlazo)}
        className={`${inputClass} min-w-0 flex-1`}
      >
        {TIPOS_PLAZO.map((t) => (
          <option key={t.id} value={t.id}>
            {t.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function etiqueta(p: PuntoEntrega): string {
  return `${p.nombre} — ${p.direccion}`;
}

/**
 * Lugar de entrega elegido de la lista de direcciones (DEC-L089): el almacén
 * central y las direcciones de los proyectos. Si falta una, se agrega aquí mismo
 * y queda en la lista para las siguientes órdenes.
 */
export function LugarEntregaCampo({ valor, onCambio }: { valor: string | null; onCambio: (id: string | null) => void }) {
  const puntos = useDatos("puntos-entrega", listarPuntosEntrega);
  const lista = useMemo(() => puntos.datos ?? [], [puntos.datos]);
  const caja = useRef<HTMLDivElement>(null);
  const [texto, setTexto] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [agregando, setAgregando] = useState(false);
  const [nombre, setNombre] = useState("");
  const [direccion, setDireccion] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const elegido = lista.find((p) => p.id === valor) ?? null;

  const opciones = useMemo(() => {
    const coincide = buscador(texto);
    return lista.filter((p) => coincide(`${p.nombre} ${p.direccion} ${p.cecoCode ?? ""} ${p.cecoNombre ?? ""}`));
  }, [lista, texto]);

  function elegir(id: string | null) {
    onCambio(id);
    setTexto("");
    setAbierto(false);
  }

  async function guardar() {
    if (!nombre.trim() || !direccion.trim()) {
      setError("Indica el nombre y la dirección.");
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const res = await crearPuntoEntrega({ nombre: nombre.trim(), direccion: direccion.trim(), cecoId: null });
      if (res.ok && res.result) {
        const nuevo = res.result;
        puntos.mutar((prev) => [...(prev ?? []), nuevo]);
        onCambio(nuevo.id);
        setAgregando(false);
        setNombre("");
        setDireccion("");
      } else {
        setError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  if (agregando) {
    return (
      <div className="flex flex-col gap-2 rounded-md border border-slate-200 bg-slate-50/60 p-3">
        <input
          autoFocus
          value={nombre}
          maxLength={120}
          onChange={(e) => setNombre(e.target.value)}
          placeholder="Nombre (ej. Proyecto ATC Piura)"
          className={`${inputClass} w-full`}
        />
        <input
          value={direccion}
          maxLength={200}
          onChange={(e) => setDireccion(e.target.value)}
          placeholder="Dirección"
          className={`${inputClass} w-full`}
        />
        {error && <p className="text-xs text-rose-700">{error}</p>}
        <div className="flex gap-2">
          <Button size="sm" loading={guardando} onClick={() => void guardar()}>
            Agregar a la lista
          </Button>
          <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setAgregando(false)}>
            Cancelar
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div ref={caja} className="relative">
      <input
        role="combobox"
        aria-expanded={abierto}
        aria-autocomplete="list"
        value={abierto ? texto : elegido ? etiqueta(elegido) : ""}
        placeholder="Elige la dirección de entrega"
        onFocus={() => setAbierto(true)}
        onBlur={() => setTimeout(() => setAbierto(false), 120)}
        onChange={(e) => {
          setTexto(e.target.value);
          setAbierto(true);
        }}
        onKeyDown={(e) => {
          if (e.key === "Escape") setAbierto(false);
        }}
        className={`${inputClass} w-full pr-9`}
        title={elegido ? etiqueta(elegido) : undefined}
      />
      <ChevronsUpDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      {abierto && (
        <Flotante ancla={caja} anchoAncla altoMaximo={288} className="rounded-md border border-slate-200 bg-white py-1 shadow-flotante">
          <ul role="listbox">
            {!texto.trim() && (
              <li
                role="option"
                aria-selected={!valor}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(null);
                }}
                className="cursor-pointer px-3 py-2 text-sm text-slate-500 hover:bg-slate-100"
              >
                Sin lugar de entrega
              </li>
            )}
            {opciones.map((p) => (
              <li
                key={p.id}
                role="option"
                aria-selected={p.id === valor}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(p.id);
                }}
                className="cursor-pointer px-3 py-2 text-sm hover:bg-slate-100"
              >
                <p className="truncate text-slate-800">{p.nombre}</p>
                <p className="truncate text-xs text-slate-500">{p.direccion}</p>
              </li>
            ))}
            <li
              onMouseDown={(e) => {
                e.preventDefault();
                setAbierto(false);
                setAgregando(true);
              }}
              className="flex cursor-pointer items-center gap-1.5 border-t border-slate-100 px-3 py-2 text-sm font-medium text-brand-700 hover:bg-slate-100"
            >
              <Plus className="size-3.5" />
              Agregar dirección
            </li>
          </ul>
        </Flotante>
      )}
    </div>
  );
}
