import { useState } from "react";
import { Check, FileUp, PackageOpen, X } from "lucide-react";
import { decidirOrden, registrarPagoOrden } from "../../api/compras";
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

/** Estados en los que el almacen recibe (FlujoOrden.ESTADOS_RECIBIBLES). */
const RECIBIBLES = new Set(["EMITIDA", "EN_ATENCION", "RECEPCION_PARCIAL"]);

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE") : "";
}

const ENLACE = /^https?:\/\/\S+$/i;

/**
 * Aprobacion, pago y recepcion de la OC (DEC-L061). Una sola accion principal
 * segun el paso: aprobar → recibir (DEC-L094: al aprobarse queda liberada).
 * El pago es control, va aparte y se registra con enlaces.
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
  const [panel, setPanel] = useState<"pago" | null>(null);
  const [estadoPago, setEstadoPago] = useState<"ANTICIPO" | "PAGADO">("PAGADO");
  // DEC-L094: constancia y factura como enlace; DEC-L089: el pago final exige la factura.
  const [constancia, setConstancia] = useState("");
  const [factura, setFactura] = useState("");
  const [numeroFactura, setNumeroFactura] = useState("");

  const gestiona = tieneAlgunRol("LOGISTICA", "ADMIN");
  const emitidaPorMi = usuario?.id === orden.createdById;
  // DEC-L100: ADMIN también decide la OC que emitió.
  const puedeDecidir = tieneAlgunRol("ADMIN") || (tieneAlgunRol("GERENCIA", "LOGISTICA") && !emitidaPorMi);
  const porAprobar = orden.status === "POR_APROBAR";
  const anulada = orden.status === "ANULADA";
  // DEC-L080: la orden solo de servicios no pasa por el almacen; se cierra al pagarse.
  const servicios = orden.servicesOnly;
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
  const conFactura = !!factura.trim() || !!numeroFactura.trim();
  const facturaValida = ENLACE.test(factura.trim()) && /^[A-Za-z0-9]{1,6}-\d{1,12}$/.test(numeroFactura.trim());
  const listoParaPagar = ENLACE.test(constancia.trim()) && (pideFactura || conFactura ? facturaValida : true);

  async function onPagar() {
    const datosFactura = facturaValida ? { numero: numeroFactura.trim(), url: factura.trim() } : undefined;
    await ejecutar("pago", () => registrarPagoOrden(orden.orderId, estadoPago, constancia.trim(), datosFactura));
    setConstancia("");
    setFactura("");
    setNumeroFactura("");
  }

  const recepcion =
    orden.status === "RECEPCION_COMPLETA" || orden.status === "CERRADA"
      ? "Completa"
      : orden.status === "RECEPCION_PARCIAL"
        ? "Parcial"
        : "Pendiente";

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
          label: "Recepción",
          valor: recepcion,
          ok: recepcion === "Completa",
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
            {!porAprobar && !servicios && !orden.bloqueoRecepcion && RECIBIBLES.has(orden.status) && gestiona && (
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
            <input
              value={constancia}
              maxLength={500}
              onChange={(e) => setConstancia(e.target.value)}
              placeholder="Enlace de la constancia de pago"
              aria-label="Enlace de la constancia de pago"
              className={`${inputClass} min-w-0 flex-1`}
            />
            {pideFactura && (
              <div className="flex w-full flex-wrap items-center gap-2">
                <input
                  value={numeroFactura}
                  maxLength={30}
                  onChange={(e) => setNumeroFactura(e.target.value)}
                  placeholder="Factura (ej. F001-123)"
                  aria-label="Número de factura"
                  className={`${inputClass} w-48`}
                />
                <input
                  value={factura}
                  maxLength={500}
                  onChange={(e) => setFactura(e.target.value)}
                  placeholder="Enlace de la factura"
                  aria-label="Enlace de la factura"
                  className={`${inputClass} min-w-0 flex-1`}
                />
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
