package com.tdem.logistica.repository;

import com.tdem.logistica.entity.PuntoEntrega;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PuntoEntregaRepository extends RepositorioBase<PuntoEntrega, UUID> {

    /** Direcciones vigentes: primero el almacen, luego por nombre (DEC-L089). */
    @Query("select p from PuntoEntrega p join fetch p.empresa left join fetch p.ceco where p.active "
            + "order by case when p.pointType = 'ALMACEN' then 0 else 1 end, p.name")
    List<PuntoEntrega> findActivos();
}
