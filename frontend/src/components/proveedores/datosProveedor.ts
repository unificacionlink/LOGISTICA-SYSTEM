import type { GuardarProveedorInput } from "../../api/proveedores";
import type { Proveedor } from "../../api/types";

/** Valores del formulario de la ficha: todo texto, como lo escribe el usuario. */
export interface DatosProveedor {
  ruc: string;
  legalName: string;
  tradeName: string;
  supplierType: string;
  contactName: string;
  contactRole: string;
  email: string;
  phone: string;
  whatsappPhone: string;
  website: string;
  address: string;
  city: string;
  preferredDeliveryMode: string;
  offeringDescription: string;
  paymentCondition: string;
  creditDays: string;
  leadDays: string;
  warrantyTerms: string;
  empresaCode: string;
}

export const DATOS_VACIOS: DatosProveedor = {
  ruc: "",
  legalName: "",
  tradeName: "",
  supplierType: "NO_CRITICO",
  contactName: "",
  contactRole: "",
  email: "",
  phone: "",
  whatsappPhone: "",
  website: "",
  address: "",
  city: "",
  preferredDeliveryMode: "",
  offeringDescription: "",
  paymentCondition: "",
  creditDays: "",
  leadDays: "",
  warrantyTerms: "",
  empresaCode: "TDEMSRL",
};

export function datosDe(p: Proveedor): DatosProveedor {
  return {
    ruc: p.ruc,
    legalName: p.legalName,
    tradeName: p.tradeName ?? "",
    supplierType: p.supplierType,
    contactName: p.contactName ?? "",
    contactRole: p.contactRole ?? "",
    email: p.email ?? "",
    phone: p.phone ?? "",
    whatsappPhone: p.whatsappPhone ?? "",
    website: p.website ?? "",
    address: p.address ?? "",
    city: p.city ?? "",
    preferredDeliveryMode: p.preferredDeliveryMode ?? "",
    offeringDescription: p.offeringDescription ?? "",
    paymentCondition: p.paymentCondition ?? "",
    creditDays: p.creditDays != null ? String(p.creditDays) : "",
    leadDays: p.leadDays != null ? String(p.leadDays) : "",
    warrantyTerms: p.warrantyTerms ?? "",
    empresaCode: p.empresaCode ?? "TDEMSRL",
  };
}

const texto = (s: string) => (s.trim() ? s.trim() : undefined);
const numero = (s: string) => (s.trim() ? Number(s) : null);

export function aInput(d: DatosProveedor): GuardarProveedorInput {
  return {
    ruc: d.ruc.trim(),
    legalName: d.legalName.trim(),
    tradeName: texto(d.tradeName),
    supplierType: d.supplierType,
    contactName: texto(d.contactName),
    contactRole: texto(d.contactRole),
    email: texto(d.email),
    phone: texto(d.phone),
    whatsappPhone: texto(d.whatsappPhone),
    website: texto(d.website),
    address: texto(d.address),
    city: texto(d.city),
    preferredDeliveryMode: texto(d.preferredDeliveryMode),
    offeringDescription: texto(d.offeringDescription),
    paymentCondition: texto(d.paymentCondition),
    creditDays: numero(d.creditDays),
    leadDays: numero(d.leadDays),
    warrantyTerms: texto(d.warrantyTerms),
    empresaCode: d.empresaCode,
  };
}

export const ENTREGA: Record<string, string> = {
  AGENCIA: "Agencia",
  ALMACEN: "En nuestro almacén",
  RECOJO: "Recojo en su local",
};
