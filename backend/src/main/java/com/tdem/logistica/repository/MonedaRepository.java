package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Moneda;

import java.util.List;

public interface MonedaRepository extends RepositorioBase<Moneda, String> {

    List<Moneda> findAllByActiveTrueOrderByCurrencyCode();
}
