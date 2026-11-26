import { verificacionesDeNota } from "../../api/almacen";
import { descargarPdfVerificacionDeNota } from "../../api/documentos";
import { useDatos } from "../../api/useDatos";
import BotonDescarga from "../BotonDescarga";

/**
 * F-LOG-08 de las OC que compraron lo que despacha esta nota (DEC-L102): se
 * relacionan solos por las líneas del requerimiento que atiende cada OC.
 */
export default function VerificacionesNota({ noteId }: { noteId: string }) {
  const lista = useDatos(`ni-verif:${noteId}`, () => verificacionesDeNota(noteId), { conservar: true });
  return (
    <>
      {(lista.datos ?? []).map((v) => (
        <BotonDescarga
          key={v.orderId}
          etiqueta={`F-LOG-08 ${v.orderCode}`}
          descargar={() => descargarPdfVerificacionDeNota(noteId, v.orderId, v.orderCode)}
        />
      ))}
    </>
  );
}
