import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { BellRing, X } from "lucide-react";
import { useRealtime } from "../realtime/RealtimeProvider";
import { useAuth } from "../auth/AuthContext";
import type { Requerimiento } from "../api/types";
import Button from "./ui/Button";
import { Card, CardBody } from "./ui/Card";

/**
 * Aviso discreto cuando llega un evento en vivo (decision de aprobacion) y
 * el usuario NO esta ya mirando ese requerimiento en pantalla (ahi el
 * propio RequerimientoDetalle.tsx ya lo refleja in-place).
 */
export default function Toast() {
  const { onMiRequerimientoDecidido, onNuevaAprobacion } = useRealtime();
  const { tieneAlgunRol } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [mensaje, setMensaje] = useState<{ texto: string; ir: string } | null>(null);

  useEffect(() => {
    return onMiRequerimientoDecidido((actualizado: Requerimiento) => {
      if (location.pathname === `/requerimientos/${actualizado.id}`) return;
      const texto =
        actualizado.status === "RECHAZADO"
          ? `Tu requerimiento ${actualizado.code} fue rechazado.`
          : actualizado.status === "OBSERVADO"
            ? `Tu requerimiento ${actualizado.code} fue observado: revisa el motivo y corrígelo.`
          : actualizado.status === "ATENCION_COMPLETA"
            ? `Tu requerimiento ${actualizado.code} fue entregado por completo.`
            : actualizado.status === "ATENCION_PARCIAL"
              ? `Llegó una entrega parcial de tu requerimiento ${actualizado.code}.`
              : `Tu requerimiento ${actualizado.code} fue aprobado.`;
      setMensaje({ texto, ir: `/requerimientos/${actualizado.id}` });
    });
  }, [onMiRequerimientoDecidido, location.pathname]);

  useEffect(() => {
    if (!tieneAlgunRol("APROBADOR", "ADMIN")) return;
    return onNuevaAprobacion((pendiente) => {
      if (location.pathname === "/aprobaciones") return;
      setMensaje({ texto: `Nuevo requerimiento por aprobar: ${pendiente.requerimiento.code}.`, ir: "/aprobaciones" });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onNuevaAprobacion, location.pathname]);

  useEffect(() => {
    if (!mensaje) return;
    const t = setTimeout(() => setMensaje(null), 8000);
    return () => clearTimeout(t);
  }, [mensaje]);

  if (!mensaje) return null;

  return (
    <div className="fixed right-4 top-4 z-50 w-full max-w-xs animate-toast-in">
      <Card className="shadow-flotante">
        <CardBody className="flex gap-3 p-4">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-slate-50 text-slate-700">
            <BellRing className="size-4" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm text-slate-700">{mensaje.texto}</p>
            <div className="mt-3 flex gap-2">
              <Button
                size="sm"
                onClick={() => {
                  navigate(mensaje.ir);
                  setMensaje(null);
                }}
              >
                Ver
              </Button>
              <Button size="sm" variant="ghost" icon={<X className="size-3.5" />} onClick={() => setMensaje(null)}>
                Cerrar
              </Button>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}
