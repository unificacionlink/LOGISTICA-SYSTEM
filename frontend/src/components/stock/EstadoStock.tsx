import type { SaldoStock } from "../../api/types";
import Badge from "../ui/Badge";
import { estadoStock } from "./estado";

export default function EstadoStock({ saldo }: { saldo: SaldoStock }) {
  const e = estadoStock(saldo);
  if (e === "AGOTADO") return <Badge tone="rojo">Agotado</Badge>;
  if (e === "BAJO_MINIMO") return <Badge tone="amarillo">Bajo mínimo</Badge>;
  if (e === "SIN_STOCK") return <Badge tone="gris">Sin stock</Badge>;
  return null;
}
