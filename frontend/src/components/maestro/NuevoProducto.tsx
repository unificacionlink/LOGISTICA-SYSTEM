import { useState } from "react";
import { X } from "lucide-react";
import { crearProducto } from "../../api/maestro";
import type { Clasificacion, ProductoCreado, TipoAtributo } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody, CardHeader } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import { SelectorFamilia, SelectorUnidad } from "./SelectoresMaestro";
import { ETIQUETA_ATRIBUTO, unidadPorDefecto } from "./atributos";

/** Alta directa de un SKU por Logistica (DEC-L025). El codigo lo genera el sistema. */
export default function NuevoProducto({
  clasificacion,
  onCreado,
  cerrar,
}: {
  clasificacion: Clasificacion;
  onCreado: (p: ProductoCreado, mensaje: string) => void;
  cerrar: () => void;
}) {
  const [familyId, setFamilyId] = useState("");
  const [nombre, setNombre] = useState("");
  const [uomId, setUomId] = useState(unidadPorDefecto(clasificacion));
  const [conVariantes, setConVariantes] = useState(false);
  const [tipo, setTipo] = useState<TipoAtributo>("TALLA");
  const [valores, setValores] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const lista = valores
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    if (conVariantes && lista.length === 0) {
      setError(`Indica al menos un valor de ${ETIQUETA_ATRIBUTO[tipo].toLowerCase()}.`);
      return;
    }
    setError(null);
    setGuardando(true);
    try {
      const res = await crearProducto({
        familyId,
        nombre,
        uomId,
        variantes: conVariantes ? lista.map((valor) => ({ tipo, valor })) : undefined,
      });
      if (res.ok && res.result) onCreado(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Card className="mb-4">
      <CardHeader className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900">Nuevo producto</h2>
        <button type="button" onClick={cerrar} title="Cerrar" className="text-slate-400 hover:text-slate-700">
          <X className="size-4" />
        </button>
      </CardHeader>
      <CardBody>
        <form onSubmit={onGuardar} className="grid gap-4 sm:grid-cols-[2fr_1fr_1fr]">
          <Field label="Nombre" required>
            <input
              required
              minLength={3}
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className={`${inputClass} w-full`}
            />
          </Field>
          <Field label="Familia › Subfamilia" required>
            <SelectorFamilia clasificacion={clasificacion} value={familyId} onChange={setFamilyId} />
          </Field>
          <Field label="Unidad" required>
            <SelectorUnidad clasificacion={clasificacion} value={uomId} onChange={setUomId} />
          </Field>

          <label className="flex items-center gap-2 text-sm text-slate-600 sm:col-span-3">
            <input type="checkbox" checked={conVariantes} onChange={(e) => setConVariantes(e.target.checked)} />
            Tiene tallas, colores o modelos
          </label>
          {conVariantes && (
            <div className="grid gap-4 sm:col-span-3 sm:grid-cols-[160px_1fr]">
              <Field label="Atributo">
                <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoAtributo)} className={`${inputClass} w-full`}>
                  {(Object.keys(ETIQUETA_ATRIBUTO) as TipoAtributo[]).map((t) => (
                    <option key={t} value={t}>
                      {ETIQUETA_ATRIBUTO[t]}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Valores separados por coma">
                <input
                  value={valores}
                  onChange={(e) => setValores(e.target.value)}
                  placeholder="S, M, L, XL"
                  className={`${inputClass} w-full`}
                />
              </Field>
            </div>
          )}

          {error && <Alert tone="danger" className="sm:col-span-3">{error}</Alert>}
          <div className="flex gap-2 sm:col-span-3">
            <Button type="submit" loading={guardando}>
              Crear producto
            </Button>
            <Button type="button" variant="ghost" onClick={cerrar}>
              Cancelar
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}
