package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Rol;

import java.util.Optional;
import java.util.UUID;

public interface RolRepository extends RepositorioBase<Rol, UUID> {
    Optional<Rol> findByCode(String code);
}
