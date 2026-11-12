import { useState } from "react";
import { Check, Copy, Link2 } from "lucide-react";
import { anularEnlaceRfq, generarEnlaceRfq } from "../../api/compras";
import { descargarPdfRfq } from "../../api/documentos";
import type { EnlaceCotizacion } from "../../api/types";
import BotonDescarga from "../BotonDescarga";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { inputClass } from "../ui/Field";
import MenuAcciones from "../ui/MenuAcciones";
import { useConfirmar } from "../ui/Confirmacion";

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "2-digit" }) : "";
}

/**
 * Enlace para que el proveedor cotice en linea sobre las lineas de la RFQ
 * (DEC-L052). La URL solo se ve al generarla (se guarda su hash); despues se
 * muestra el estado y se puede generar otra (la anterior deja de funcionar).
 */
export default function EnlaceProveedor({
  rfqId,
  rfqCode,
  enlace,
  onCambio,
  onError,
}: {
  rfqId: string;
  rfqCode: string;
  enlace: EnlaceCotizacion | null;
  onCambio: (e: EnlaceCotizacion | null) => void;
  onError: (mensaje: string) => void;
}) {
  const confirmar = useConfirmar();
  const [procesando, setProcesando] = useState(false);
  const [copiado, setCopiado] = useState(false);

  async function generar(reemplaza: boolean) {
    if (reemplaza) {
      const r = await confirmar({
        titulo: "¿Generar un enlace nuevo?",
        mensaje: "El enlace anterior deja de funcionar; envía el nuevo al proveedor.",
        confirmar: "Generar",
      });
      if (!r) return;
    }
    setProcesando(true);
    try {
      const res = await generarEnlaceRfq(rfqId);
      if (res.ok && res.result) onCambio(res.result);
      else onError(res.message);
    } finally {
      setProcesando(false);
    }
  }

  async function anular() {
    const r = await confirmar({
      titulo: "¿Anular el enlace del proveedor?",
      mensaje: "Ya no podrá abrirlo ni enviar su cotización por ahí.",
      confirmar: "Anular",
      peligro: true,
    });
    if (!r) return;
    setProcesando(true);
    try {
      const res = await anularEnlaceRfq(rfqId);
      if (res.ok) onCambio(null);
      else onError(res.message);
    } finally {
      setProcesando(false);
    }
  }

  async function copiar(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    } catch {
      onError("No se pudo copiar: selecciona el enlace y cópialo a mano.");
    }
  }

  const vencido = enlace && !enlace.vigente;
  const estado = enlace
    ? [
        enlace.respondidoEl ? `Respondió el ${fecha(enlace.respondidoEl)}` : enlace.abiertoEl ? `Abierto el ${fecha(enlace.abiertoEl)}` : "Aún no lo abre",
        vencido ? "vencido" : `vence el ${fecha(enlace.venceEl)}`,
      ].join(" · ")
    : null;

  return (
    <Card className="mb-6">
      <CardBody className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <Link2 className="size-4.5 shrink-0 text-slate-500" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">Enlace para cotizar en línea</p>
            {estado && <p className={`text-xs ${vencido ? "text-amber-700" : "text-slate-500"}`}>{estado}</p>}
          </div>
          {!enlace ? (
            <Button size="sm" variant="secondary" loading={procesando} onClick={() => generar(false)}>
              Generar enlace
            </Button>
          ) : (
            <MenuAcciones
              acciones={[
                { label: "Generar enlace nuevo", onClick: () => generar(true), disabled: procesando },
                ...(enlace.vigente ? [{ label: "Anular enlace", onClick: anular, peligro: true, disabled: procesando }] : []),
              ]}
            />
          )}
        </div>
        {enlace?.url && enlace.token && (
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <input
              readOnly
              value={enlace.url}
              aria-label="Enlace del proveedor"
              onFocus={(e) => e.target.select()}
              className={`${inputClass} min-w-0 flex-1 py-1.5 font-mono text-xs`}
            />
            <div className="flex shrink-0 items-center gap-2">
              <Button
                size="sm"
                variant="secondary"
                icon={copiado ? <Check className="size-4" /> : <Copy className="size-4" />}
                onClick={() => copiar(enlace.url!)}
              >
                {copiado ? "Copiado" : "Copiar"}
              </Button>
              <BotonDescarga
                etiqueta="PDF con enlace"
                descargar={() => descargarPdfRfq(rfqId, rfqCode, enlace.token!)}
              />
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
