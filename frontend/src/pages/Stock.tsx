import { useEffect, useMemo, useState } from "react";
import { Boxes, PackagePlus, Search } from "lucide-react";
import { listarEmpresasStock, listarSaldosStock } from "../api/stock";
import type { EmpresaOpcion, SaldoStock } from "../api/types";
import { fmtCantidad } from "../components/AlmacenBadges";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

export default function Stock() {
  const [empresas, setEmpresas] = useState<EmpresaOpcion[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [saldos, setSaldos] = useState<SaldoStock[]>([]);
  const [texto, setTexto] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    listarEmpresasStock().then((res) => {
      if (res.ok && res.result) {
        setEmpresas(res.result);
        if (res.result.length > 0) setCompanyId(res.result[0].companyId);
        else setCargando(false);
      } else {
        setError(res.message);
        setCargando(false);
      }
    });
  }, []);

  useEffect(() => {
    if (!companyId) return;
    setCargando(true);
    listarSaldosStock(companyId)
      .then((res) => {
        if (res.ok) setSaldos(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, [companyId]);

  const filtrados = useMemo(() => {
    const t = texto.trim().toLowerCase();
    if (!t) return saldos;
    return saldos.filter(
      (s) => s.codigoVariante.toLowerCase().includes(t) || s.nombreProducto.toLowerCase().includes(t),
    );
  }, [saldos, texto]);

  return (
    <div>
      <PageHeader
        title="Stock"
        actions={
          <Button href="/stock/ajustes" variant="secondary" icon={<PackagePlus className="size-4" />}>
            Ajustes
          </Button>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <div className="mb-4 flex flex-wrap gap-3">
        <select
          value={companyId}
          onChange={(e) => setCompanyId(e.target.value)}
          className={inputClass}
          aria-label="Empresa"
        >
          {empresas.map((e) => (
            <option key={e.companyId} value={e.companyId}>{e.code}</option>
          ))}
        </select>
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Buscar por SKU o producto"
            className={`${inputClass} w-full pl-9`}
          />
        </div>
      </div>

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={5} />
        ) : filtrados.length === 0 ? (
          <EmptyState icon={Boxes} title="Sin stock registrado" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className="px-5 py-3">SKU</th>
                  <th className="px-5 py-3">Producto</th>
                  <th className="px-5 py-3 text-right">Físico</th>
                  <th className="px-5 py-3 text-right">Reservado</th>
                  <th className="px-5 py-3 text-right">Disponible</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtrados.map((s) => (
                  <tr key={s.variantId} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-xs text-slate-600">{s.codigoVariante}</td>
                    <td className="col-flex px-5 py-3 text-slate-700" title={s.nombreProducto}>
                      {s.nombreProducto}
                      {s.atributo ? <span className="text-slate-400"> · {s.atributo}</span> : null}
                    </td>
                    <td className="px-5 py-3 text-right text-slate-600">{fmtCantidad(s.fisico)}</td>
                    <td className="px-5 py-3 text-right text-slate-600">{fmtCantidad(s.reservado)}</td>
                    <td
                      className={`px-5 py-3 text-right font-medium ${
                        s.disponible > 0 ? "text-emerald-700" : "text-slate-400"
                      }`}
                    >
                      {fmtCantidad(s.disponible)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
