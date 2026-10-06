package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RolRepository extends JpaRepository<Rol, UUID> {
    Optional<Rol> findByCode(String code);
}
