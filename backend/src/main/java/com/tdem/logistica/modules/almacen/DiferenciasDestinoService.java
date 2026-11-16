package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Envio;
import com.tdem.logistica.entity.NotaInventario;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.entity.ResolucionDestino;
import com.tdem.logistica.entity.SalidaItem;
import com.tdem.logistica.modules.almacen.dto.DestinoDtos;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.repository.EnvioRepository;
import com.tdem.logistica.repository.ResolucionDestinoRepository;
import com.tdem.logistica.repository.SalidaItemRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pestana Diferencias del almacen (V38.7 aiRender 'diferencias'): observado por resolver y devoluciones. */
@Service
@Transactional(readOnly = true)
public class DiferenciasDestinoService {

    private final SalidaItemRepository salidaItemRepository;
    private final EnvioRepository envioRepository;
    private final ResolucionDestinoRepository resolucionRepository;
    private final RequerimientoService requerimientoService;

    public DiferenciasDestinoService(SalidaItemRepository salidaItemRepository, EnvioRepository envioRepository,
                                     ResolucionDestinoRepository resolucionRepository,
                                     RequerimientoService requerimientoService) {
        this.salidaItemRepository = salidaItemRepository;
        this.envioRepository = envioRepository;
        this.resolucionRepository = resolucionRepository;
        this.requerimientoService = requerimientoService;
    }

    public DestinoDtos.DiferenciasDto listar(UsuarioPrincipal principal) {
        if (!RecepcionDestinoService.esOperadorDestino(principal)) {
            throw ApiException.forbidden("No tienes permiso para ver las diferencias de recepcion.");
        }
        List<SalidaItem> observados = salidaItemRepository.findConObservado();
        Map<UUID, Envio> envioPorSalida = observados.isEmpty() ? Map.of()
                : envioRepository.findBySalidas(observados.stream().map(si -> si.getSalida().getIssueId())
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(e -> e.getSalida().getIssueId(), Function.identity()));
        Map<UUID, Boolean> visibles = new HashMap<>();

        List<DestinoDtos.ObservadoDto> filas = new ArrayList<>();
        for (SalidaItem si : observados) {
            Envio e = envioPorSalida.get(si.getSalida().getIssueId());
            if (e == null || !puedeVer(e.getNotaInventario().getRequerimiento(), principal, visibles)) {
                continue;
            }
            NotaInventario n = e.getNotaInventario();
            filas.add(new DestinoDtos.ObservadoDto(e.getShipmentId(), n.getInventoryNoteId(), n.getInventoryNoteCode(),
                    n.getRequerimiento().getRequestCode(), n.getEmpresa().getCode(), si.getIssueItemId(),
                    AlmacenMapper.sku(si), si.getProducto().getName(), si.getDestObservedQty()));
        }
        List<DestinoDtos.DevolucionDto> devoluciones = new ArrayList<>();
        for (ResolucionDestino r : resolucionRepository.findDevoluciones()) {
            NotaInventario n = r.getEnvio().getNotaInventario();
            if (!puedeVer(n.getRequerimiento(), principal, visibles)) {
                continue;
            }
            devoluciones.add(new DestinoDtos.DevolucionDto(r.getResolutionId(), n.getInventoryNoteId(),
                    n.getInventoryNoteCode(), n.getRequerimiento().getRequestCode(), n.getEmpresa().getCode(),
                    AlmacenMapper.sku(r.getSalidaItem()),
                    r.getSalidaItem().getProducto().getName(), r.getQty(), r.getStatus(), r.getEvidenceUrl(),
                    r.getCreatedAt(), r.getReceivedAt(), r.getReturnEvidenceUrl()));
        }
        return new DestinoDtos.DiferenciasDto(filas, devoluciones);
    }

    /** LOGISTICA/ADMIN ven todo; GERENCIA, los requerimientos de su ambito. */
    private boolean puedeVer(Requerimiento r, UsuarioPrincipal p, Map<UUID, Boolean> cache) {
        if (p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            return true;
        }
        return cache.computeIfAbsent(r.getRequestId(), k -> {
            try {
                requerimientoService.exigirAcceso(r, p);
                return true;
            } catch (ApiException e) {
                return false;
            }
        });
    }
}
