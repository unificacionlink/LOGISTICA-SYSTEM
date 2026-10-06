package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByCorporateEmailIgnoreCase(String correo);

    Optional<Usuario> findByEmployeeExternalId(String employeeExternalId);

    /** Administracion: todos los usuarios con su empresa, en una consulta. */
    @Query("select u from Usuario u left join fetch u.empresa order by u.displayName")
    List<Usuario> findAllConEmpresa();

    List<Usuario> findByFuenteAndActiveTrue(String fuente);
}
