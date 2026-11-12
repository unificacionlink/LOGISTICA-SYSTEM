import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { actualizarProveedor, crearProveedor, obtenerProveedor } from "../api/proveedores";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import EquivalenciasProveedor from "../components/proveedores/EquivalenciasProveedor";
import { Skeleton } from "../components/ui/Skeleton";

export default function ProveedorForm() {
  const { id } = useParams();
  const editando = Boolean(id);
  const navigate = useNavigate();

  const [cargando, setCargando] = useState(editando);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [ruc, setRuc] = useState("");
  const [legalName, setLegalName] = useState("");
  const [contactName, setContactName] = useState("");
  const [email, setEmail] = useState("");
  const [whatsappPhone, setWhatsappPhone] = useState("");
  const [supplierType, setSupplierType] = useState("NO_CRITICO");
  const [preferredDeliveryMode, setPreferredDeliveryMode] = useState("");
  const [active, setActive] = useState(true);

  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!id) return;
    obtenerProveedor(id)
      .then((res) => {
        if (res.ok && res.result) {
          const p = res.result;
          setRuc(p.ruc);
          setLegalName(p.legalName);
          setContactName(p.contactName ?? "");
          setEmail(p.email ?? "");
          setWhatsappPhone(p.whatsappPhone ?? "");
          setSupplierType(p.supplierType);
          setPreferredDeliveryMode(p.preferredDeliveryMode ?? "");
          setActive(p.active);
        } else {
          setErrorCarga(res.message);
        }
      })
      .finally(() => setCargando(false));
  }, [id]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const input = {
        ruc,
        legalName,
        contactName: contactName || undefined,
        email: email || undefined,
        whatsappPhone: whatsappPhone || undefined,
        supplierType,
        preferredDeliveryMode: preferredDeliveryMode || undefined,
        active,
      };
      const res = id ? await actualizarProveedor(id, input) : await crearProveedor(input);
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      navigate("/proveedores");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <PageHeader
        title={editando ? "Editar proveedor" : "Nuevo proveedor"}
        backTo="/proveedores"
        backLabel="Proveedores"
      />

      {cargando ? (
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </CardBody>
        </Card>
      ) : errorCarga ? (
        <Alert tone="danger">{errorCarga}</Alert>
      ) : (
        <Card>
          <CardBody>
            <form onSubmit={onSubmit} className="flex flex-col gap-5">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="RUC" required>
                  <input
                    required
                    maxLength={11}
                    value={ruc}
                    onChange={(e) => setRuc(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
                <Field label="Razón social" required>
                  <input
                    required
                    value={legalName}
                    onChange={(e) => setLegalName(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Contacto">
                  <input
                    value={contactName}
                    onChange={(e) => setContactName(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
                <Field label="Correo">
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="WhatsApp">
                  <input
                    value={whatsappPhone}
                    onChange={(e) => setWhatsappPhone(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
                <Field label="Tipo de proveedor">
                  <select
                    value={supplierType}
                    onChange={(e) => setSupplierType(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="NO_CRITICO">No crítico</option>
                    <option value="CRITICO">Crítico</option>
                  </select>
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Modalidad de entrega preferida">
                  <select
                    value={preferredDeliveryMode}
                    onChange={(e) => setPreferredDeliveryMode(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="">Sin definir</option>
                    <option value="AGENCIA">Agencia</option>
                    <option value="ALMACEN">Almacén</option>
                    <option value="RECOJO">Recojo</option>
                  </select>
                </Field>
                {editando && (
                  <Field label="Estado">
                    <select
                      value={active ? "1" : "0"}
                      onChange={(e) => setActive(e.target.value === "1")}
                      className={`${inputClass} w-full`}
                    >
                      <option value="1">Activo</option>
                      <option value="0">Inactivo</option>
                    </select>
                  </Field>
                )}
              </div>

              {error && <Alert tone="danger">{error}</Alert>}

              <Button type="submit" loading={enviando} className="self-start">
                {enviando ? "Guardando…" : editando ? "Guardar cambios" : "Crear proveedor"}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}
      {editando && id && !cargando && !errorCarga && <EquivalenciasProveedor supplierId={id} />}
    </div>
  );
}
