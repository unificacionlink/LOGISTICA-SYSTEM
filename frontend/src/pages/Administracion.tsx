import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Building2, Contact, Copy, RefreshCw, Search, UserPlus, Users } from "lucide-react";
import {
  asignarRoles,
  cambiarActivo,
  crearCeco,
  crearUsuario,
  editarUsuario,
  generarPasswordTemporal,
  obtenerAdmin,
  sincronizarRrhh,
} from "../api/admin";
import type { AdminDatos, CecoAdmin, UsuarioAdmin } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";
import { useConfirmar } from "../components/ui/Confirmacion";
import PestanaPadron from "../components/admin/PestanaPadron";
import { ETIQUETA_ROL } from "../components/admin/roles";
import CecosUsuario from "../components/admin/CecosUsuario";
import CecosAprobados from "../components/admin/CecosAprobados";

type Pestana = "usuarios" | "personal" | "cecos" | "rrhh";

const PESTANAS: { id: Pestana; label: string; icon: typeof Users }[] = [
  { id: "usuarios", label: "Usuarios", icon: Users },
  { id: "personal", label: "Personal", icon: Contact },
  { id: "cecos", label: "CECOs", icon: Building2 },
  { id: "rrhh", label: "RR.HH.", icon: RefreshCw },
];

const thClass = "px-5 py-3";

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

/** Estado de acceso en una palabra: lo primero que ADMIN necesita saber de un usuario. */
function EstadoAcceso({ u }: { u: UsuarioAdmin }) {
  if (!u.activo) return <Badge tone="gris">Inactivo</Badge>;
  if (!u.tieneAcceso) return <Badge tone="amarillo">Sin acceso</Badge>;
  if (u.debeCambiarPassword) return <Badge tone="azul">Por activar</Badge>;
  return <Badge tone="verde">Activo</Badge>;
}

export default function Administracion() {
  const [params, setParams] = useSearchParams();
  const tab = (PESTANAS.some((p) => p.id === params.get("tab")) ? params.get("tab") : "usuarios") as Pestana;
  const [datos, setDatos] = useState<AdminDatos | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);

  function recargar() {
    obtenerAdmin().then((res) => {
      if (res.ok && res.result) setDatos(res.result);
      else setError(res.message);
    });
  }

  useEffect(recargar, []);


  /** Aplica la respuesta de una escritura (siempre devuelve los datos completos). */
  function aplicar(res: { ok: boolean; result: AdminDatos | null; message: string }): boolean {
    if (res.ok && res.result) {
      setDatos(res.result);
      setError(null);
      setExito(res.message);
      return true;
    }
    setExito(null);
    setError(res.message);
    return false;
  }

  return (
    <div>
      <PageHeader title="Administración" />

      <div role="tablist" className="mb-4 flex flex-wrap gap-1 border-b border-slate-200">
        {PESTANAS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => {
              setParams({ tab: id }, { replace: true });
              setExito(null);
              setError(null);
            }}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="size-4" />
            {label}
          </button>
        ))}
      </div>

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      {!datos ? (
        !error && (
          <Card className="overflow-hidden">
            <SkeletonTable cols={5} />
          </Card>
        )
      ) : tab === "usuarios" ? (
        <PestanaUsuarios datos={datos} aplicar={aplicar} />
      ) : tab === "personal" ? (
        <PestanaPadron datos={datos} recargarAdmin={recargar} />
      ) : tab === "cecos" ? (
        <PestanaCecos datos={datos} aplicar={aplicar} />
      ) : (
        <PestanaRrhh datos={datos} aplicar={aplicar} />
      )}
    </div>
  );
}

type Aplicar = (res: { ok: boolean; result: AdminDatos | null; message: string }) => boolean;

// ---------------------------------------------------------------- Usuarios

function PestanaUsuarios({ datos, aplicar }: { datos: AdminDatos; aplicar: Aplicar }) {
  const [busqueda, setBusqueda] = useState("");
  const [empresa, setEmpresa] = useState("");
  const [abierto, setAbierto] = useState<string | null>(null);
  const [creando, setCreando] = useState(false);
  const hayAltaManual = datos.empresas.some((e) => e.altaManual);

  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    const empresaDeCeco = new Map(datos.cecos.map((c) => [c.id, c.empresaCode]));
    return datos.usuarios.filter(
      (u) =>
        (!empresa || u.empresaCode === empresa || u.cecoIds.some((id) => empresaDeCeco.get(id) === empresa)) &&
        (!q || u.nombre.toLowerCase().includes(q) || u.correo.toLowerCase().includes(q) || (u.dni ?? "").includes(q)),
    );
  }, [datos.usuarios, datos.cecos, busqueda, empresa]);

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <label className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por nombre, correo o DNI"
            className={`${inputClass} w-full pl-9`}
          />
        </label>
        <select value={empresa} onChange={(e) => setEmpresa(e.target.value)} className={inputClass} aria-label="Empresa">
          <option value="">Todas las empresas</option>
          {datos.empresas.map((e) => (
            <option key={e.id} value={e.code}>
              {e.code}
            </option>
          ))}
        </select>
        {hayAltaManual && !creando && (
          <Button variant="secondary" icon={<UserPlus className="size-4" />} onClick={() => setCreando(true)}>
            Alta manual
          </Button>
        )}
      </div>

      {creando && <NuevoUsuario datos={datos} aplicar={aplicar} cerrar={() => setCreando(false)} />}

      <Card className="overflow-hidden">
        {filtrados.length === 0 ? (
          <EmptyState icon={Users} title={datos.usuarios.length === 0 ? "Aún no hay usuarios" : "Sin resultados"} />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                <th className={thClass}>Usuario</th>
                <th className={`${thClass} col-sec`}>Empresa</th>
                <th className={thClass}>Roles</th>
                <th className={thClass}>Acceso</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtrados.map((u) => (
                <FilaUsuario
                  key={u.id}
                  u={u}
                  roles={datos.roles}
                  cecos={datos.cecos}
                  usuarios={datos.usuarios}
                  abierto={abierto === u.id}
                  alternar={() => setAbierto(abierto === u.id ? null : u.id)}
                  aplicar={aplicar}
                />
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

function FilaUsuario({
  u,
  roles,
  cecos,
  usuarios,
  abierto,
  alternar,
  aplicar,
}: {
  u: UsuarioAdmin;
  roles: string[];
  cecos: CecoAdmin[];
  usuarios: UsuarioAdmin[];
  abierto: boolean;
  alternar: () => void;
  aplicar: Aplicar;
}) {
  const { usuario: yo } = useAuth();
  const confirmar = useConfirmar();
  const [seleccion, setSeleccion] = useState<string[]>(u.roles);
  const [guardando, setGuardando] = useState<"roles" | "password" | "activo" | null>(null);
  const [editando, setEditando] = useState(false);
  const [temporal, setTemporal] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);
  const [errorFila, setErrorFila] = useState<string | null>(null);
  const cambiaronRoles = [...seleccion].sort().join() !== [...u.roles].sort().join();

  async function onGuardarRoles() {
    setGuardando("roles");
    try {
      aplicar(await asignarRoles(u.id, seleccion));
    } finally {
      setGuardando(null);
    }
  }

  async function onPassword() {
    if (
      u.tieneAcceso &&
      !(await confirmar({
        titulo: `¿Restablecer la contraseña de ${u.nombre}?`,
        mensaje: "Su contraseña actual deja de funcionar.",
        confirmar: "Restablecer",
      }))
    )
      return;
    setGuardando("password");
    setErrorFila(null);
    try {
      const res = await generarPasswordTemporal(u.id);
      if (res.ok && res.result) setTemporal(res.result.password);
      else setErrorFila(res.message);
    } finally {
      setGuardando(null);
    }
  }

  async function onActivo() {
    if (
      u.activo &&
      !(await confirmar({
        titulo: `¿Desactivar a ${u.nombre}?`,
        mensaje: "No podrá ingresar al sistema.",
        confirmar: "Desactivar",
        peligro: true,
      }))
    )
      return;
    setGuardando("activo");
    try {
      aplicar(await cambiarActivo(u.id, !u.activo));
    } finally {
      setGuardando(null);
    }
  }

  return (
    <>
      <tr
        onClick={alternar}
        aria-expanded={abierto}
        className={`cursor-pointer ${abierto ? "bg-slate-50" : "hover:bg-slate-50"}`}
      >
        <td className="col-flex px-5 py-3">
          <p className="text-sm text-slate-800">{u.nombre}</p>
          <p className="truncate text-xs text-slate-500">
            {u.correo}
            {u.fuente === "MANUAL" && <span className="text-slate-400"> · manual</span>}
          </p>
        </td>
        <td className="col-sec px-5 py-3 font-mono text-xs text-slate-600">{u.empresaCode ?? "—"}</td>
        <td className="px-5 py-3 text-xs text-slate-600">
          {u.roles.length ? u.roles.map((r) => ETIQUETA_ROL[r] ?? r).join(", ") : <span className="text-amber-700">Sin rol</span>}
          {u.roles.includes("SOLICITANTE") && u.cecoIds.length === 0 && (
            <span className="block text-amber-700">Sin CECO</span>
          )}
        </td>
        <td className="px-5 py-3">
          <EstadoAcceso u={u} />
        </td>
      </tr>
      {abierto && (
        <tr className="bg-slate-50">
          <td colSpan={4} className="px-5 pb-4 pt-1">
            <div className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 md:grid-cols-[1fr_auto]">
              <div>
                <div className="rotulo mb-2">Roles</div>
                <div className="flex flex-wrap gap-x-4 gap-y-2">
                  {roles.map((r) => (
                    <label key={r} className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        checked={seleccion.includes(r)}
                        disabled={r === "ADMIN" && u.id === yo?.id}
                        onChange={(e) =>
                          setSeleccion((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))
                        }
                      />
                      {ETIQUETA_ROL[r] ?? r}
                    </label>
                  ))}
                </div>
                {cambiaronRoles && (
                  <Button size="sm" className="mt-3" loading={guardando === "roles"} onClick={onGuardarRoles}>
                    Guardar roles
                  </Button>
                )}
                <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs sm:grid-cols-3">
                  <div>
                    <dt className="rotulo">DNI</dt>
                    <dd className="font-mono text-slate-700">{u.dni ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="rotulo">Cargo</dt>
                    <dd className="text-slate-700">{u.cargo ?? "—"}</dd>
                  </div>
                </dl>
                <CecosUsuario u={u} cecos={cecos} aplicar={aplicar} />
                {(u.roles.includes("APROBADOR") || u.roles.includes("GERENCIA")) && (
                  // key: si otro usuario toma uno de sus CECOs, la seleccion se rehace.
                  <CecosAprobados
                    key={cecos.filter((c) => c.aprobadorId === u.id).map((c) => c.id).join()}
                    u={u}
                    cecos={cecos}
                    usuarios={usuarios}
                    aplicar={aplicar}
                  />
                )}
              </div>
              <div className="flex flex-col items-start gap-2 md:items-end">
                {u.activo && (
                  <Button size="sm" variant="secondary" loading={guardando === "password"} onClick={onPassword}>
                    {u.tieneAcceso ? "Restablecer contraseña" : "Dar acceso"}
                  </Button>
                )}
                {u.fuente === "MANUAL" && !editando && (
                  <Button size="sm" variant="ghost" onClick={() => setEditando(true)}>
                    Editar datos
                  </Button>
                )}
                {u.fuente === "MANUAL" && u.id !== yo?.id && (
                  <Button size="sm" variant="ghost" loading={guardando === "activo"} onClick={onActivo}>
                    {u.activo ? "Desactivar" : "Activar"}
                  </Button>
                )}
              </div>
              {editando && (
                <EditarDatos
                  u={u}
                  aplicar={aplicar}
                  cerrar={() => setEditando(false)}
                />
              )}
              {errorFila && <Alert tone="danger" className="md:col-span-2">{errorFila}</Alert>}
              {temporal && (
                <div className="flex flex-wrap items-center gap-3 rounded-md border border-brand-200 bg-brand-50 px-3 py-2.5 text-sm md:col-span-2">
                  <span className="text-slate-600">Contraseña temporal (se muestra una sola vez):</span>
                  <code className="rounded bg-white px-2 py-0.5 font-mono text-base font-semibold tracking-wider text-slate-900">
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
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

/** Datos de un usuario MANUAL (DEC-L028); los de RR.HH. se corrigen alla. */
function EditarDatos({
  u,
  aplicar,
  cerrar,
}: {
  u: UsuarioAdmin;
  aplicar: Aplicar;
  cerrar: () => void;
}) {
  const [nombre, setNombre] = useState(u.nombre);
  const [correo, setCorreo] = useState(u.correo);
  const [dni, setDni] = useState(u.dni ?? "");
  const [cargo, setCargo] = useState(u.cargo ?? "");
  const [guardando, setGuardando] = useState(false);

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    try {
      const ok = aplicar(
        await editarUsuario(u.id, {
          nombre,
          correo,
          dni: dni || undefined,
          cargo: cargo || undefined,
        }),
      );
      if (ok) cerrar();
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={onGuardar} className="grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-3 md:col-span-2">
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
      <div className="flex items-end gap-2">
        <Button type="submit" size="sm" loading={guardando}>
          Guardar
        </Button>
        <Button size="sm" variant="ghost" disabled={guardando} onClick={cerrar}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}

function NuevoUsuario({ datos, aplicar, cerrar }: { datos: AdminDatos; aplicar: Aplicar; cerrar: () => void }) {
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

// ---------------------------------------------------------------- CECOs

function PestanaCecos({ datos, aplicar }: { datos: AdminDatos; aplicar: Aplicar }) {
  const empresasManuales = datos.empresas.filter((e) => e.altaManual);
  const [creando, setCreando] = useState(false);
  const [empresa, setEmpresa] = useState(empresasManuales[0]?.code ?? "");
  const [code, setCode] = useState("");
  const [nombre, setNombre] = useState("");
  const [guardando, setGuardando] = useState(false);
  // Solo vigentes: los CECO que RR.HH. ya no tiene (ADM/OPS de relleno) quedan inactivos por historial.
  const activos = useMemo(() => datos.cecos.filter((c) => c.activo), [datos.cecos]);
  // Aprobador del CECO (DEC-L030): se asigna desde la ficha del usuario (DEC-L037).
  const nombres = useMemo(() => new Map(datos.usuarios.map((u) => [u.id, u.nombre])), [datos.usuarios]);

  async function onCrear(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    try {
      if (aplicar(await crearCeco(empresa, code, nombre))) {
        setCode("");
        setNombre("");
        setCreando(false);
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <>
      {empresasManuales.length > 0 && (
        <div className="mb-3">
          {!creando ? (
            <Button variant="secondary" onClick={() => setCreando(true)}>
              Nuevo CECO
            </Button>
          ) : (
            <Card>
              <CardBody>
                <form onSubmit={onCrear} className="flex flex-wrap items-end gap-3">
                  <Field label="Empresa" required>
                    <select value={empresa} onChange={(e) => setEmpresa(e.target.value)} className={inputClass}>
                      {empresasManuales.map((e) => (
                        <option key={e.id} value={e.code}>
                          {e.code}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Código" required>
                    <input required value={code} onChange={(e) => setCode(e.target.value)} className={`${inputClass} w-28`} />
                  </Field>
                  <Field label="Nombre" required>
                    <input required value={nombre} onChange={(e) => setNombre(e.target.value)} className={`${inputClass} w-64`} />
                  </Field>
                  <Button type="submit" loading={guardando}>
                    Crear
                  </Button>
                  <Button type="button" variant="ghost" onClick={() => setCreando(false)}>
                    Cancelar
                  </Button>
                </form>
              </CardBody>
            </Card>
          )}
        </div>
      )}
      <Card className="overflow-hidden">
        {activos.length === 0 ? (
          <EmptyState icon={Building2} title="Aún no hay CECOs" />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                <th className={thClass}>Empresa</th>
                <th className={thClass}>Código</th>
                <th className={thClass}>Nombre</th>
                <th className={thClass}>Aprobador</th>
                <th className={thClass}>Origen</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {activos.map((c) => {
                const manual = datos.empresas.find((e) => e.code === c.empresaCode)?.altaManual;
                return (
                  <tr key={c.id}>
                    <td className="px-5 py-2.5 font-mono text-xs">{c.empresaCode}</td>
                    <td className="px-5 py-2.5 font-mono text-xs">{c.code}</td>
                    <td className="col-flex px-5 py-2.5 text-sm">{c.nombre}</td>
                    <td className="px-5 py-2.5 text-xs text-slate-600">
                      {c.aprobadorId ? (nombres.get(c.aprobadorId) ?? "—") : <span className="text-slate-400">Sin asignar</span>}
                    </td>
                    <td className="px-5 py-2.5 text-xs text-slate-500">{manual ? "Manual" : "RR.HH."}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

// ---------------------------------------------------------------- RR.HH.

function PestanaRrhh({ datos, aplicar }: { datos: AdminDatos; aplicar: Aplicar }) {
  const [sincronizando, setSincronizando] = useState(false);
  const r = datos.ultimoResultado;

  async function onSincronizar() {
    setSincronizando(true);
    try {
      aplicar(await sincronizarRrhh());
    } finally {
      setSincronizando(false);
    }
  }

  const cubiertas = datos.empresas.filter((e) => !e.altaManual).map((e) => e.code);
  const manuales = datos.empresas.filter((e) => e.altaManual).map((e) => e.code);

  return (
    <Card>
      <CardBody className="flex flex-col gap-5">
        {!datos.syncConfigurado && (
          <Alert tone="info">
            La sincronización no está configurada en el servidor (RRHH_API_URL y RRHH_SYNC_SERVICE_TOKEN).
          </Alert>
        )}
        <dl className="grid gap-4 text-sm sm:grid-cols-3">
          <div>
            <dt className="rotulo">Última sincronización</dt>
            <dd className="mt-1 text-slate-800">{fmtFechaHora(datos.ultimaSincronizacion)}</dd>
          </div>
          <div>
            <dt className="rotulo">Desde RR.HH.</dt>
            <dd className="mt-1 font-mono text-xs text-slate-700">{cubiertas.join(" · ") || "—"}</dd>
          </div>
          <div>
            <dt className="rotulo">Alta manual</dt>
            <dd className="mt-1 font-mono text-xs text-slate-700">{manuales.join(" · ") || "—"}</dd>
          </div>
        </dl>
        <Button
          className="self-start"
          loading={sincronizando}
          disabled={!datos.syncConfigurado}
          icon={<RefreshCw className="size-4" />}
          onClick={onSincronizar}
        >
          Sincronizar ahora
        </Button>
        {sincronizando && <Skeleton className="h-16 w-full" />}
        {r && !sincronizando && (
          <div className="rounded-lg border border-slate-200 p-4">
            <div className="rotulo mb-2">Resultado · {fmtFechaHora(r.ejecutadoEn)}</div>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
              <span>
                <b className="text-slate-900">{r.recibidos}</b> personas recibidas
              </span>
              <span>
                <b className="text-emerald-700">{r.creados}</b> nuevas
              </span>
              <span>
                <b className="text-slate-900">{r.actualizados}</b> con cambios
              </span>
              <span>
                <b className={r.desactivados ? "text-rose-700" : "text-slate-900"}>{r.desactivados}</b> ceses
              </span>
              <span>
                <b className="text-slate-900">{r.cecosCreados}</b> CECOs nuevos
              </span>
              {r.cecosDesactivados > 0 && (
                <span>
                  <b className="text-rose-700">{r.cecosDesactivados}</b> CECOs desactivados
                </span>
              )}
            </div>
            {r.avisos.length > 0 && (
              <ul className="mt-3 flex flex-col gap-1 text-xs text-amber-800">
                {r.avisos.map((a) => (
                  <li key={a}>{a}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
