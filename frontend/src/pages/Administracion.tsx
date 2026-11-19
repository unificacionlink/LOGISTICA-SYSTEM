import { useMemo, useState } from "react";
import { useDatos } from "../api/useDatos";
import { useSearchParams } from "react-router-dom";
import { Building2, Contact, RefreshCw, ScrollText, Users } from "lucide-react";
import { crearCeco, obtenerAdmin, sincronizarRrhh } from "../api/admin";
import type { AdminDatos } from "../api/types";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";
import PestanaPadron from "../components/admin/PestanaPadron";
import PestanaRegistro from "../components/admin/PestanaRegistro";
import PestanaUsuarios from "../components/admin/PestanaUsuarios";

type Pestana = "usuarios" | "personal" | "cecos" | "rrhh" | "registro";

const PESTANAS: { id: Pestana; label: string; icon: typeof Users }[] = [
  { id: "usuarios", label: "Usuarios", icon: Users },
  { id: "personal", label: "Personal", icon: Contact },
  { id: "cecos", label: "CECOs", icon: Building2 },
  { id: "rrhh", label: "RR.HH.", icon: RefreshCw },
  { id: "registro", label: "Registro", icon: ScrollText },
];

const thClass = "px-5 py-3";

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

export default function Administracion() {
  const [params, setParams] = useSearchParams();
  const tab = (PESTANAS.some((p) => p.id === params.get("tab")) ? params.get("tab") : "usuarios") as Pestana;
  // Memoria de la pestana (DEC-L073): las escrituras devuelven los datos completos.
  const admin = useDatos("admin", obtenerAdmin);
  const datos = admin.datos;
  const setDatos = admin.mutar;
  const [errorAccion, setError] = useState<string | null>(null);
  const error = errorAccion ?? admin.error;
  const [exito, setExito] = useState<string | null>(null);

  function recargar() {
    void admin.recargar();
  }


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

      {tab === "registro" ? (
        // DEC-L060: no depende de los datos de usuarios; carga lo suyo.
        <PestanaRegistro />
      ) : !datos ? (
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
              <tr className="border-b border-slate-200 cabecera-tabla">
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
