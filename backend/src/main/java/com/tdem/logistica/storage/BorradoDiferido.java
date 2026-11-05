package com.tdem.logistica.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Borra archivos del Storage segun termine la transaccion en curso (DEC-L044):
 * los que reemplazo un cambio solo cuando la BD lo confirma (si algo falla, el
 * anterior sigue sirviendo), y el recien subido si la transaccion se revierte
 * (asi no queda un archivo huerfano). Un fallo al borrar se registra y no
 * afecta al usuario: la limpieza nocturna lo reintenta.
 */
public final class BorradoDiferido {

    private static final Logger log = LoggerFactory.getLogger(BorradoDiferido.class);

    private BorradoDiferido() {
    }

    public static void programar(StorageClient storage, List<String> siConfirma, List<String> siRevierte) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            siConfirma.forEach(url -> borrar(storage, url));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                (estado == STATUS_COMMITTED ? siConfirma : siRevierte).forEach(url -> borrar(storage, url));
            }
        });
    }

    static void borrar(StorageClient storage, String url) {
        try {
            storage.borrar(url);
        } catch (RuntimeException e) {
            log.warn("No se pudo borrar {} del Storage: {}", url, e.getMessage());
        }
    }
}
