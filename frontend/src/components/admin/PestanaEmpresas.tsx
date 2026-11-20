import { useEffect, useRef, useState } from "react";
import { ImageOff, Pencil, Upload } from "lucide-react";
import {
  editarEmpresa,
  type ImagenEmpresa,
  listarEmpresas,
  quitarImagenEmpresa,
  subirImagenEmpresa,
  verImagenEmpresa,
} from "../../api/admin";
import type { EmpresaDatos } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";
import Field, { inputClass } from "../ui/Field";
import { Skeleton } from "../ui/Skeleton";

const IMAGENES: { tipo: ImagenEmpresa; label: string; tiene: (e: EmpresaDatos) => boolean }[] = [
  { tipo: "logo", label: "Logo", tiene: (e) => e.tieneLogo },
  { tipo: "firma-gerencia", label: "Firma V° B° Gerencia", tiene: (e) => e.tieneFirmaGerencia },
  { tipo: "firma-logistica", label: "Firma V° B° Logística", tiene: (e) => e.tieneFirmaLogistica },
];

/**
 * Datos de cada empresa para la orden de compra F-LOG-09 (DEC-L079): razon
 * social, RUC, direccion, telefono, logo y firmas escaneadas. Las firmas solo
 * se estampan en las OC aprobadas.
 */
export default function PestanaEmpresas() {
  const empresas = useDatos("admin:empresas", listarEmpresas);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);

  function aplicar(res: { ok: boolean; result: EmpresaDatos[] | null; message: string }): boolean {
    if (res.ok && res.result) {
      empresas.mutar(res.result);
      setError(null);
      setExito(res.message);
      return true;
    }
    setExito(null);
    setError(res.message);
    return false;
  }

  if (!empresas.datos) {
    return (
      <div className="flex flex-col gap-4">
        {empresas.error && <Alert tone="danger">{empresas.error}</Alert>}
        {!empresas.error &&
          [0, 1, 2].map((i) => (
            <Card key={i}>
              <CardBody className="flex flex-col gap-3">
                <Skeleton className="h-5 w-48" />
                <Skeleton className="h-4 w-full" />
                <div className="flex gap-3">
                  <Skeleton className="h-20 w-40" />
                  <Skeleton className="h-20 w-40" />
                  <Skeleton className="h-20 w-40" />
                </div>
              </CardBody>
            </Card>
          ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert tone="danger">{error}</Alert>}
      {exito && <Alert tone="good">{exito}</Alert>}
      {empresas.datos.map((e) => (
        <TarjetaEmpresa key={e.id} empresa={e} aplicar={aplicar} />
      ))}
    </div>
  );
}

function TarjetaEmpresa({
  empresa,
  aplicar,
}: {
  empresa: EmpresaDatos;
  aplicar: (res: { ok: boolean; result: EmpresaDatos[] | null; message: string }) => boolean;
}) {
  const [editando, setEditando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [nombre, setNombre] = useState(empresa.nombre);
  const [ruc, setRuc] = useState(empresa.ruc ?? "");
  const [direccion, setDireccion] = useState(empresa.direccion ?? "");
  const [telefono, setTelefono] = useState(empresa.telefono ?? "");

  function abrir() {
    setNombre(empresa.nombre);
    setRuc(empresa.ruc ?? "");
    setDireccion(empresa.direccion ?? "");
    setTelefono(empresa.telefono ?? "");
    setEditando(true);
  }

  async function guardar() {
    setGuardando(true);
    try {
      const ok = aplicar(
        await editarEmpresa(empresa.id, {
          nombre: nombre.trim(),
          ruc: ruc.trim(),
          direccion: direccion.trim(),
          telefono: telefono.trim(),
        }),
      );
      if (ok) setEditando(false);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Card>
      <CardBody className="flex flex-col gap-4">
        {editando ? (
          <div className="flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Razón social" required>
                <input className={`${inputClass} w-full`} value={nombre} maxLength={180} onChange={(ev) => setNombre(ev.target.value)} />
              </Field>
              <Field label="RUC">
                <input
                  className={`${inputClass} w-full font-mono`}
                  value={ruc}
                  inputMode="numeric"
                  maxLength={11}
                  onChange={(ev) => setRuc(ev.target.value.replace(/\D/g, ""))}
                />
              </Field>
              <Field label="Dirección">
                <input className={`${inputClass} w-full`} value={direccion} maxLength={250} onChange={(ev) => setDireccion(ev.target.value)} />
              </Field>
              <Field label="Teléfono">
                <input className={`${inputClass} w-full`} value={telefono} maxLength={40} onChange={(ev) => setTelefono(ev.target.value)} />
              </Field>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setEditando(false)} disabled={guardando}>
                Cancelar
              </Button>
              <Button onClick={guardar} loading={guardando} disabled={!nombre.trim()}>
                Guardar
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline gap-2">
                <span className="font-mono text-xs text-slate-500">{empresa.code}</span>
                <h3 className="truncate text-base font-semibold text-slate-900" title={empresa.nombre}>
                  {empresa.nombre}
                </h3>
              </div>
              <p className="mt-1 truncate text-sm text-slate-600">
                RUC {empresa.ruc ?? "—"} · {empresa.direccion ?? "Sin dirección"} · {empresa.telefono ?? "Sin teléfono"}
              </p>
            </div>
            <Button variant="secondary" size="sm" icon={<Pencil className="size-3.5" />} onClick={abrir}>
              Editar
            </Button>
          </div>
        )}

        <div className="grid gap-3 sm:grid-cols-3">
          {IMAGENES.map((img) => (
            <CasillaImagen key={img.tipo} empresa={empresa} tipo={img.tipo} label={img.label} tiene={img.tiene(empresa)} aplicar={aplicar} />
          ))}
        </div>
      </CardBody>
    </Card>
  );
}

function CasillaImagen({
  empresa,
  tipo,
  label,
  tiene,
  aplicar,
}: {
  empresa: EmpresaDatos;
  tipo: ImagenEmpresa;
  label: string;
  tiene: boolean;
  aplicar: (res: { ok: boolean; result: EmpresaDatos[] | null; message: string }) => boolean;
}) {
  const confirmar = useConfirmar();
  const input = useRef<HTMLInputElement>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [ocupado, setOcupado] = useState(false);

  // Vista previa desde el bucket privado (solo ADMIN); se libera al cambiar o salir.
  useEffect(() => {
    if (!tiene) return;
    let vigente = true;
    let creada: string | null = null;
    void verImagenEmpresa(empresa.id, tipo).then((u) => {
      creada = u;
      if (vigente) setUrl(u);
      else if (u) URL.revokeObjectURL(u);
    });
    return () => {
      vigente = false;
      if (creada) URL.revokeObjectURL(creada);
      setUrl(null);
    };
  }, [empresa.id, tipo, tiene, version]);

  async function subir(file: File) {
    setOcupado(true);
    try {
      if (aplicar(await subirImagenEmpresa(empresa.id, tipo, file))) setVersion((v) => v + 1);
    } finally {
      setOcupado(false);
      if (input.current) input.current.value = "";
    }
  }

  async function quitar() {
    const ok = await confirmar({
      titulo: `¿Quitar ${label.toLowerCase()}?`,
      mensaje: "Las órdenes de compra saldrán sin esta imagen.",
      confirmar: "Quitar",
      peligro: true,
    });
    if (!ok) return;
    setOcupado(true);
    try {
      aplicar(await quitarImagenEmpresa(empresa.id, tipo));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-slate-200 p-3">
      <div className="rotulo">{label}</div>
      <div className="flex h-20 items-center justify-center rounded bg-slate-50">
        {tiene && url ? (
          <img src={url} alt={label} className="max-h-full max-w-full object-contain" />
        ) : tiene ? (
          <Skeleton className="h-16 w-32" />
        ) : (
          <ImageOff className="size-5 text-slate-300" aria-label="Sin imagen" />
        )}
      </div>
      <div className="flex gap-2">
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
          {tiene ? "Reemplazar" : "Subir"}
        </Button>
        {tiene && (
          <Button variant="ghost" size="sm" onClick={quitar} disabled={ocupado}>
            Quitar
          </Button>
        )}
      </div>
    </div>
  );
}
