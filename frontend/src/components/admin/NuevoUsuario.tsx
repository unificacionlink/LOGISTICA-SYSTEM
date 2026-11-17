import { useState } from "react";
import { crearUsuario } from "../../api/admin";
import type { AdminDatos } from "../../api/types";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import type { Aplicar } from "./PanelUsuario";
import { ETIQUETA_ROL } from "./roles";

/** Alta manual de un usuario, solo para empresas sin padron en RR.HH. (DEC-L002). */
export default function NuevoUsuario({ datos, aplicar, cerrar }: { datos: AdminDatos; aplicar: Aplicar; cerrar: () => void }) {
  const empresasManuales = datos.empresas.filter((e) => e.altaManual);
  const [nombre, setNombre] = useState("");
  const [correo, setCorreo] = useState("");
  const [dni, setDni] = useState("");
  const [cargo, setCargo] = useState("");
  const [empresa, setEmpresa] = useState(empresasManuales[0]?.code ?? "");
  const [cecoId, setCecoId] = useState("");
  const [roles, setRoles] = useState<string[]>(["SOLICITANTE"]);
  const [guardando, setGuardando] = useState(false);
  const cecos = datos.cecos.filter((c) => c.empresaCode === empresa && c.activo);

  async function onCrear(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    try {
      const ok = aplicar(
        await crearUsuario({
          nombre,
          correo,
          dni: dni || undefined,
          cargo: cargo || undefined,
          empresaCode: empresa,
          cecoId: cecoId || undefined,
          roles,
        }),
      );
      if (ok) cerrar();
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Card className="mb-3">
      <CardBody>
        <form onSubmit={onCrear} className="grid gap-4 sm:grid-cols-3">
          <Field label="Nombre" required>
            <input required value={nombre} onChange={(e) => setNombre(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <Field label="Correo" required>
            <input
              required
              type="email"
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              className={`${inputClass} w-full`}
            />
          </Field>
          <Field label="DNI">
            <input value={dni} onChange={(e) => setDni(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <Field label="Cargo">
            <input value={cargo} onChange={(e) => setCargo(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <Field label="Empresa" required>
            <select
              value={empresa}
              onChange={(e) => {
                setEmpresa(e.target.value);
                setCecoId("");
              }}
              className={`${inputClass} w-full`}
            >
              {empresasManuales.map((e) => (
                <option key={e.id} value={e.code}>
                  {e.code}
                </option>
              ))}
            </select>
          </Field>
          <Field label="CECO">
            <select value={cecoId} onChange={(e) => setCecoId(e.target.value)} className={`${inputClass} w-full`}>
              <option value="">{cecos.length ? "Sin CECO" : "La empresa no tiene CECOs"}</option>
              {cecos.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} · {c.nombre}
                </option>
              ))}
            </select>
          </Field>
          <div>
            <div className="rotulo mb-2">Roles</div>
            <div className="flex flex-wrap gap-x-3 gap-y-1.5">
              {datos.roles.map((r) => (
                <label key={r} className="flex items-center gap-1.5 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    checked={roles.includes(r)}
                    onChange={(e) => setRoles((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))}
                  />
                  {ETIQUETA_ROL[r] ?? r}
                </label>
              ))}
            </div>
          </div>
          <div className="flex gap-2 sm:col-span-3">
            <Button type="submit" loading={guardando} disabled={roles.length === 0}>
              Crear usuario
            </Button>
            <Button type="button" variant="ghost" onClick={cerrar}>
              Cancelar
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}
