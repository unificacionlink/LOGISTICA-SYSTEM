import { useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Copy, X } from "lucide-react";
import { asignarRoles, cambiarActivo, editarUsuario, generarPasswordTemporal } from "../../api/admin";
import type { AdminDatos, CecoAdmin, UsuarioAdmin } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { EMPRESAS } from "../empresas";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { useConfirmar } from "../ui/Confirmacion";
import Field, { inputClass } from "../ui/Field";
import CecosAprobados from "./CecosAprobados";
import CecosUsuario from "./CecosUsuario";
import { ETIQUETA_ROL } from "./roles";

export type Aplicar = (res: { ok: boolean; result: AdminDatos | null; message: string }) => boolean;

/** Estado de acceso en una palabra: lo primero que ADMIN necesita saber de un usuario. */
export function EstadoAcceso({ u }: { u: UsuarioAdmin }) {
  if (!u.activo) return <Badge tone="gris">Inactivo</Badge>;
  if (!u.tieneAcceso) return <Badge tone="amarillo">Sin acceso</Badge>;
  if (u.debeCambiarPassword) return <Badge tone="azul">Por activar</Badge>;
  return <Badge tone="verde">Activo</Badge>;
}

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="border-b border-slate-100 px-5 py-4 last:border-b-0">
      <h3 className="rotulo mb-2">{titulo}</h3>
      {children}
    </section>
  );
}

/**
 * Ficha de un usuario en panel lateral: roles, CECOs para pedir, CECOs que
 * aprueba, acceso y datos, todo en un solo lugar (Administracion > Usuarios).
 */
export default function PanelUsuario({
  u,
  roles,
  cecos,
  usuarios,
  aplicar: aplicarPagina,
  onCerrar,
}: {
  u: UsuarioAdmin;
  roles: string[];
  cecos: CecoAdmin[];
  usuarios: UsuarioAdmin[];
  aplicar: Aplicar;
  onCerrar: () => void;
}) {
  const { usuario: yo } = useAuth();
  const confirmar = useConfirmar();
  const [seleccion, setSeleccion] = useState<string[]>(u.roles);
  const [guardando, setGuardando] = useState<"roles" | "password" | "activo" | null>(null);
  const [editando, setEditando] = useState(false);
  const [temporal, setTemporal] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  // El resultado se muestra aqui: los avisos de la pagina quedan tapados por el panel.
  const aplicar: Aplicar = (res) => {
    const ok = aplicarPagina(res);
    setError(ok ? null : res.message);
    setAviso(ok ? res.message : null);
    return ok;
  };
  const cambiaronRoles = seleccion.toSorted().join() !== u.roles.toSorted().join();
  const aprueba = u.roles.includes("APROBADOR") || u.roles.includes("GERENCIA");
  const empresa = EMPRESAS.find((e) => e.code === u.empresaCode);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar]);

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
    setError(null);
    setAviso(null);
    try {
      const res = await generarPasswordTemporal(u.id);
      if (res.ok && res.result) setTemporal(res.result.password);
      else setError(res.message);
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

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="panel-usuario-titulo"
        className="flex h-full w-full max-w-xl flex-col border-l border-slate-200 bg-white shadow-flotante"
      >
        <header className="flex items-start gap-3 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-1.5 text-xs text-slate-500">
              <span className={`size-1.5 rounded-full ${empresa?.marca ?? "bg-slate-400"}`} aria-hidden="true" />
              {empresa?.label ?? u.empresaCode ?? "Sin empresa"}
              <span className="text-slate-300">·</span>
              {u.fuente === "MANUAL" ? "Alta manual" : "RR.HH."}
            </p>
            <h2 id="panel-usuario-titulo" className="mt-1 truncate text-base font-semibold text-slate-900">
              {u.nombre}
            </h2>
            <p className="mt-0.5 flex items-center gap-2 text-sm text-slate-500">
              <span className="truncate">{u.correo}</span>
              <EstadoAcceso u={u} />
            </p>
          </div>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto">
          {(error || aviso) && (
            <div className="px-5 pt-4">
              <Alert tone={error ? "danger" : "good"}>{error ?? aviso}</Alert>
            </div>
          )}

          <Seccion titulo="Roles">
            <div className="flex flex-wrap gap-1.5">
              {roles.map((r) => {
                const activo = seleccion.includes(r);
                const bloqueado = r === "ADMIN" && u.id === yo?.id;
                return (
                  <button
                    key={r}
                    type="button"
                    aria-pressed={activo}
                    disabled={bloqueado}
                    title={bloqueado ? "No puedes quitarte el rol de administrador" : undefined}
                    onClick={() => setSeleccion((prev) => (activo ? prev.filter((x) => x !== r) : [...prev, r]))}
                    className={`rounded-full border px-3 py-1 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                      activo
                        ? "border-brand-600 bg-brand-50 font-medium text-brand-800"
                        : "border-slate-200 text-slate-600 hover:border-slate-300"
                    }`}
                  >
                    {ETIQUETA_ROL[r] ?? r}
                  </button>
                );
              })}
            </div>
            {cambiaronRoles && (
              <div className="mt-3 flex gap-2">
                <Button size="sm" loading={guardando === "roles"} onClick={onGuardarRoles}>
                  Guardar roles
                </Button>
                <Button size="sm" variant="ghost" disabled={guardando === "roles"} onClick={() => setSeleccion(u.roles)}>
                  Descartar
                </Button>
              </div>
            )}
          </Seccion>

          <Seccion titulo="CECOs">
            {/* key: tras guardar, la seleccion se rehace con lo que devolvio el servidor. */}
            <CecosUsuario key={u.cecoIds.join()} u={u} cecos={cecos} aplicar={aplicar} />
            {aprueba && (
              <CecosAprobados
                key={cecos.filter((c) => c.aprobadorId === u.id).map((c) => c.id).join()}
                u={u}
                cecos={cecos}
                usuarios={usuarios}
                aplicar={aplicar}
              />
            )}
          </Seccion>

          <Seccion titulo="Acceso">
            <div className="flex flex-wrap gap-2">
              {u.activo && (
                <Button size="sm" variant="secondary" loading={guardando === "password"} onClick={onPassword}>
                  {u.tieneAcceso ? "Restablecer contraseña" : "Dar acceso"}
                </Button>
              )}
              {u.fuente === "MANUAL" && u.id !== yo?.id && (
                <Button size="sm" variant="ghost" loading={guardando === "activo"} onClick={onActivo}>
                  {u.activo ? "Desactivar" : "Activar"}
                </Button>
              )}
            </div>
            {u.fuente !== "MANUAL" && (
              <p className="mt-2 text-xs text-slate-500">Su estado lo controla la sincronización con RR.HH.</p>
            )}
            {temporal && (
              <div className="mt-3 flex flex-wrap items-center gap-3 rounded-md border border-brand-200 bg-brand-50 px-3 py-2.5 text-sm">
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
          </Seccion>

          <Seccion titulo="Datos">
            {editando ? (
              <EditarDatos u={u} aplicar={aplicar} cerrar={() => setEditando(false)} />
            ) : (
              <>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                  <div>
                    <dt className="text-xs text-slate-500">DNI</dt>
                    <dd className="font-mono text-slate-800">{u.dni ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-slate-500">Cargo</dt>
                    <dd className="text-slate-800">{u.cargo ?? "—"}</dd>
                  </div>
                </dl>
                {u.fuente === "MANUAL" && (
                  <Button size="sm" variant="ghost" className="mt-2" onClick={() => setEditando(true)}>
                    Editar datos
                  </Button>
                )}
              </>
            )}
          </Seccion>
        </div>
      </aside>
    </div>,
    document.body,
  );
}

/** Datos de un usuario MANUAL (DEC-L028); los de RR.HH. se corrigen alla. */
function EditarDatos({ u, aplicar, cerrar }: { u: UsuarioAdmin; aplicar: Aplicar; cerrar: () => void }) {
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
        await editarUsuario(u.id, { nombre, correo, dni: dni || undefined, cargo: cargo || undefined }),
      );
      if (ok) cerrar();
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={onGuardar} className="grid gap-3 sm:grid-cols-2">
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
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" size="sm" loading={guardando}>
          Guardar
        </Button>
        <Button type="button" size="sm" variant="ghost" disabled={guardando} onClick={cerrar}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
