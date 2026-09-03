// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.HistoriaUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HistoriaUsuarioRepository extends JpaRepository<HistoriaUsuario, UUID> {
    List<HistoriaUsuario> findByProyectoId(UUID proyectoId);
}
