import { useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronsUpDown, ClipboardCheck, PenLine } from "lucide-react";
import { guardarVerificacionOc, obtenerVerificacionOc } from "../../api/compras";
import { descargarPdfVerificacion } from "../../api/documentos";
import type { ItemVerificacion, MarcaVerificacion, PersonaVerificacion, VerificacionOc } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import BotonDescarga from "../BotonDescarga";
import { buscador } from "../busqueda";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody, CardHeader } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import Flotante from "../ui/Flotante";
import { Skeleton } from "../ui/Skeleton";

interface Fila {
  orderItemId: string;
  funcionalidad: MarcaVerificacion;
  estandares: MarcaVerificacion;
  especificaciones: MarcaVerificacion;
  observacion: string;
}

const COLUMNAS: { clave: "funcionalidad" | "estandares" | "especificaciones"; titulo: string; ayuda: string; opcional: boolean }[] = [
  { clave: "funcionalidad", titulo: "Funcionalidad", ayuda: "¿Aprueba funcionalidad por testeo y/o muestreo? Solo equipos electrónicos.", opcional: true },
  { clave: "estandares", titulo: "Estándares", ayuda: "¿Cumple estándares y/o normas de la lista de productos críticos F-LOG-04? Solo productos críticos.", opcional: true },
  { clave: "especificaciones", titulo: "Especificaciones", ayuda: "¿Cumple con las especificaciones de la OC y está en condiciones óptimas?", opcional: false },
];

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—";
}

function filasDe(v: VerificacionOc): Fila[] {
  return v.items.map((i) => ({
    orderItemId: i.orderItemId,
    funcionalidad: i.funcionalidad,
    estandares: i.estandares,
    // Lo nuevo empieza conforme: es lo habitual y se cambia lo que no.
    especificaciones: i.especificaciones ?? "Y",
    observacion: i.observacion ?? "",
  }));
}

/**
 * F-LOG-08 de la OC (DEC-L102): cuando se recibió todo, Logística marca por
 * producto Y/F, escribe la conclusión y elige el V°B° del área solicitante.
 */
export default function VerificacionOrden({ orderId, version }: { orderId: string; version: string }) {
  const datos = useDatos(`oc-verif:${orderId}:${version}`, () => obtenerVerificacionOc(orderId), { conservar: true });
  const [editando, setEditando] = useState(false);
  const v = datos.datos;

  return (
    <Card className="mb-4 overflow-hidden">
      <CardHeader className="flex items-center gap-2">
        <ClipboardCheck className="size-4.5 text-slate-500" />
        <h2 className="flex-1 font-semibold text-slate-900">Verificación (F-LOG-08)</h2>
        {v?.registrada && !editando && (
          <BotonDescarga descargar={() => descargarPdfVerificacion(orderId, v.orderCode)} />
        )}
        {v?.puedeEditar && !editando && (
          <Button size="sm" variant={v.registrada ? "secondary" : "primary"} icon={<PenLine className="size-4" />} onClick={() => setEditando(true)}>
            {v.registrada ? "Editar" : "Verificar"}
          </Button>
        )}
      </CardHeader>
      {!v ? (
        <CardBody className="flex flex-col gap-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </CardBody>
      ) : editando ? (
        <Formulario
          v={v}
          onCancelar={() => setEditando(false)}
          onGuardado={(nuevo) => {
            datos.mutar(nuevo);
            setEditando(false);
          }}
        />
      ) : v.registrada ? (
        <Resumen v={v} />
      ) : (
        <CardBody>
          <p className="text-sm text-slate-500">{v.disponible ? "Lista para verificar." : v.motivo}</p>
          <Notas v={v} />
        </CardBody>
      )}
    </Card>
  );
}

function Marca({ m }: { m: MarcaVerificacion }) {
  if (!m) return <span className="text-slate-300">—</span>;
  return <span className={`font-mono font-semibold ${m === "Y" ? "text-emerald-700" : "text-rose-700"}`}>{m}</span>;
}

function Notas({ v }: { v: VerificacionOc }) {
  if (v.notas.length === 0) return null;
  return (
    <p className="mt-2 text-xs text-slate-500">
      Notas de inventario:{" "}
      {v.notas.map((n, i) => (
        <span key={n.id}>
          {i > 0 && ", "}
          <Link to={`/almacen/notas/${n.id}`} className="font-medium text-brand-600 hover:text-brand-700">
            {n.codigo}
          </Link>
        </span>
      ))}
    </p>
  );
}

function Resumen({ v }: { v: VerificacionOc }) {
  return (
    <>
      <CardBody className="flex flex-col gap-1 pb-3 text-sm text-slate-600">
        <span>
          Verificada el {fecha(v.verificadaEn)} por <span className="font-medium text-slate-800">{v.verificadaPor?.nombre}</span>
          {v.especialista && (
            <>
              {" "}· V°B° área solicitante: <span className="font-medium text-slate-800">{v.especialista.nombre}</span>
            </>
          )}
        </span>
        <span className="whitespace-normal text-slate-700">{v.conclusion}</span>
        <Notas v={v} />
      </CardBody>
      <table className="tabla">
        <thead>
          <tr className="border-y border-slate-200 cabecera-tabla">
            <th className="px-5 py-2.5">Producto</th>
            <th className="px-5 py-2.5">Recibido</th>
            {COLUMNAS.map((c) => (
              <th key={c.clave} className="px-5 py-2.5 text-center" title={c.ayuda}>
                {c.titulo}
              </th>
            ))}
            <th className="px-5 py-2.5">Observación</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {v.items.map((i) => (
            <tr key={i.orderItemId}>
              <td className="col-flex px-5 py-2.5 text-slate-700" title={i.descripcion}>
                {i.descripcion}
              </td>
              <td className="px-5 py-2.5 text-slate-600">
                {i.recibida} / {i.cantidad} {i.um ?? ""}
              </td>
              {COLUMNAS.map((c) => (
                <td key={c.clave} className="px-5 py-2.5 text-center">
                  <Marca m={i[c.clave]} />
                </td>
              ))}
              <td className="max-w-[16rem] truncate px-5 py-2.5 text-slate-600" title={i.observacion ?? ""}>
                {i.observacion ?? "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function Formulario({
  v,
  onCancelar,
  onGuardado,
}: {
  v: VerificacionOc;
  onCancelar: () => void;
  onGuardado: (v: VerificacionOc) => void;
}) {
  const [filas, setFilas] = useState<Fila[]>(() => filasDe(v));
  const [conclusion, setConclusion] = useState(v.conclusion ?? "CONFORME");
  const [especialista, setEspecialista] = useState(v.especialista?.id ?? v.especialistaSugeridoId ?? "");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const porId = new Map(v.items.map((i) => [i.orderItemId, i]));
  const elegido = v.candidatos.find((c) => c.id === especialista);

  function cambiar(id: string, cambio: Partial<Fila>) {
    setFilas((prev) => prev.map((f) => (f.orderItemId === id ? { ...f, ...cambio } : f)));
  }

  async function guardar() {
    if (!especialista) return setError("Elige quién da el V°B° del área solicitante o especialista.");
    if (!conclusion.trim()) return setError("Escribe las observaciones o conclusiones de la verificación.");
    if (filas.some((f) => !f.especificaciones)) return setError("Marca si cada producto cumple con las especificaciones.");
    setGuardando(true);
    setError(null);
    try {
      const res = await guardarVerificacionOc(v.orderId, {
        especialistaUserId: especialista,
        conclusion: conclusion.trim(),
        items: filas.map((f) => ({ ...f, observacion: f.observacion.trim() || null })),
      });
      if (res.ok && res.result) onGuardado(res.result);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      <div className="overflow-x-auto">
        <table className="tabla">
          <thead>
            <tr className="border-y border-slate-200 cabecera-tabla">
              <th className="px-5 py-2.5">Producto</th>
              <th className="px-5 py-2.5">Recibido</th>
              {COLUMNAS.map((c) => (
                <th key={c.clave} className="px-3 py-2.5 text-center" title={c.ayuda}>
                  {c.titulo}
                </th>
              ))}
              <th className="px-5 py-2.5">Observación</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filas.map((f) => {
              const item = porId.get(f.orderItemId) as ItemVerificacion;
              return (
                <tr key={f.orderItemId}>
                  <td className="col-flex px-5 py-2.5 text-slate-700" title={item.descripcion}>
                    {item.descripcion}
                  </td>
                  <td className="px-5 py-2.5 text-slate-600">
                    {item.recibida} / {item.cantidad} {item.um ?? ""}
                  </td>
                  {COLUMNAS.map((c) => (
                    <td key={c.clave} className="px-3 py-2">
                      <SelectorMarca
                        valor={f[c.clave]}
                        opcional={c.opcional}
                        titulo={c.ayuda}
                        onCambio={(m) => cambiar(f.orderItemId, { [c.clave]: m })}
                      />
                    </td>
                  ))}
                  <td className="px-5 py-2">
                    <input
                      value={f.observacion}
                      maxLength={500}
                      onChange={(e) => cambiar(f.orderItemId, { observacion: e.target.value })}
                      className={`${inputClass} w-full min-w-[10rem]`}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <CardBody className="flex flex-col gap-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Observaciones y/o conclusiones" required>
            <textarea
              value={conclusion}
              maxLength={2000}
              rows={2}
              onChange={(e) => setConclusion(e.target.value)}
              className={`${inputClass} w-full resize-y`}
            />
          </Field>
          <Field label="V°B° área solicitante o especialista" required>
            <PersonaCombobox personas={v.candidatos} valor={especialista} onSeleccionar={setEspecialista} />
            {elegido && !elegido.tieneFirma && (
              <span className="text-xs text-amber-700">No tiene firma cargada: saldrá solo su nombre.</span>
            )}
          </Field>
        </div>
        {error && <Alert tone="danger">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancelar}>
            Cancelar
          </Button>
          <Button loading={guardando} onClick={guardar}>
            Guardar verificación
          </Button>
        </div>
      </CardBody>
    </>
  );
}

/** Y / F, y "—" (no aplica) donde la pregunta es opcional. */
function SelectorMarca({
  valor,
  opcional,
  titulo,
  onCambio,
}: {
  valor: MarcaVerificacion;
  opcional: boolean;
  titulo: string;
  onCambio: (m: MarcaVerificacion) => void;
}) {
  const opciones: MarcaVerificacion[] = opcional ? ["Y", "F", null] : ["Y", "F"];
  return (
    <div role="radiogroup" title={titulo} className="mx-auto inline-flex w-fit rounded-md border border-slate-200 p-0.5">
      {opciones.map((m) => {
        const activo = valor === m;
        const color = m === "Y" ? "bg-emerald-50 text-emerald-700" : m === "F" ? "bg-rose-50 text-rose-700" : "bg-slate-100 text-slate-600";
        return (
          <button
            key={m ?? "na"}
            type="button"
            role="radio"
            aria-checked={activo}
            aria-label={m === "Y" ? "Conforme" : m === "F" ? "No conforme" : "No aplica"}
            onClick={() => onCambio(m)}
            className={`min-w-[1.75rem] rounded px-2 py-1 font-mono text-xs font-semibold ${activo ? color : "text-slate-400 hover:text-slate-700"}`}
          >
            {m ?? "—"}
          </button>
        );
      })}
    </div>
  );
}

function PersonaCombobox({
  personas,
  valor,
  onSeleccionar,
}: {
  personas: PersonaVerificacion[];
  valor: string;
  onSeleccionar: (id: string) => void;
}) {
  const [texto, setTexto] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const caja = useRef<HTMLDivElement>(null);
  const elegido = personas.find((p) => p.id === valor) ?? null;
  const opciones = useMemo(() => {
    const coincide = buscador(texto);
    return personas.filter((p) => coincide(`${p.nombre} ${p.cargo ?? ""}`));
  }, [personas, texto]);

  function elegir(id: string) {
    onSeleccionar(id);
    setTexto("");
    setAbierto(false);
  }

  return (
    <div ref={caja} className="relative">
      <input
        role="combobox"
        aria-expanded={abierto}
        aria-autocomplete="list"
        value={abierto ? texto : elegido?.nombre ?? ""}
        placeholder="Escribe el nombre"
        onFocus={() => {
          setAbierto(true);
          setActivo(0);
        }}
        onBlur={() => setTimeout(() => setAbierto(false), 120)}
        onChange={(e) => {
          setTexto(e.target.value);
          setActivo(0);
          setAbierto(true);
        }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setActivo((a) => Math.min(a + 1, opciones.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActivo((a) => Math.max(a - 1, 0));
          } else if (e.key === "Enter" && abierto && opciones[activo]) {
            e.preventDefault();
            elegir(opciones[activo].id);
          } else if (e.key === "Escape") {
            setAbierto(false);
          }
        }}
        className={`${inputClass} w-full pr-9`}
      />
      <ChevronsUpDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      {abierto && (
        <Flotante ancla={caja} anchoAncla altoMaximo={288} className="rounded-md border border-slate-200 bg-white py-1 shadow-flotante">
          <ul role="listbox">
            {opciones.length === 0 ? (
              <li className="px-3 py-2 text-sm text-slate-500">Sin coincidencias</li>
            ) : (
              opciones.map((p, i) => (
                <li
                  key={p.id}
                  role="option"
                  aria-selected={p.id === valor}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    elegir(p.id);
                  }}
                  onMouseEnter={() => setActivo(i)}
                  className={`flex cursor-pointer items-center gap-2 px-3 py-2 text-sm ${i === activo ? "bg-slate-100" : ""}`}
                >
                  <span className="min-w-0 flex-1 truncate text-slate-800" title={p.nombre}>
                    {p.nombre}
                  </span>
                  {p.cargo && <span className="max-w-[45%] shrink-0 truncate text-xs text-slate-500">{p.cargo}</span>}
                </li>
              ))
            )}
          </ul>
        </Flotante>
      )}
    </div>
  );
}
