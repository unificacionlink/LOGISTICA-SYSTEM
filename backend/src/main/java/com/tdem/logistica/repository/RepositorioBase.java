package com.tdem.logistica.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base de todos los repositorios (DEC-L074).
 *
 * <p>Los ids se asignan a mano ({@code UUID.randomUUID()}), asi que Spring Data
 * no sabe que una entidad es nueva y {@code save} hace {@code merge}: un SELECT
 * por id antes de cada INSERT. Con la BD lejos, cada alta costaba el doble.
 * {@code insertar} hace {@code persist} directo (un solo INSERT, que ademas
 * entra en los lotes de {@code hibernate.jdbc.batch_size}).
 *
 * <p>Solo para entidades recien creadas con {@code new}: si el id ya existe en
 * la BD, falla por clave duplicada. Para modificar, {@code save} sigue igual.
 */
@NoRepositoryBean
public interface RepositorioBase<T, ID> extends JpaRepository<T, ID> {

    <S extends T> S insertar(S entidad);

    <S extends T> List<S> insertarTodos(Iterable<S> entidades);
}
