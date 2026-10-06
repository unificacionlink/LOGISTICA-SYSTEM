package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Salida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalidaRepository extends JpaRepository<Salida, UUID> {
}
