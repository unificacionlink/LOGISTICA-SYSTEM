import type { CompraCajaChica } from "../../api/types";
import Badge from "../ui/Badge";

/** Estado de una compra con caja chica (DEC-L054: Logistica verifica el comprobante). */
export default function EstadoCompra({ estado }: { estado: CompraCajaChica["estado"] }) {
  if (estado === "PENDIENTE_REGULARIZACION") return <Badge tone="amarillo">Por revisar</Badge>;
  if (estado === "OBSERVADO") return <Badge tone="rojo">Observada</Badge>;
  if (estado === "REGULARIZADO") return <Badge tone="verde">Regularizada</Badge>;
  return <Badge tone="gris">Anulada</Badge>;
}
