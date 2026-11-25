import { useRef, useState } from "react";
import { Check, FileUp, PackageOpen, Unlock, X } from "lucide-react";
import { decidirOrden, liberarOrden, registrarPagoOrden, subirConstanciaPago, subirFactura } from "../../api/compras";
import type { Orden } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { inputClass } from "../ui/Field";
import { useConfirmar } from "../ui/Confirmacion";

const ETIQUETA_PAGO: Record<Orden["paymentStatus"], string> = {
  PENDIENTE: "Pendiente",
  ANTICIPO: "Anticipo",
  PAGADO: "Pagado",
};

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE") : "";
}

/**
 * Aprobacion, pago y liberacion de la OC (DEC-L061). Una sola accion principal
 * segun el paso: aprobar → liberar → recibir. El pago es control y va aparte.
 */
export default function CicloOrden({
  orden,
  onCambio,
  onError,
}: {
  orden: Orden;
  onCambio: (o: Orden) => void;
  onError: (mensaje: string) => void;
}) {
  const { usuario, tieneAlgunRol } = useAuth();
  const confirmar = useConfirmar();
  const [ocupado, setOcupado] = useState<string | null>(null);
  const [panel, setPanel] = useState<"liberar" | "pago" | null>(null);
  const [nota, setNota] = useState("");
  const [estadoPago, setEstadoPago] = useState<"ANTICIPO" | "PAGADO">("PAGADO");
  const [archivo, setArchivo] = useState<File | null>(null);
  // DEC-L089: el pago final exige la factura del proveedor (número y archivo).
  const [factura, setFactura] = useState<File | null>(null);
  const [numeroFactura, setNumeroFactura] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const gestiona = tieneAlgunRol("LOGISTICA", "ADMIN");
  const emitidaPorMi = usuario?.id === orden.createdById;
  const puedeDecidir = tieneAlgunRol("GERENCIA", "LOGISTICA", "ADMIN") && !emitidaPorMi;
  const porAprobar = orden.status === "POR_APROBAR";
  const anulada = orden.status === "ANULADA";
  // DEC-L080: la orden solo de servicios no pasa por el almacen; se cierra al pagarse.
  const servicios = orden.servicesOnly;
  const porLiberar = orden.status === "EMITIDA" && !orden.releasedAt && !servicios;
  const puedePagar = gestiona && !porAprobar && !anulada && orden.paymentStatus !== "PAGADO";

  async function ejecutar(clave: string, accion: () => Promise<{ ok: boolean; result?: Orden | null; message: string }>) {
    setOcupado(clave);
    try {
      const res = await accion();
      if (res.ok && res.result) {
        onCambio(res.result);
        setPanel(null);
      } else onError(res.message);
    } finally {
      setOcupado(null);
    }
  }

  async function onRechazar() {
    const r = await confirmar({
      titulo: `¿Rechazar ${orden.orderCode}?`,
      mensaje: "La orden se anula y sus productos vuelven a cotización.",
      confirmar: "Rechazar orden",
      peligro: true,
      motivo: "Motivo del rechazo",
    });
    if (r) await ejecutar("rechazar", () => decidirOrden(orden.orderId, "RECHAZADO", r.motivo));
  }

  const pideFactura = estadoPago === "PAGADO" && !orden.invoiceNumber;
  const listoParaPagar = !!archivo && (!pideFactura || (!!factura && /^[A-Za-z0-9]{1,6}-\d{1,12}$/.test(numeroFactura.trim())));

  async function onPagar() {
    if (!archivo) return;
    await ejecutar("pago", async () => {
      const doc = await subirConstanciaPago(archivo);
      if (!doc.ok || !doc.result) return { ok: false, message: doc.message };
      let datosFactura: { numero: string; documentId: string } | undefined;
      if (factura && numeroFactura.trim()) {
        const fac = await subirFactura(factura);
        if (!fac.ok || !fac.result) return { ok: false, message: fac.message };
        datosFactura = { numero: numeroFactura.trim(), documentId: fac.result.documentId };
      }
      return registrarPagoOrden(orden.orderId, estadoPago, doc.result.documentId, datosFactura);
    });
    setArchivo(null);
    setFactura(null);
    setNumeroFactura("");
  }

  const pasos = [
    {
      label: "Aprobación",
      valor: porAprobar ? "Pendiente" : anulada && !orden.approvedAt ? "Rechazada" : `Aprobada ${fecha(orden.approvedAt)}`,
      ok: !!orden.approvedAt,
    },
    {
      label: "Pago",
      valor: ETIQUETA_PAGO[orden.paymentStatus],
      ok: orden.paymentStatus === "PAGADO",
      enlace: orden.paymentProofUrl,
      detalle: orden.invoiceNumber ? `Factura ${orden.invoiceNumber}` : null,
    },
    servicios
      ? {
          label: "Servicio",
          valor: orden.status === "CERRADA" ? "Cerrada al pagar" : "Se cierra al registrar el pago",
          ok: orden.status === "CERRADA",
        }
      : {
          label: "Liberación",
          valor: orden.releasedAt ? `Liberada ${fecha(orden.releasedAt)}` : "Pendiente",
          ok: !!orden.releasedAt,
          detalle: orden.releaseNote,
        },
  ];

  return (
    <Card className="mb-4" data-tutorial="ciclo-orden">
      <CardBody className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-3">
          {pasos.map((p) => (
            <div key={p.label} className="min-w-0">
              <div className="rotulo">{p.label}</div>
              <div className={`mt-1 truncate text-sm ${p.ok ? "text-emerald-700" : "text-slate-700"}`} title={p.detalle ?? p.valor}>
                {p.valor}
                {p.enlace && (
                  <a href={p.enlace} target="_blank" rel="noopener noreferrer" className="ml-2 text-brand-700 hover:underline">
                    Constancia
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>

        {!anulada && (
          <div className="flex flex-wrap items-center gap-2">
            {porAprobar && puedeDecidir && (
              <>
                <Button
                  icon={<Check className="size-4" />}
                  loading={ocupado === "aprobar"}
                  onClick={() => ejecutar("aprobar", () => decidirOrden(orden.orderId, "APROBADO"))}
                >
                  Aprobar orden
                </Button>
                <Button variant="secondary" icon={<X className="size-4" />} loading={ocupado === "rechazar"} onClick={onRechazar}>
                  Rechazar
                </Button>
              </>
            )}
            {porAprobar && !puedeDecidir && (
              <span className="text-sm text-slate-500">
                {emitidaPorMi ? "Espera la aprobación de Gerencia o de otro usuario de Logística." : "Espera su aprobación."}
              </span>
            )}
            {porLiberar && gestiona && (
              <Button icon={<Unlock className="size-4" />} onClick={() => setPanel(panel === "liberar" ? null : "liberar")}>
                Liberar para recepción
              </Button>
            )}
            {orden.releasedAt && !orden.bloqueoRecepcion && orden.status !== "RECEPCION_COMPLETA" && gestiona && (
              <Button href={`/almacen/ordenes/${orden.orderId}/recepcion`} icon={<PackageOpen className="size-4" />}>
                Registrar recepción
              </Button>
            )}
            {puedePagar && (
              <Button variant="secondary" icon={<FileUp className="size-4" />} onClick={() => setPanel(panel === "pago" ? null : "pago")}>
                Registrar pago
              </Button>
            )}
          </div>
        )}

        {panel === "liberar" && (
          <div className="flex flex-wrap items-center gap-2">
            <input
              className={`${inputClass} min-w-0 flex-1`}
              placeholder="Nota para el almacén (opcional)"
              maxLength={300}
              value={nota}
              onChange={(e) => setNota(e.target.value)}
            />
            <Button
              loading={ocupado === "liberar"}
              onClick={() => ejecutar("liberar", () => liberarOrden(orden.orderId, nota.trim()))}
            >
              Confirmar liberación
            </Button>
          </div>
        )}

        {panel === "pago" && (
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex rounded-md border border-slate-200 p-0.5">
              {(["ANTICIPO", "PAGADO"] as const)
                .filter((e) => e !== "ANTICIPO" || orden.paymentStatus === "PENDIENTE")
                .map((e) => (
                  <button
                    key={e}
                    type="button"
                    onClick={() => setEstadoPago(e)}
                    className={`rounded px-3 py-1.5 text-sm ${estadoPago === e ? "bg-brand-700 text-white" : "text-slate-600 hover:bg-slate-50"}`}
                  >
                    {ETIQUETA_PAGO[e]}
                  </button>
                ))}
            </div>
            <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-md border border-dashed border-slate-300 px-3 py-2 text-sm text-slate-600 hover:border-slate-400 hover:bg-slate-50">
              <FileUp className="size-4 shrink-0 text-slate-400" />
              <span className="truncate">{archivo ? archivo.name : "Constancia (PDF o imagen)"}</span>
              <input
                ref={inputRef}
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                className="sr-only"
                onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
              />
            </label>
            {pideFactura && (
              <div className="flex w-full flex-wrap items-center gap-2">
                <input
                  value={numeroFactura}
                  maxLength={30}
                  onChange={(e) => setNumeroFactura(e.target.value)}
                  placeholder="Factura (ej. F001-123)"
                  aria-label="Número de factura"
                  className="h-9 w-48 rounded-md border border-slate-300 px-3 text-sm"
                />
                <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-md border border-dashed border-slate-300 px-3 py-2 text-sm text-slate-600 hover:border-slate-400 hover:bg-slate-50">
                  <FileUp className="size-4 shrink-0 text-slate-400" />
                  <span className="truncate">{factura ? factura.name : "Factura (PDF o imagen)"}</span>
                  <input
                    type="file"
                    accept="application/pdf,image/jpeg,image/png,image/webp"
                    className="sr-only"
                    onChange={(e) => setFactura(e.target.files?.[0] ?? null)}
                  />
                </label>
              </div>
            )}
            <Button disabled={!listoParaPagar} loading={ocupado === "pago"} onClick={onPagar}>
              Guardar pago
            </Button>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
