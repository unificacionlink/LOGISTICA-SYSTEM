import { useEffect, useMemo, useRef, useState } from "react";
import { ImagePlus, Send, X } from "lucide-react";
import {
  codigoReporte,
  enviarReporte,
  ESTADOS_REPORTE,
  misReportes,
  type ReportePropio,
} from "../../api/reportes";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";

const MAX_CAPTURA = 5 * 1024 * 1024;

/** Formulario del reporte (DEC-L104): pantalla y zona salen solas; el usuario describe. */
export default function FormReporte({
  modulo,
  pantalla,
  ruta,
  zona,
  onCambiarZona,
  onEnviado,
}: {
  modulo: string | null;
  pantalla: string | null;
  ruta: string;
  zona: string | null;
  onCambiarZona: () => void;
  onEnviado: (mensaje: string) => void;
}) {
  const [descripcion, setDescripcion] = useState("");
  const [captura, setCaptura] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mios, setMios] = useState<ReportePropio[] | null>(null);
  const archivo = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let vivo = true;
    void misReportes().then((r) => vivo && r.ok && setMios(r.result ?? []));
    return () => {
      vivo = false;
    };
  }, []);

  const vista = useMemo(() => (captura ? URL.createObjectURL(captura) : null), [captura]);
  useEffect(() => () => {
    if (vista) URL.revokeObjectURL(vista);
  }, [vista]);

  function tomarImagen(f: File | null | undefined) {
    if (!f) return;
    if (f.type !== "image/png" && f.type !== "image/jpeg") {
      setError("La captura debe ser PNG o JPG.");
      return;
    }
    if (f.size > MAX_CAPTURA) {
      setError("La captura supera los 5 MB.");
      return;
    }
    setError(null);
    setCaptura(f);
  }

  /** Una captura pegada con Ctrl+V (Impr Pant, recortes de Windows). */
  function alPegar(e: React.ClipboardEvent) {
    const item = [...e.clipboardData.items].find((i) => i.type.startsWith("image/"));
    if (!item) return;
    e.preventDefault();
    const f = item.getAsFile();
    if (f) tomarImagen(new File([f], `captura.${f.type === "image/png" ? "png" : "jpg"}`, { type: f.type }));
  }

  async function enviar() {
    if (!descripcion.trim()) {
      setError("Describe la observación.");
      return;
    }
    setEnviando(true);
    setError(null);
    const res = await enviarReporte({
      modulo,
      pantalla,
      ruta,
      zona,
      descripcion: descripcion.trim(),
      viewport: `${window.innerWidth}x${window.innerHeight}`,
      captura,
    });
    setEnviando(false);
    if (res.ok && res.result) onEnviado(`${codigoReporte(res.result.numero)} enviado. Gracias.`);
    else setError(res.message);
  }

  const lugar = [modulo, pantalla && pantalla !== modulo ? pantalla : null].filter(Boolean).join(" · ") || ruta;

  return (
    <PanelLateral
      titulo="Reportar observación"
      subtitulo={lugar}
      ancho="max-w-md"
      onCerrar={onCambiarZona}
      pie={
        <>
          <Button variant="ghost" onClick={onCambiarZona}>
            Volver
          </Button>
          <Button onClick={enviar} loading={enviando} icon={<Send className="size-4" />}>
            Enviar reporte
          </Button>
        </>
      }
    >
      <div onPaste={alPegar}>
        <SeccionPanel
          titulo="Zona señalada"
          accion={
            <button type="button" onClick={onCambiarZona} className="text-xs font-medium text-brand-700 hover:underline">
              Cambiar
            </button>
          }
        >
          <p className="whitespace-normal text-sm text-slate-800">{zona ?? "Toda la pantalla"}</p>
        </SeccionPanel>

        <SeccionPanel titulo="Observación">
          <textarea
            autoFocus
            rows={6}
            maxLength={2000}
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            placeholder="¿Qué pasó o qué debería ser distinto?"
            className={`${inputClass} w-full resize-y`}
          />
          <div className="mt-3 flex items-center gap-3">
            {vista ? (
              <div className="relative">
                <img src={vista} alt="Captura adjunta" className="h-20 rounded-md border border-slate-200 object-cover" />
                <button
                  type="button"
                  onClick={() => setCaptura(null)}
                  aria-label="Quitar captura"
                  className="absolute -right-2 -top-2 flex size-5 items-center justify-center rounded-full bg-slate-800 text-white"
                >
                  <X className="size-3" />
                </button>
              </div>
            ) : (
              <Button
                size="sm"
                variant="secondary"
                icon={<ImagePlus className="size-4" />}
                onClick={() => archivo.current?.click()}
                title="También puedes pegarla con Ctrl+V"
              >
                Captura (opcional)
              </Button>
            )}
            <input
              ref={archivo}
              type="file"
              accept="image/png,image/jpeg"
              className="hidden"
              onChange={(e) => {
                tomarImagen(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
          </div>
          {error && (
            <Alert tone="danger" className="mt-3">
              {error}
            </Alert>
          )}
        </SeccionPanel>

        {mios && mios.length > 0 && (
          <SeccionPanel titulo={`Mis reportes (${mios.length})`}>
            <details className="group">
              <summary className="cursor-pointer text-xs font-medium text-brand-700">Ver estado</summary>
              <ul className="mt-2 flex flex-col gap-2">
                {mios.map((r) => (
                  <li key={r.id} className="rounded-md border border-slate-200 px-3 py-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-xs text-slate-500">{codigoReporte(r.numero)}</span>
                      <Badge tone={ESTADOS_REPORTE[r.estado].tono}>{ESTADOS_REPORTE[r.estado].label}</Badge>
                    </div>
                    <p className="mt-1 line-clamp-2 whitespace-normal text-xs text-slate-700">{r.descripcion}</p>
                    {r.respuesta && (
                      <p className="mt-1 whitespace-normal text-xs text-slate-500">Respuesta: {r.respuesta}</p>
                    )}
                  </li>
                ))}
              </ul>
            </details>
          </SeccionPanel>
        )}
      </div>
    </PanelLateral>
  );
}
