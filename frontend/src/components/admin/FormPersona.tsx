import { useState } from "react";
import { crearPersona, editarPersona } from "../../api/admin";
import type { CecoAdmin, PersonalAdmin } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";

/**
 * Alta o edicion de una persona del padron (DEC-L064), solo en empresas sin
 * padron en RR.HH. Al editar, la empresa y el DNI no cambian.
 */
export default function FormPersona({
  empresaCode,
  persona,
  cecos,
  alGuardar,
  cerrar,
}: {
  empresaCode: string;
  /** null = agregar. */
  persona: PersonalAdmin | null;
  cecos: CecoAdmin[];
  alGuardar: (lista: PersonalAdmin[]) => void;
  cerrar: () => void;
}) {
  const [dni, setDni] = useState(persona?.dni ?? "");
  const [nombre, setNombre] = useState(persona?.nombre ?? "");
  const [correo, setCorreo] = useState(persona?.correo ?? "");
  const [cargo, setCargo] = useState(persona?.cargo ?? "");
  const [cecoId, setCecoId] = useState(persona?.cecoId ?? "");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Uno inactivo solo si ya era el suyo.
  const opciones = cecos.filter((c) => c.empresaCode === empresaCode && (c.activo || c.id === persona?.cecoId));

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      const datos = { nombre, correo: correo || undefined, cargo: cargo || undefined, cecoId: cecoId || undefined };
      const res = persona
        ? await editarPersona(persona.id, datos)
        : await crearPersona({ ...datos, empresaCode, dni });
      if (res.ok && res.result) {
        alGuardar(res.result);
        cerrar();
      } else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={onGuardar} className="grid gap-4 sm:grid-cols-3">
      {!persona && (
        <Field label="DNI" required>
          <input
            required
            autoFocus
            value={dni}
            onChange={(e) => setDni(e.target.value)}
            maxLength={12}
            className={`${inputClass} w-full font-mono`}
          />
        </Field>
      )}
      <Field label="Nombre completo" required>
        <input
          required
          autoFocus={!!persona}
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          maxLength={180}
          className={`${inputClass} w-full`}
        />
      </Field>
      <Field label="Correo">
        <input
          type="email"
          value={correo}
          onChange={(e) => setCorreo(e.target.value)}
          maxLength={180}
          className={`${inputClass} w-full`}
        />
      </Field>
      <Field label="Cargo">
        <input value={cargo} onChange={(e) => setCargo(e.target.value)} maxLength={150} className={`${inputClass} w-full`} />
      </Field>
      <Field label="CECO">
        <select value={cecoId} onChange={(e) => setCecoId(e.target.value)} className={`${inputClass} w-full`}>
          <option value="">{opciones.length ? "Sin CECO" : "La empresa no tiene CECOs"}</option>
          {opciones.map((c) => (
            <option key={c.id} value={c.id}>
              {c.code} · {c.nombre}
            </option>
          ))}
        </select>
      </Field>
      {error && (
        <Alert tone="danger" className="sm:col-span-3">
          {error}
        </Alert>
      )}
      <div className="flex gap-2 sm:col-span-3">
        <Button type="submit" size="sm" loading={guardando}>
          {persona ? "Guardar cambios" : "Agregar persona"}
        </Button>
        <Button type="button" size="sm" variant="ghost" disabled={guardando} onClick={cerrar}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
