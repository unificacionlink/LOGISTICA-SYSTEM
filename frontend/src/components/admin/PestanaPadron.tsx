import { useEffect, useMemo, useState } from "react";
import { Copy, Search, UserCheck, UserPlus, Users } from "lucide-react";
import { cambiarActivoPersona, darAcceso, listarPersonal } from "../../api/admin";
import type { AdminDatos, CecoAdmin, PersonalAdmin } from "../../api/types";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";
import EmptyState from "../ui/EmptyState";
import Field, { inputClass } from "../ui/Field";
import MenuAcciones, { type AccionMenu } from "../ui/MenuAcciones";
import { SkeletonTable } from "../ui/Skeleton";
import CargaPersonalExcel from "./CargaPersonalExcel";
import FormPersona from "./FormPersona";
import { ETIQUETA_ROL } from "./roles";

type Filtro = "activos" | "sin" | "con" | "inactivos";

/** Fila desplegada: dar acceso o editar sus datos. */
type Abierto = { id: string; modo: "acceso" | "editar" } | null;

const thClass = "px-5 py-3";

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE") : "";
}

/** A quien cesa RR.HH. lo reactiva el sync; al resto, solo un ADMIN (DEC-L064). */
function puedeReactivar(p: PersonalAdmin): boolean {
  return p.fuente === "MANUAL" || p.inactivadoPor !== null;
}

/**
 * Padron de personal por empresa (DEC-L041): cada empresa con su propia lista.
 * TDEM llega de RR.HH.; GOYDEL y METRIN se agregan aqui o por Excel. Nadie se
 * elimina: se inactiva con fecha y solo ADMIN lo reactiva (DEC-L064).
 */
export default function PestanaPadron({ datos, recargarAdmin }: { datos: AdminDatos; recargarAdmin: () => void }) {
  const confirmar = useConfirmar();
  const [personal, setPersonal] = useState<PersonalAdmin[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // "" = aun no elegida: se muestra la empresa con mas personal (TDEM, la de RR.HH.).
  const [elegida, setElegida] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("activos");
  const [abierto, setAbierto] = useState<Abierto>(null);
  const [agregando, setAgregando] = useState(false);
  const [verExcel, setVerExcel] = useState(false);

  function cargar() {
    listarPersonal().then((res) => {
      if (res.ok && res.result) setPersonal(res.result);
      else setError(res.message);
    });
  }

  useEffect(cargar, []);

  function aplicarLista(lista: PersonalAdmin[]) {
    setPersonal(lista);
    recargarAdmin();
  }

  async function cambiarEstado(p: PersonalAdmin, activo: boolean) {
    const ok = await confirmar(
      activo
        ? { titulo: `¿Reactivar a ${p.nombre}?`, confirmar: "Reactivar" }
        : {
            titulo: `¿Inactivar a ${p.nombre}?`,
            mensaje: p.userId ? "También pierde el acceso a la plataforma." : undefined,
            confirmar: "Inactivar",
            peligro: true,
          },
    );
    if (!ok) return;
    setError(null);
    const res = await cambiarActivoPersona(p.id, activo);
    if (res.ok && res.result) aplicarLista(res.result);
    else setError(res.message);
  }

  // Los chips cuentan solo al personal activo.
  const conteo = useMemo(() => {
    const m = new Map<string, number>();
    personal?.forEach((p) => p.activo && m.set(p.empresaCode, (m.get(p.empresaCode) ?? 0) + 1));
    return m;
  }, [personal]);
  const empresa =
    elegida ||
    datos.empresas.toSorted((a, b) => (conteo.get(b.code) ?? 0) - (conteo.get(a.code) ?? 0))[0]?.code ||
    "";
  const empresasManuales = datos.empresas.filter((e) => e.altaManual).map((e) => e.code);
  const esManual = empresasManuales.includes(empresa);

  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return (personal ?? []).filter(
      (p) =>
        p.empresaCode === empresa &&
        // La fila abierta se queda aunque cambie de grupo: muestra la contrasena recien creada.
        (p.id === abierto?.id || coincide(p, filtro)) &&
        (!q || p.nombre.toLowerCase().includes(q) || p.dni.includes(q) || (p.correo ?? "").toLowerCase().includes(q)),
    );
  }, [personal, empresa, filtro, busqueda, abierto]);

  return (
    <>
      <div role="tablist" className="mb-3 flex flex-wrap gap-1.5">
        {datos.empresas.map((e) => (
          <button
            key={e.id}
            type="button"
            role="tab"
            aria-selected={empresa === e.code}
            onClick={() => {
              setElegida(e.code);
              setAbierto(null);
              setAgregando(false);
              setVerExcel(false);
            }}
            className={`rounded-full border px-3 py-1 font-mono text-xs transition-colors ${
              empresa === e.code
                ? "border-brand-600 bg-brand-50 text-brand-700"
                : "border-slate-200 text-slate-600 hover:border-slate-300"
            }`}
          >
            {e.code}
            {personal && <span className="ml-1.5 text-slate-400">{conteo.get(e.code) ?? 0}</span>}
          </button>
        ))}
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2">
        <label className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por nombre, DNI o correo"
            className={`${inputClass} w-full pl-9`}
          />
        </label>
        <select
          value={filtro}
          onChange={(e) => setFiltro(e.target.value as Filtro)}
          className={inputClass}
          aria-label="Estado"
        >
          <option value="activos">Activos</option>
          <option value="sin">Activos sin cuenta</option>
          <option value="con">Activos con cuenta</option>
          <option value="inactivos">Inactivos</option>
        </select>
        {esManual && (
          <>
            <Button
              icon={<UserPlus className="size-4" />}
              onClick={() => {
                setAgregando((v) => !v);
                setVerExcel(false);
              }}
            >
              Agregar persona
            </Button>
            <MenuAcciones
              acciones={[
                {
                  label: verExcel ? "Ocultar carga por Excel" : "Carga por Excel",
                  onClick: () => {
                    setVerExcel((v) => !v);
                    setAgregando(false);
                  },
                },
              ]}
            />
          </>
        )}
      </div>

      {esManual && agregando && (
        <Card className="mb-3">
          <CardBody>
            <FormPersona
              key={empresa}
              empresaCode={empresa}
              persona={null}
              cecos={datos.cecos}
              alGuardar={aplicarLista}
              cerrar={() => setAgregando(false)}
            />
          </CardBody>
        </Card>
      )}

      {esManual && verExcel && (
        <CargaPersonalExcel
          empresas={empresasManuales}
          recargar={() => {
            cargar();
            recargarAdmin();
          }}
        />
      )}

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <Card className="overflow-hidden">
        {!personal ? (
          !error && <SkeletonTable cols={4} />
        ) : filtrados.length === 0 ? (
          <EmptyState
            icon={Users}
            title={
              filtro === "inactivos"
                ? `Sin personal inactivo en ${empresa}`
                : conteo.get(empresa)
                  ? "Sin resultados"
                  : `Aún no hay personal de ${empresa}`
            }
          />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 cabecera-tabla">
                <th className={thClass}>Persona</th>
                <th className={`${thClass} col-sec`}>CECO</th>
                <th className={`${thClass} text-right`}>{filtro === "inactivos" ? "Estado" : "Cuenta"}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtrados.map((p) => (
                <FilaPersona
                  key={p.id}
                  p={p}
                  roles={datos.roles}
                  cecos={datos.cecos}
                  modo={abierto?.id === p.id ? abierto.modo : null}
                  abrir={(modo) => setAbierto({ id: p.id, modo })}
                  cerrar={() => setAbierto(null)}
                  cambiarEstado={(activo) => cambiarEstado(p, activo)}
                  alGuardar={aplicarLista}
                />
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

function coincide(p: PersonalAdmin, filtro: Filtro): boolean {
  if (filtro === "inactivos") return !p.activo;
  if (!p.activo) return false;
  return filtro === "activos" || (filtro === "con") === (p.userId !== null);
}

function FilaPersona({
  p,
  roles,
  cecos,
  modo,
  abrir,
  cerrar,
  cambiarEstado,
  alGuardar,
}: {
  p: PersonalAdmin;
  roles: string[];
  cecos: CecoAdmin[];
  modo: "acceso" | "editar" | null;
  abrir: (modo: "acceso" | "editar") => void;
  cerrar: () => void;
  cambiarEstado: (activo: boolean) => void;
  alGuardar: (lista: PersonalAdmin[]) => void;
}) {
  const acciones: AccionMenu[] = p.activo
    ? [
        ...(p.fuente === "MANUAL" ? [{ label: "Editar datos", onClick: () => abrir("editar") }] : []),
        { label: "Inactivar", onClick: () => cambiarEstado(false), peligro: true },
      ]
    : puedeReactivar(p)
      ? [{ label: "Reactivar", onClick: () => cambiarEstado(true) }]
      : [];
  const origenInactivo = p.inactivadoPor ? `Inactivado por ${p.inactivadoPor}` : "Cese en RR.HH.";

  return (
    <>
      <tr className={modo ? "bg-slate-50" : "hover:bg-slate-50"}>
        <td className="col-flex px-5 py-3" title={p.nombre}>
          <p className={`text-sm ${p.activo ? "text-slate-800" : "text-slate-500"}`}>{p.nombre}</p>
          <p className="truncate text-xs text-slate-500">
            <span className="font-mono">{p.dni}</span>
            {p.cargo && ` · ${p.cargo}`}
          </p>
        </td>
        <td className="col-sec px-5 py-3 text-xs text-slate-600" title={p.cecoNombre ?? undefined}>
          {p.cecoCode ? (
            <>
              <span className="font-mono">{p.cecoCode}</span> <span className="text-slate-400">{p.cecoNombre}</span>
            </>
          ) : (
            "—"
          )}
        </td>
        <td className="whitespace-nowrap px-5 py-3">
          <div className="flex items-center justify-end gap-2">
            {!p.activo ? (
              <span className="inline-flex flex-col items-end gap-0.5" title={origenInactivo}>
                <Badge tone="gris">Inactivo desde {fecha(p.inactivadoEn)}</Badge>
                <span className="text-xs text-slate-400">{origenInactivo}</span>
              </span>
            ) : p.userId ? (
              <span className="inline-flex flex-col items-end gap-0.5">
                <Badge tone="verde">
                  <UserCheck className="size-3" />
                  Con cuenta
                </Badge>
                {p.correo && <span className="text-xs text-slate-400">{p.correo}</span>}
              </span>
            ) : (
              !modo && (
                <Button size="sm" variant="secondary" onClick={() => abrir("acceso")}>
                  Dar acceso
                </Button>
              )
            )}
            <MenuAcciones acciones={acciones} etiqueta={`Acciones de ${p.nombre}`} />
          </div>
        </td>
      </tr>
      {modo && (
        <tr className="bg-slate-50">
          <td colSpan={3} className="px-5 pb-4 pt-1">
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              {modo === "editar" ? (
                <FormPersona
                  empresaCode={p.empresaCode}
                  persona={p}
                  cecos={cecos}
                  alGuardar={alGuardar}
                  cerrar={cerrar}
                />
              ) : (
                <DarAcceso p={p} roles={roles} cerrar={cerrar} alCrear={alGuardar} />
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function DarAcceso({
  p,
  roles,
  cerrar,
  alCrear,
}: {
  p: PersonalAdmin;
  roles: string[];
  cerrar: () => void;
  alCrear: (lista: PersonalAdmin[]) => void;
}) {
  const [correo, setCorreo] = useState(p.correo ?? "");
  const [seleccion, setSeleccion] = useState<string[]>(["SOLICITANTE"]);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [temporal, setTemporal] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);

  async function onCrear(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      const res = await darAcceso(p.id, correo, seleccion);
      if (res.ok && res.result) {
        if (res.result.acceso) setTemporal(res.result.acceso.password);
        else setAviso(res.message);
        alCrear(res.result.personal);
      } else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  if (temporal || aviso) {
    return (
      <div className="flex flex-wrap items-center gap-3 text-sm">
        {temporal ? (
          <>
            <span className="text-slate-600">Contraseña temporal (se muestra una sola vez):</span>
            <code className="rounded bg-brand-50 px-2 py-0.5 font-mono text-base font-semibold tracking-wider text-slate-900">
              {temporal}
            </code>
            <button
              type="button"
              onClick={() => {
                navigator.clipboard?.writeText(temporal);
                setCopiado(true);
              }}
              className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:text-brand-800"
            >
              <Copy className="size-3.5" />
              {copiado ? "Copiada" : "Copiar"}
            </button>
          </>
        ) : (
          <span className="text-slate-600">{aviso}</span>
        )}
        <Button size="sm" variant="ghost" className="ml-auto" onClick={cerrar}>
          Cerrar
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onCrear} className="grid gap-4 sm:grid-cols-[minmax(0,18rem)_1fr]">
      <Field label="Correo de ingreso" required>
        <input
          required
          type="email"
          autoFocus
          value={correo}
          onChange={(e) => setCorreo(e.target.value)}
          className={`${inputClass} w-full`}
        />
      </Field>
      <div>
        <div className="rotulo mb-2">Roles</div>
        <div className="flex flex-wrap gap-x-4 gap-y-2">
          {roles.map((r) => (
            <label key={r} className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={seleccion.includes(r)}
                onChange={(e) => setSeleccion((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))}
              />
              {ETIQUETA_ROL[r] ?? r}
            </label>
          ))}
        </div>
      </div>
      {error && <Alert tone="danger" className="sm:col-span-2">{error}</Alert>}
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" size="sm" loading={guardando} disabled={seleccion.length === 0}>
          Crear cuenta
        </Button>
        <Button type="button" size="sm" variant="ghost" disabled={guardando} onClick={cerrar}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
