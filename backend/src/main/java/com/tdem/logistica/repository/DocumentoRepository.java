package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
}
