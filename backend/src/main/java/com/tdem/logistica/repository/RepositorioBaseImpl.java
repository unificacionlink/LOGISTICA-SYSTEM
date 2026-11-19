package com.tdem.logistica.repository;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Implementacion de {@link RepositorioBase}: alta con {@code persist}, sin el SELECT de {@code merge}. */
public class RepositorioBaseImpl<T, ID> extends SimpleJpaRepository<T, ID> implements RepositorioBase<T, ID> {

    private final EntityManager em;

    public RepositorioBaseImpl(JpaEntityInformation<T, ?> informacion, EntityManager em) {
        super(informacion, em);
        this.em = em;
    }

    @Override
    @Transactional
    public <S extends T> S insertar(S entidad) {
        em.persist(entidad);
        return entidad;
    }

    @Override
    @Transactional
    public <S extends T> List<S> insertarTodos(Iterable<S> entidades) {
        List<S> lista = new ArrayList<>();
        for (S e : entidades) {
            em.persist(e);
            lista.add(e);
        }
        return lista;
    }
}
