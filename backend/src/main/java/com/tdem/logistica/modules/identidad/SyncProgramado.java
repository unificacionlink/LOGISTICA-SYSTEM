package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.security.AccesoVigente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Sincronizacion diaria con RRHH-SYSTEM (DEC-L024); apagada si no hay RRHH_API_URL. */
@Configuration
@EnableScheduling
public class SyncProgramado {

    private static final Logger log = LoggerFactory.getLogger(SyncProgramado.class);

    private final PersonalSyncService syncService;
    private final AccesoVigente accesoVigente;

    public SyncProgramado(PersonalSyncService syncService, AccesoVigente accesoVigente) {
        this.syncService = syncService;
        this.accesoVigente = accesoVigente;
    }

    @Scheduled(cron = "${app.rrhh.sync-cron:0 0 6 * * *}", zone = "America/Lima")
    public void ejecutar() {
        if (!syncService.configurado()) {
            return;
        }
        try {
            syncService.sincronizar();
            accesoVigente.invalidarTodo();
        } catch (RuntimeException e) {
            log.warn("Sync programado con RRHH fallo: {}", e.getMessage());
        }
    }
}
