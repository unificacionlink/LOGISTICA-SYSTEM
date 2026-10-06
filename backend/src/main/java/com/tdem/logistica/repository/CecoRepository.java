package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Ceco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CecoRepository extends JpaRepository<Ceco, UUID> {
    java.util.Optional<Ceco> findByEmpresa_CompanyIdAndCode(UUID companyId, String code);

    @Query("select c from Ceco c join fetch c.empresa e order by e.code, c.code")
    List<Ceco> findAllConEmpresa();

    /** CECOs activos de cualquier empresa activa (LOGISTICA/ADMIN piden para cualquiera). */
    @Query("select c from Ceco c join fetch c.empresa e where c.active = true and e.active = true order by e.code, c.code")
    List<Ceco> findActivosConEmpresa();

    /** CECOs activos asignados al usuario en {@code usuario_ceco}: el ambito del SOLICITANTE (DEC-L029). */
    @Query("select c from Ceco c join fetch c.empresa e where c.active = true and e.active = true "
            + "and c.cecoId in (:ids) order by e.code, c.code")
    List<Ceco> findActivosPorIds(List<UUID> ids);

    @Query(value = "select ceco_id from usuario_ceco where user_id = :userId", nativeQuery = true)
    List<UUID> findIdsAsignados(UUID userId);
}
