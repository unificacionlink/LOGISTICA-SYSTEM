import { useState } from "react";
import { CheckCircle2, Circle, ScanLine } from "lucide-react";
import { confirmarPickingNota } from "../../api/almacen";
import type { NotaInventarioDetalle } from "../../api/types";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { fmtDia, fmtFecha } from "../formato";

/**
 * Preparacion de la Nota de Inventario antes del envio (ALM-02, DEC-L062):
 * picking → envio. DEC-L093: el embalaje dejo de ser un paso; el envio se
 * registra solo con el enlace de su ticket (formulario de la pagina).
 */
export default function PreparacionNota({
  detalle,
  onCambio,
  onError,
}: {
  detalle: NotaInventarioDetalle;
  onCambio: (d: NotaInventarioDetalle) => void;
  onError: (mensaje: string) => void;
}) {
  const { nota } = detalle;
  const [ocupado, setOcupado] = useState(false);

  const enviada = !!nota.envio || nota.status !== "EMITIDA";
  const paso = !nota.pickedAt ? 0 : 1;
  const pasos = [
    { label: "Picking", hecho: !!nota.pickedAt, fecha: nota.pickedAt ? fmtFecha(nota.pickedAt) : null },
    { label: "Envío", hecho: enviada, fecha: nota.envio?.shipDate ? fmtDia(nota.envio.shipDate) : null },
  ];

  async function piquear() {
    setOcupado(true);
    try {
      const res = await confirmarPickingNota(nota.inventoryNoteId);
      if (res.ok && res.result) onCambio(res.result);
      else onError(res.message);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <Card className="mb-4" data-tutorial="preparacion-nota">
      <CardBody className="flex flex-col gap-4">
        <ol aria-label="Preparación de la nota" className="grid gap-3 sm:grid-cols-2">
          {pasos.map((p, i) => (
            <li key={p.label} aria-current={!enviada && i === paso ? "step" : undefined} className="flex min-w-0 items-start gap-2">
              {p.hecho ? (
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden="true" />
              ) : (
                <Circle className={`mt-0.5 size-4 shrink-0 ${i === paso ? "text-brand-600" : "text-slate-300"}`} aria-hidden="true" />
              )}
              <div className="min-w-0">
                <div className="rotulo">{p.label}</div>
                <div className={`mt-0.5 truncate text-sm ${p.hecho ? "text-emerald-700" : "text-slate-500"}`}>
                  {p.hecho ? (p.fecha ? `Hecho · ${p.fecha}` : "Hecho") : "Pendiente"}
                </div>
              </div>
            </li>
          ))}
        </ol>

        {!enviada && !nota.pickedAt && (
          <div>
            <Button icon={<ScanLine className="size-4" />} loading={ocupado} onClick={piquear}>
              Confirmar picking
            </Button>
          </div>
        )}
      </CardBody>
    </Card>
  );
}
