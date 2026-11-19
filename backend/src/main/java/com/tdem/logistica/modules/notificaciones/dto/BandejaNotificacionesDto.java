package com.tdem.logistica.modules.notificaciones.dto;

import java.util.List;

/** Las ultimas notificaciones del usuario y cuantas no ha leido. */
public record BandejaNotificacionesDto(int noLeidas, List<NotificacionDto> items) {
}
