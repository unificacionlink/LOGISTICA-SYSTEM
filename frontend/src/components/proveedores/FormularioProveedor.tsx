import { useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import CampoNumero from "../ui/CampoNumero";
import Field, { inputClass } from "../ui/Field";
import type { DatosProveedor } from "./datosProveedor";

/** Seccion plegable: el formulario no se ve largo de golpe (ley de Hick). */
function Seccion({ titulo, abierta = false, children }: { titulo: string; abierta?: boolean; children: ReactNode }) {
  const [abierto, setAbierto] = useState(abierta);
  return (
    <section className="border-b border-slate-100 last:border-b-0">
      <button
        type="button"
        aria-expanded={abierto}
        onClick={() => setAbierto((v) => !v)}
        className="flex w-full items-center justify-between px-5 py-3 text-left text-sm font-semibold text-slate-800 hover:bg-slate-50"
      >
        {titulo}
        <ChevronDown className={`size-4 text-slate-400 transition-transform ${abierto ? "rotate-180" : ""}`} />
      </button>
      {abierto && <div className="grid grid-cols-1 gap-4 px-5 pb-5 sm:grid-cols-2">{children}</div>}
    </section>
  );
}

/**
 * Datos de la ficha del proveedor por secciones (DEC-L069): Identificacion,
 * Contacto, Ubicacion y entrega, y Condiciones comerciales. Lo usan el alta y
 * el panel de edicion.
 */
export default function FormularioProveedor({
  datos,
  onCambio,
}: {
  datos: DatosProveedor;
  onCambio: (d: DatosProveedor) => void;
}) {
  const set = (campo: keyof DatosProveedor) => (valor: string) => onCambio({ ...datos, [campo]: valor });
  const input = (campo: keyof DatosProveedor, props: { type?: string; maxLength?: number; placeholder?: string } = {}) => (
    <input
      className={`${inputClass} w-full`}
      value={datos[campo]}
      onChange={(e) => set(campo)(e.target.value)}
      maxLength={props.maxLength}
      type={props.type ?? "text"}
      placeholder={props.placeholder}
    />
  );

  return (
    <div>
      <Seccion titulo="Identificación" abierta>
        <Field label="RUC" required>
          <CampoNumero
            className={`${inputClass} w-full font-mono`}
            value={datos.ruc}
            onChange={set("ruc")}
            maxEnteros={11}
            required
          />
        </Field>
        <Field label="Tipo">
          <select className={`${inputClass} w-full`} value={datos.supplierType} onChange={(e) => set("supplierType")(e.target.value)}>
            <option value="NO_CRITICO">No crítico</option>
            <option value="CRITICO">Crítico</option>
          </select>
        </Field>
        <Field label="Razón social" required className="sm:col-span-2">
          {input("legalName", { maxLength: 220 })}
        </Field>
        <Field label="Nombre comercial" className="sm:col-span-2">
          {input("tradeName", { maxLength: 220 })}
        </Field>
      </Seccion>

      <Seccion titulo="Contacto" abierta>
        <Field label="Persona responsable">{input("contactName", { maxLength: 150 })}</Field>
        <Field label="Cargo">{input("contactRole", { maxLength: 120 })}</Field>
        <Field label="Correo">{input("email", { type: "email", maxLength: 180 })}</Field>
        <Field label="Teléfono">{input("phone", { type: "tel", maxLength: 30 })}</Field>
        <Field label="WhatsApp">{input("whatsappPhone", { type: "tel", maxLength: 30 })}</Field>
        <Field label="Página web">{input("website", { maxLength: 200 })}</Field>
      </Seccion>

      <Seccion titulo="Ubicación y entrega">
        <Field label="Dirección" className="sm:col-span-2">
          {input("address", { maxLength: 250 })}
        </Field>
        <Field label="Ciudad">{input("city", { maxLength: 120 })}</Field>
        <Field label="Entrega preferida">
          <select
            className={`${inputClass} w-full`}
            value={datos.preferredDeliveryMode}
            onChange={(e) => set("preferredDeliveryMode")(e.target.value)}
          >
            <option value="">—</option>
            <option value="AGENCIA">Agencia</option>
            <option value="ALMACEN">En nuestro almacén</option>
            <option value="RECOJO">Recojo en su local</option>
          </select>
        </Field>
      </Seccion>

      <Seccion titulo="Condiciones comerciales">
        <Field label="Productos o servicios que ofrece" className="sm:col-span-2">
          <textarea
            className={`${inputClass} w-full whitespace-normal`}
            rows={2}
            maxLength={1000}
            value={datos.offeringDescription}
            onChange={(e) => set("offeringDescription")(e.target.value)}
          />
        </Field>
        <Field label="Condición de pago" className="sm:col-span-2">
          {input("paymentCondition", { maxLength: 150, placeholder: "Contado, crédito, contraentrega…" })}
        </Field>
        <Field label="Días de crédito">
          <CampoNumero className={`${inputClass} w-full`} value={datos.creditDays} onChange={set("creditDays")} maxEnteros={3} max={365} />
        </Field>
        <Field label="Plazo de entrega (días)">
          <CampoNumero className={`${inputClass} w-full`} value={datos.leadDays} onChange={set("leadDays")} maxEnteros={3} max={365} />
        </Field>
        <Field label="Garantía" className="sm:col-span-2">
          {input("warrantyTerms", { maxLength: 500 })}
        </Field>
      </Seccion>
    </div>
  );
}
