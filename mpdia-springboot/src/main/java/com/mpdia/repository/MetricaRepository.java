// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.Metrica;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MetricaRepository extends JpaRepository<Metrica, UUID> {
    List<Metrica> findAllByOrderByCategoriaIdAscNombreAsc();
    List<Metrica> findByCategoriaNombre(String categoriaNombre);
}
