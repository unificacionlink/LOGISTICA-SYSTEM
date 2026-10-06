package com.tdem.logistica.repository;

import com.tdem.logistica.entity.UsuarioRol;
import com.tdem.logistica.entity.UsuarioRolId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

    @Query("select r.code from UsuarioRol ur join Rol r on r.roleId = ur.rol " +
            "where ur.usuario = :usuarioId and r.active = true")
    List<String> findCodigosRolPorUsuario(@Param("usuarioId") UUID usuarioId);

    /** Administracion: filas [userId, codigoRol] de todos los usuarios, en una consulta. */
    @Query("select ur.usuario, r.code from UsuarioRol ur join Rol r on r.roleId = ur.rol where r.active = true")
    List<Object[]> findCodigosRolTodos();

    List<UsuarioRol> findByUsuario(UUID usuario);
}
