import { useEffect, useRef, useState } from "react";
import { ImageOff, Upload } from "lucide-react";
import { quitarFirmaUsuario, subirFirmaUsuario, verFirmaUsuario } from "../../api/admin";
import type { UsuarioAdmin } from "../../api/types";
import Button from "../ui/Button";
import { useConfirmar } from "../ui/Confirmacion";
import { Skeleton } from "../ui/Skeleton";
import type { Aplicar } from "./PanelUsuario";

/**
 * Firma escaneada del usuario (DEC-L102): se estampa en los V°B° del F-LOG-08
 * (verificación de adquisiciones) cuando él verifica o da el visto bueno.
 */
export default function FirmaUsuario({ u, aplicar }: { u: UsuarioAdmin; aplicar: Aplicar }) {
  const confirmar = useConfirmar();
  const input = useRef<HTMLInputElement>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [ocupado, setOcupado] = useState(false);

  // Vista previa desde el bucket privado (solo ADMIN); se libera al cambiar o salir.
  useEffect(() => {
    if (!u.tieneFirma) return;
    let vigente = true;
    let creada: string | null = null;
    void verFirmaUsuario(u.id).then((x) => {
      creada = x;
      if (vigente) setUrl(x);
      else if (x) URL.revokeObjectURL(x);
    });
    return () => {
      vigente = false;
      if (creada) URL.revokeObjectURL(creada);
      setUrl(null);
    };
  }, [u.id, u.tieneFirma, version]);

  async function subir(file: File) {
    setOcupado(true);
    try {
      if (aplicar(await subirFirmaUsuario(u.id, file))) setVersion((v) => v + 1);
    } finally {
      setOcupado(false);
      if (input.current) input.current.value = "";
    }
  }

  async function quitar() {
    const ok = await confirmar({
      titulo: "¿Quitar la firma?",
      mensaje: "Las verificaciones saldrán solo con su nombre.",
      confirmar: "Quitar",
      peligro: true,
    });
    if (!ok) return;
    setOcupado(true);
    try {
      aplicar(await quitarFirmaUsuario(u.id));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="flex items-center gap-3">
      <div className="flex h-16 w-40 shrink-0 items-center justify-center rounded border border-slate-200 bg-slate-50">
        {u.tieneFirma && url ? (
          <img src={url} alt="Firma" className="max-h-full max-w-full object-contain" />
        ) : u.tieneFirma ? (
          <Skeleton className="h-12 w-32" />
        ) : (
          <ImageOff className="size-5 text-slate-300" aria-label="Sin firma" />
        )}
      </div>
      <input
        ref={input}
        type="file"
        accept="image/png,image/jpeg"
        className="hidden"
        onChange={(ev) => {
          const f = ev.target.files?.[0];
          if (f) void subir(f);
        }}
      />
      <Button variant="secondary" size="sm" icon={<Upload className="size-3.5" />} loading={ocupado} onClick={() => input.current?.click()}>
        {u.tieneFirma ? "Reemplazar" : "Subir firma"}
      </Button>
      {u.tieneFirma && (
        <Button variant="ghost" size="sm" onClick={quitar} disabled={ocupado}>
          Quitar
        </Button>
      )}
    </div>
  );
}
