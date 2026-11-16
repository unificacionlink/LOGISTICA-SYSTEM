package com.tdem.logistica.modules.almacen.dto;

import java.util.List;

public record NotaInventarioDetalleDto(
        NotaInventarioDto nota,
        List<NotaItemDto> items,
        List<DocumentoAdjuntoDto> documentos,
        DocumentoAdjuntoDto evidenciaEmbalaje,
        List<DestinoDtos.RecepcionDto> recepciones,
        List<DestinoDtos.ResolucionDto> resoluciones) {
}
