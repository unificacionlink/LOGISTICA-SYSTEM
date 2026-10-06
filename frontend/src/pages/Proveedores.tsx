import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Truck, UserPlus } from "lucide-react";
import { listarProveedores } from "../api/proveedores";
import type { Proveedor } from "../api/types";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

export default function Proveedores() {
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    listarProveedores()
      .then((res) => {
        if (res.ok) setProveedores(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

  return (
    <div>
      <PageHeader
        title="Proveedores"
        actions={
          <Button href="/proveedores/nuevo" icon={<UserPlus className="size-4" />}>
            Nuevo proveedor
          </Button>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={5} />
        ) : proveedores.length === 0 ? (
          <EmptyState icon={Truck} title="Todavía no hay proveedores registrados" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className="px-5 py-3">RUC</th>
                  <th className="px-5 py-3">Razón social</th>
                  <th className="px-5 py-3">Contacto</th>
                  <th className="px-5 py-3">Tipo</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {proveedores.map((p) => (
                  <tr key={p.supplierId} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">{p.ruc}</td>
                    <td className="col-flex px-5 py-3 text-slate-700" title={String(p.legalName)}>{p.legalName}</td>
                    <td className="px-5 py-3 text-slate-600">
                      {p.contactName ?? p.email ?? p.whatsappPhone ?? "—"}
                    </td>
                    <td className="px-5 py-3">
                      <Badge tone={p.supplierType === "CRITICO" ? "amarillo" : "gris"}>
                        {p.supplierType === "CRITICO" ? "Crítico" : "No crítico"}
                      </Badge>
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link
                        to={`/proveedores/${p.supplierId}/editar`}
                        className="text-sm font-medium text-brand-600 hover:text-brand-700"
                      >
                        Editar
                      </Link>
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
