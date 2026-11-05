import { useEffect, useMemo, useState } from "react";
import { Copy, FileSpreadsheet, Search, UserCheck, Users } from "lucide-react";
import { darAcceso, listarPersonal } from "../../api/admin";
import type { AdminDatos, PersonalAdmin } from "../../api/types";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import Field, { inputClass } from "../ui/Field";
import { SkeletonTable } from "../ui/Skeleton";
import CargaPersonalExcel from "./CargaPersonalExcel";
import { ETIQUETA_ROL } from "./roles";

type FiltroCuenta = "" | "sin" | "con";

const thClass = "px-5 py-3";

/**
 * Padron de personal por empresa (DEC-L041): cada empresa con su propia lista.
 * TDEM llega de RR.HH.; GOYDEL y METRIN se cargan por Excel. La cuenta se crea
 * aqui con "Dar acceso".
 */
export default function PestanaPadron({ datos, recargarAdmin }: { datos: AdminDatos; recargarAdmin: () => void }) {
  const [personal, setPersonal] = useState<PersonalAdmin[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // "" = aun no elegida: se muestra la empresa con mas personal (TDEM, la de RR.HH.).
  const [elegida, setElegida] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState<FiltroCuenta>("");
  const [abierto, setAbierto] = useState<string | null>(null);
  const [verExcel, setVerExcel] = useState(false);

  function cargar() {
    listarPersonal().then((res) => {
      if (res.ok && res.result) setPersonal(res.result);
      else setError(res.message);
    });
  }

  useEffect(cargar, []);

  const conteo = useMemo(() => {
    const m = new Map<string, number>();
    personal?.forEach((p) => m.set(p.empresaCode, (m.get(p.empresaCode) ?? 0) + 1));
    return m;
  }, [personal]);
  const empresa =
    elegida ||
    [...datos.empresas].sort((a, b) => (conteo.get(b.code) ?? 0) - (conteo.get(a.code) ?? 0))[0]?.code ||
    "";
  const empresasManuales = datos.empresas.filter((e) => e.altaManual).map((e) => e.code);
  const esManual = empresasManuales.includes(empresa);

  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return (personal ?? []).filter(
      (p) =>
        p.empresaCode === empresa &&
        // La fila abierta se queda aunque cambie de grupo: muestra la contrasena recien creada.
        (filtro === "" || p.id === abierto || (filtro === "con") === (p.userId !== null)) &&
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
          onChange={(e) => setFiltro(e.target.value as FiltroCuenta)}
          className={inputClass}
          aria-label="Cuenta"
        >
          <option value="">Con y sin cuenta</option>
          <option value="sin">Sin cuenta</option>
          <option value="con">Con cuenta</option>
        </select>
        {esManual && (
          <Button variant="secondary" icon={<FileSpreadsheet className="size-4" />} onClick={() => setVerExcel((v) => !v)}>
            Carga por Excel
          </Button>
        )}
      </div>

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
          <EmptyState icon={Users} title={conteo.get(empresa) ? "Sin resultados" : `Aún no hay personal de ${empresa}`} />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 cabecera-tabla">
                <th className={thClass}>Persona</th>
                <th className={`${thClass} col-sec`}>CECO</th>
                <th className={`${thClass} text-right`}>Cuenta</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtrados.map((p) => (
                <FilaPersona
                  key={p.id}
                  p={p}
                  roles={datos.roles}
                  abierto={abierto === p.id}
                  abrir={() => setAbierto(p.id)}
                  cerrar={() => setAbierto(null)}
                  alCrear={(lista) => {
                    setPersonal(lista);
                    recargarAdmin();
                  }}
                />
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

function FilaPersona({
  p,
  roles,
  abierto,
  abrir,
  cerrar,
  alCrear,
}: {
  p: PersonalAdmin;
  roles: string[];
  abierto: boolean;
  abrir: () => void;
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

  return (
    <>
      <tr className={abierto ? "bg-slate-50" : "hover:bg-slate-50"}>
        <td className="col-flex px-5 py-3" title={p.nombre}>
          <p className="text-sm text-slate-800">{p.nombre}</p>
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
        <td className="whitespace-nowrap px-5 py-3 text-right">
          {p.userId ? (
            <span className="inline-flex flex-col items-end gap-0.5">
              <Badge tone="verde">
                <UserCheck className="size-3" />
                Con cuenta
              </Badge>
              {p.correo && <span className="text-xs text-slate-400">{p.correo}</span>}
            </span>
          ) : (
            !abierto && (
              <Button size="sm" variant="secondary" onClick={abrir}>
                Dar acceso
              </Button>
            )
          )}
        </td>
      </tr>
      {abierto && (
        <tr className="bg-slate-50">
          <td colSpan={3} className="px-5 pb-4 pt-1">
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              {temporal || aviso ? (
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
              ) : (
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
                            onChange={(e) =>
                              setSeleccion((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))
                            }
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
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}
