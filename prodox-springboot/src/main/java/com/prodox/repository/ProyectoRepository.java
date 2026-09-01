// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, UUID> {
    List<Proyecto> findByScrumMasterIdOrderByCreatedAtDesc(String scrumMasterId);
    List<Proyecto> findByScrumMasterIdAndEstadoOrderByCreatedAtDesc(String scrumMasterId, String estado);
}
