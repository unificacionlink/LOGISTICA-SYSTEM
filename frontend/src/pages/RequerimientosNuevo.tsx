import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ShoppingCart, Trash2 } from "lucide-react";
import {
  actualizarRequerimiento,
  crearRequerimiento,
  obtenerCatalogoFormulario,
  obtenerRequerimiento,
} from "../api/requerimientos";
import type { Ceco, Familia, VarianteCatalogo } from "../api/types";
import { etiquetaFamilia } from "../components/familia";
import { useAuth } from "../auth/AuthContext";
import BuscadorProductos, { Miniatura } from "../components/BuscadorProductos";
import SolicitarProducto from "../components/maestro/SolicitarProducto";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody, CardHeader } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import Field, { inputClass } from "../components/ui/Field";
import { EMPRESAS } from "../components/empresas";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

import CampoNumero from "../components/ui/CampoNumero";
interface ItemCarrito {
  variante: VarianteCatalogo;
  qty: string;
  observacion: string;
}

export default function RequerimientosNuevo() {
  const { id } = useParams<{ id: string }>();
  const editando = Boolean(id);
  const navigate = useNavigate();
  const { tieneAlgunRol } = useAuth();
  const puedeEditarImagenes = tieneAlgunRol("LOGISTICA", "ADMIN");
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [variantes, setVariantes] = useState<VarianteCatalogo[]>([]);
  const [familias, setFamilias] = useState<Familia[]>([]);
  const [cecos, setCecos] = useState<Ceco[]>([]);

  const [cecoId, setCecoId] = useState("");
  const [priority, setPriority] = useState("NORMAL");
  const [purpose, setPurpose] = useState("");
  const [requiredDate, setRequiredDate] = useState("");
  const [justification, setJustification] = useState("");
  const [items, setItems] = useState<ItemCarrito[]>([]);
  // Plazo de atencion (DEC-L035): el mayor de los SKU del pedido, contado desde hoy.
  const plazoMaximo = items.reduce<number | null>(
    (max, it) => (it.variante.plazoAtencionDias == null ? max : Math.max(max ?? 0, it.variante.plazoAtencionDias)),
    null,
  );
  const atencionEstimada =
    plazoMaximo == null
      ? null
      : new Date(Date.now() + plazoMaximo * 86_400_000).toLocaleDateString("es-PE", {
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
        });
  const [errorItems, setErrorItems] = useState<string | null>(null);
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    Promise.all([
      obtenerCatalogoFormulario(),
      editando ? obtenerRequerimiento(id!) : Promise.resolve(null),
    ])
      .then(([catalogoRes, detalleRes]) => {
        if (!catalogoRes.ok || !catalogoRes.result) {
          setErrorCarga(catalogoRes.message);
          return;
        }
        setVariantes(catalogoRes.result.variantes);
        setFamilias(catalogoRes.result.familias);
        setCecos(catalogoRes.result.cecos);

        if (detalleRes) {
          if (!detalleRes.ok || !detalleRes.result) {
            setErrorCarga(detalleRes.message);
            return;
          }
          const { requerimiento, items: itemsExistentes } = detalleRes.result;
          if (requerimiento.status !== "BORRADOR" && requerimiento.status !== "OBSERVADO") {
            setErrorCarga("Solo se puede editar un requerimiento en borrador u observado.");
            return;
          }
          setCecoId(requerimiento.cecoId ?? "");
          // Al corregir no cambia la empresa (va en el codigo del REQ); su CECO se conserva
          // aunque ya no este asignado (DEC-L029).
          const deSuEmpresa = catalogoRes.result.cecos.filter((c) => c.empresaCode === requerimiento.empresaCode);
          if (requerimiento.cecoId && !deSuEmpresa.some((c) => c.cecoId === requerimiento.cecoId)) {
            deSuEmpresa.unshift({
              cecoId: requerimiento.cecoId,
              code: "",
              name: requerimiento.cecoNombre ?? "",
              empresaCode: requerimiento.empresaCode ?? "",
            });
          }
          setCecos(deSuEmpresa);
          setPriority(requerimiento.priority);
          setPurpose(requerimiento.purpose);
          setRequiredDate(requerimiento.requiredDate ?? "");
          setJustification(requerimiento.justification);

          const porCodigoVariante = new Map(catalogoRes.result.variantes.map((v) => [v.codigoVariante, v]));
          const itemsPrecargados: ItemCarrito[] = [];
          for (const it of itemsExistentes) {
            const variante = porCodigoVariante.get(it.codigoVariante);
            if (variante) itemsPrecargados.push({ variante, qty: String(it.cantidad), observacion: it.observacion ?? "" });
          }
          setItems(itemsPrecargados);
        }
      })
      .finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editando, id]);

  // Si el usuario pide para varias empresas, el selector agrupa sus CECOs por empresa.
  const gruposCeco = useMemo(() => {
    const codes = [...new Set(cecos.map((c) => c.empresaCode))];
    return codes.map((code) => ({
      code,
      label: EMPRESAS.find((e) => e.code === code)?.label ?? code,
      cecos: cecos.filter((c) => c.empresaCode === code),
    }));
  }, [cecos]);

  const cantidadesEnCarrito = useMemo(() => {
    const mapa: Record<string, number> = {};
    for (const it of items) mapa[it.variante.variantId] = Number(it.qty) || 0;
    return mapa;
  }, [items]);

  function agregarAlCarrito(v: VarianteCatalogo) {
    setErrorItems(null);
    setItems((prev) => {
      const idx = prev.findIndex((it) => it.variante.variantId === v.variantId);
      if (idx === -1) return [...prev, { variante: v, qty: "1", observacion: "" }];
      const copia = [...prev];
      const actual = Number(copia[idx].qty) || 0;
      copia[idx] = { ...copia[idx], qty: String(actual + 1) };
      return copia;
    });
  }

  function actualizarItem(variantId: string, cambios: Partial<ItemCarrito>) {
    setItems((prev) => prev.map((it) => (it.variante.variantId === variantId ? { ...it, ...cambios } : it)));
  }

  function quitarItem(variantId: string) {
    setItems((prev) => prev.filter((it) => it.variante.variantId !== variantId));
  }

  function manejarImagenSubida(productId: string, url: string) {
    setVariantes((prev) => prev.map((v) => (v.productId === productId ? { ...v, imagenUrl: url } : v)));
    setItems((prev) =>
      prev.map((it) => (it.variante.productId === productId ? { ...it, variante: { ...it.variante, imagenUrl: url } } : it)),
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const validos = items.filter((it) => Number(it.qty) > 0);
    if (validos.length === 0) {
      setErrorItems("Agrega al menos un producto con cantidad mayor a 0.");
      return;
    }
    setErrorItems(null);
    setErrorEnvio(null);
    setEnviando(true);
    try {
      const payload = {
        cecoId,
        priority,
        purpose,
        justification,
        requiredDate: requiredDate || undefined,
        items: validos.map((it) => ({
          variantId: it.variante.variantId,
          qtyRequested: Number(it.qty),
          observation: it.observacion || undefined,
        })),
      };
      const res = editando ? await actualizarRequerimiento(id!, payload) : await crearRequerimiento(payload);
      if (!res.ok || !res.result) {
        setErrorEnvio(res.message);
        return;
      }
      navigate(`/requerimientos/${res.result.id}?msg=${encodeURIComponent(res.message)}`);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <PageHeader
        title={editando ? "Editar requerimiento" : "Nuevo requerimiento"}
        subtitle={
          editando
            ? undefined
            : "Busca y agrega productos a tu pedido. Quedará en BORRADOR hasta que lo envíes a aprobación."
        }
        backTo={editando ? `/requerimientos/${id}` : "/requerimientos"}
        backLabel={editando ? "Requerimiento" : "Requerimientos"}
      />

      {cargando ? (
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
          </CardBody>
        </Card>
      ) : errorCarga ? (
        <Alert tone="danger">{errorCarga}</Alert>
      ) : cecos.length === 0 ? (
        <Alert tone="info">
          No tienes centros de costo asignados. Pide a un administrador que te los asigne para crear requerimientos.
        </Alert>
      ) : (
        <form onSubmit={onSubmit} className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[1fr_380px]">
          <div className="flex flex-col gap-6">
            <Card>
              <CardBody className="flex flex-col gap-5">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Centro de costo (CECO)" required>
                    <select
                      required
                      value={cecoId}
                      onChange={(e) => setCecoId(e.target.value)}
                      className={`${inputClass} w-full`}
                    >
                      <option value="" disabled>
                        Selecciona un CECO…
                      </option>
                      {gruposCeco.length > 1
                        ? gruposCeco.map((g) => (
                            <optgroup key={g.code} label={g.label}>
                              {g.cecos.map((c) => (
                                <option key={c.cecoId} value={c.cecoId}>
                                  {etiquetaCeco(c)}
                                </option>
                              ))}
                            </optgroup>
                          ))
                        : cecos.map((c) => (
                            <option key={c.cecoId} value={c.cecoId}>
                              {etiquetaCeco(c)}
                            </option>
                          ))}
                    </select>
                  </Field>
                  <Field label="Prioridad" required>
                    <select
                      required
                      value={priority}
                      onChange={(e) => setPriority(e.target.value)}
                      className={`${inputClass} w-full`}
                    >
                      <option value="NORMAL">Normal</option>
                      <option value="URGENTE">Urgente</option>
                      <option value="CRITICA">Crítica</option>
                    </select>
                  </Field>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Propósito" required>
                    <select
                      required
                      value={purpose}
                      onChange={(e) => setPurpose(e.target.value)}
                      className={`${inputClass} w-full`}
                    >
                      <option value="" disabled>
                        Selecciona…
                      </option>
                      <option value="CONSUMO">Consumo</option>
                      <option value="VENTA">Venta</option>
                      <option value="PRESTAMO">Préstamo</option>
                      <option value="IMPLEMENTACION">Implementación</option>
                    </select>
                  </Field>
                  <Field label="Fecha requerida" hint={atencionEstimada && `Atención estimada: ${atencionEstimada}`}>
                    <input
                      type="date"
                      value={requiredDate}
                      onChange={(e) => setRequiredDate(e.target.value)}
                      className={`${inputClass} w-full`}
                    />
                  </Field>
                </div>

                <Field label="Justificación" required>
                  <textarea
                    required
                    rows={3}
                    minLength={10}
                    placeholder="Motivo del requerimiento (mínimo 10 caracteres)"
                    value={justification}
                    onChange={(e) => setJustification(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </CardBody>
            </Card>

            <Card>
              <CardHeader>
                <h2 className="font-semibold text-slate-900">Agregar productos</h2>
                <p className="mt-0.5 text-sm text-slate-500">Búscalos por SKU o nombre y añádelos a tu pedido.</p>
              </CardHeader>
              <CardBody>
                <BuscadorProductos
                  variantes={variantes}
                  familias={familias}
                  cantidadesEnCarrito={cantidadesEnCarrito}
                  onAgregar={agregarAlCarrito}
                  puedeEditarImagenes={puedeEditarImagenes}
                  onImagenSubida={manejarImagenSubida}
                />
                <SolicitarProducto />
              </CardBody>
            </Card>
          </div>

          <aside className="lg:sticky lg:top-6">
            <Card className="overflow-hidden">
              <CardHeader className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ShoppingCart className="size-4.5 text-slate-500" />
                  <h2 className="font-semibold text-slate-900">Tu pedido</h2>
                </div>
                <Badge tone={items.length > 0 ? "azul" : "gris"}>{items.length}</Badge>
              </CardHeader>

              {items.length === 0 ? (
                <EmptyState
                  icon={ShoppingCart}
                  title="Aún no agregas productos"
                />
              ) : (
                <ul className="max-h-[420px] divide-y divide-slate-100 overflow-y-auto">
                  {items.map((it) => (
                    <li key={it.variante.variantId} className="flex flex-col gap-2 p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex min-w-0 flex-1 items-center gap-2">
                          <Miniatura
                            url={it.variante.imagenUrl}
                            titulo={it.variante.nombre}
                            detalle={`${it.variante.codigoVariante} · ${etiquetaFamilia(it.variante.tipoNombre, it.variante.familiaNombre)}`}
                          />
                          <div className="min-w-0 flex-1">
                            <span className="block truncate font-mono text-xs text-slate-700">{it.variante.etiqueta}</span>
                            {it.variante.plazoAtencionDias != null && (
                              <span className="text-xs text-slate-400">
                                Atención en {it.variante.plazoAtencionDias}{" "}
                                {it.variante.plazoAtencionDias === 1 ? "día" : "días"}
                              </span>
                            )}
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => quitarItem(it.variante.variantId)}
                          title="Quitar del pedido"
                          className="shrink-0 text-slate-400 hover:text-rose-600"
                        >
                          <Trash2 className="size-4" />
                        </button>
                      </div>
                      <div className="flex gap-2">
                        <CampoNumero
                          decimales={0}
                          value={it.qty}
                          onChange={(v) => actualizarItem(it.variante.variantId, { qty: v })}
                          className={`${inputClass} w-20 py-1.5 text-sm`}
                        />
                        <input
                          type="text"
                          value={it.observacion}
                          onChange={(e) => actualizarItem(it.variante.variantId, { observacion: e.target.value })}
                          placeholder="Observación (opcional)"
                          className={`${inputClass} flex-1 py-1.5 text-sm`}
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              <div className="flex flex-col gap-3 border-t border-slate-100 p-4">
                {errorItems && <Alert tone="danger">{errorItems}</Alert>}
                {errorEnvio && <Alert tone="danger">{errorEnvio}</Alert>}
                <Button type="submit" loading={enviando} disabled={items.length === 0} className="w-full">
                  {enviando ? "Guardando…" : editando ? "Guardar cambios" : "Solicitar"}
                </Button>
              </div>
            </Card>
          </aside>
        </form>
      )}
    </div>
  );
}

function etiquetaCeco(c: Ceco) {
  return c.code ? `${c.code} — ${c.name}` : c.name;
}
