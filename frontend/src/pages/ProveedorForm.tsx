import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { crearProveedor } from "../api/proveedores";
import { aInput, DATOS_VACIOS } from "../components/proveedores/datosProveedor";
import FormularioProveedor from "../components/proveedores/FormularioProveedor";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import PageHeader from "../components/ui/PageHeader";

/**
 * Registro de un proveedor nuevo (DEC-L069): nace como borrador y se abre su
 * ficha para completar categorias, productos, documentos y seleccion.
 * La ruta antigua /proveedores/:id/editar lleva a la ficha con el editor abierto.
 */
export default function ProveedorForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [datos, setDatos] = useState(DATOS_VACIOS);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (id) return <Navigate to={`/proveedores/${id}?editar=1`} replace />;

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const res = await crearProveedor(aInput(datos));
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      navigate(`/proveedores/${res.result.supplierId}`, { replace: true });
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="max-w-3xl">
      <PageHeader title="Nuevo proveedor" backTo="/proveedores" backLabel="Proveedores" />
      <form onSubmit={onSubmit}>
        <Card className="overflow-hidden">
          <FormularioProveedor datos={datos} onCambio={setDatos} />
        </Card>
        {error && <Alert tone="danger" className="mt-4">{error}</Alert>}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" href="/proveedores">
            Cancelar
          </Button>
          <Button type="submit" loading={enviando} disabled={datos.ruc.length !== 11 || !datos.legalName.trim()}>
            Registrar
          </Button>
        </div>
      </form>
    </div>
  );
}
