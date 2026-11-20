import { useState } from "react";
import { Landmark, Plus, Star } from "lucide-react";
import { listarMonedas } from "../../api/compras";
import {
  agregarCuentaBancaria,
  editarCuentaBancaria,
  listarCuentasBancarias,
  quitarCuentaBancaria,
  type CuentaBancaria,
  type GuardarCuentaBancaria,
} from "../../api/proveedores";
import { useDatos } from "../../api/useDatos";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";
import EmptyState from "../ui/EmptyState";
import Field, { inputClass } from "../ui/Field";
import MenuAcciones from "../ui/MenuAcciones";
import PanelLateral from "../ui/PanelLateral";
import { Skeleton } from "../ui/Skeleton";

const TIPO: Record<CuentaBancaria["tipo"], string> = {
  CORRIENTE: "Corriente",
  AHORROS: "Ahorros",
  DETRACCION: "Detracción",
  OTRA: "Otra",
};

const VACIA: GuardarCuentaBancaria = { banco: "", tipo: "CORRIENTE", moneda: "PEN", numero: "", cci: "", principal: false };

/**
 * Cuentas bancarias del proveedor (DEC-L078): para pagar y para la detracción.
 * Dato sensible: la pestaña solo existe para Logística y Administración, y la
 * auditoría guarda el número enmascarado.
 */
export default function PestanaCuentas({ supplierId }: { supplierId: string }) {
  const confirmar = useConfirmar();
  const datos = useDatos(`proveedor-cuentas:${supplierId}`, () => listarCuentasBancarias(supplierId));
  const cuentas = datos.datos ?? [];
  const [editando, setEditando] = useState<{ id: string | null; valores: GuardarCuentaBancaria } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  async function quitar(c: CuentaBancaria) {
    const ok = await confirmar({
      titulo: "¿Quitar esta cuenta?",
      mensaje: `${c.banco} · ${c.numero}`,
      confirmar: "Quitar",
      peligro: true,
    });
    if (!ok) return;
    const res = await quitarCuentaBancaria(c.id);
    if (res.ok && res.result) {
      datos.mutar(res.result);
      setAviso(res.message);
      setError(null);
    } else setError(res.message);
  }

  if (datos.cargando) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-20 rounded-xl" />
        <Skeleton className="h-20 rounded-xl" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <Alert tone="danger">{error}</Alert>}
      {aviso && <Alert tone="good">{aviso}</Alert>}
      <div className="flex justify-end">
        <Button icon={<Plus className="size-4" />} onClick={() => setEditando({ id: null, valores: VACIA })}>
          Agregar cuenta
        </Button>
      </div>

      {cuentas.length === 0 ? (
        <Card>
          <EmptyState icon={Landmark} title="Sin cuentas bancarias" />
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <ul className="divide-y divide-slate-100">
            {cuentas.map((c) => (
              <li key={c.id} className="flex items-center gap-4 px-4 py-3">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-slate-50">
                  <Landmark className="size-4 text-slate-500" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2 text-sm font-medium text-slate-900">
                    <span className="truncate">{c.banco}</span>
                    {c.principal && (
                      <span className="flex items-center gap-1 text-xs font-normal text-amber-700">
                        <Star className="size-3.5 fill-current" /> Principal
                      </span>
                    )}
                  </p>
                  <p className="mt-0.5 font-mono text-xs text-slate-700">
                    {c.numero}
                    {c.cci && <span className="ml-3 text-slate-500">CCI {c.cci}</span>}
                  </p>
                </div>
                <Badge tone={c.tipo === "DETRACCION" ? "amarillo" : "gris"}>{TIPO[c.tipo]}</Badge>
                <span className="w-10 shrink-0 text-right font-mono text-xs text-slate-500">{c.moneda}</span>
                <MenuAcciones
                  acciones={[
                    {
                      label: "Editar",
                      onClick: () =>
                        setEditando({
                          id: c.id,
                          valores: { banco: c.banco, tipo: c.tipo, moneda: c.moneda, numero: c.numero, cci: c.cci ?? "", principal: c.principal },
                        }),
                    },
                    { label: "Quitar", onClick: () => void quitar(c), peligro: true },
                  ]}
                />
              </li>
            ))}
          </ul>
        </Card>
      )}

      {editando && (
        <PanelCuenta
          supplierId={supplierId}
          cuentaId={editando.id}
          inicial={editando.valores}
          onCerrar={() => setEditando(null)}
          onGuardado={(lista, mensaje) => {
            datos.mutar(lista);
            setAviso(mensaje);
            setError(null);
            setEditando(null);
          }}
        />
      )}
    </div>
  );
}

function PanelCuenta({
  supplierId,
  cuentaId,
  inicial,
  onCerrar,
  onGuardado,
}: {
  supplierId: string;
  cuentaId: string | null;
  inicial: GuardarCuentaBancaria;
  onCerrar: () => void;
  onGuardado: (lista: CuentaBancaria[], mensaje: string) => void;
}) {
  const [v, setV] = useState(inicial);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const monedas = useDatos("monedas", listarMonedas, { fresco: 60 * 60_000 });

  function campo<K extends keyof GuardarCuentaBancaria>(k: K, valor: GuardarCuentaBancaria[K]) {
    setV((prev) => ({ ...prev, [k]: valor }));
  }

  async function guardar(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    try {
      const cuerpo = { ...v, cci: v.cci?.trim() || null };
      const res = cuentaId ? await editarCuentaBancaria(cuentaId, cuerpo) : await agregarCuentaBancaria(supplierId, cuerpo);
      if (res.ok && res.result) onGuardado(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo={cuentaId ? "Editar cuenta bancaria" : "Agregar cuenta bancaria"}
      ancho="max-w-md"
      onCerrar={onCerrar}
      pie={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button type="submit" form="form-cuenta" loading={guardando}>
            Guardar
          </Button>
        </div>
      }
    >
      <form id="form-cuenta" onSubmit={guardar} className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <Field label="Banco" required>
          <input
            required
            value={v.banco}
            onChange={(e) => campo("banco", e.target.value)}
            placeholder="BCP, BBVA, Interbank, Banco de la Nación…"
            className={`${inputClass} w-full`}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tipo" required>
            <select value={v.tipo} onChange={(e) => campo("tipo", e.target.value as CuentaBancaria["tipo"])} className={`${inputClass} w-full`}>
              {(Object.keys(TIPO) as CuentaBancaria["tipo"][]).map((t) => (
                <option key={t} value={t}>
                  {TIPO[t]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Moneda" required>
            <select value={v.moneda} onChange={(e) => campo("moneda", e.target.value)} className={`${inputClass} w-full`}>
              {(monedas.datos ?? [{ currencyCode: "PEN", name: "Sol peruano" }]).map((m) => (
                <option key={m.currencyCode} value={m.currencyCode}>
                  {m.currencyCode}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Número de cuenta" required>
          <input
            required
            value={v.numero}
            onChange={(e) => campo("numero", e.target.value)}
            inputMode="numeric"
            className={`${inputClass} w-full font-mono`}
          />
        </Field>
        <Field label="CCI (interbancario)">
          <input
            value={v.cci ?? ""}
            onChange={(e) => campo("cci", e.target.value)}
            inputMode="numeric"
            placeholder="20 dígitos"
            className={`${inputClass} w-full font-mono`}
          />
        </Field>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={v.principal} onChange={(e) => campo("principal", e.target.checked)} />
          Cuenta principal para pagos
        </label>
      </form>
    </PanelLateral>
  );
}
