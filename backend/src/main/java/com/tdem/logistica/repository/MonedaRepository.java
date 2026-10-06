package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonedaRepository extends JpaRepository<Moneda, String> {

    List<Moneda> findAllByActiveTrueOrderByCurrencyCode();
}
