import { useState } from "react";
import { Download, FileText } from "lucide-react";
import type { ApiResponse } from "../api/http";
import Button from "./ui/Button";

/** Boton que descarga un archivo (PDF/Excel) y muestra el error al lado si falla. */
export default function BotonDescarga({
  descargar,
  etiqueta = "PDF",
  tipo = "pdf",
}: {
  descargar: () => Promise<ApiResponse<null>>;
  etiqueta?: string;
  tipo?: "pdf" | "excel";
}) {
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onClick() {
    setCargando(true);
    setError(null);
    try {
      const res = await descargar();
      if (!res.ok) setError(res.message);
    } finally {
      setCargando(false);
    }
  }

  const Icono = tipo === "pdf" ? FileText : Download;
  return (
    <span className="inline-flex items-center gap-2">
      {error && <span className="text-xs text-rose-700">{error}</span>}
      <Button size="sm" variant="secondary" loading={cargando} onClick={onClick} icon={<Icono className="size-4" />}>
        {etiqueta}
      </Button>
    </span>
  );
}
