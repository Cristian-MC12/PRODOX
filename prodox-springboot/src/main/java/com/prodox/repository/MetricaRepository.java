// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.Metrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricaRepository extends JpaRepository<Metrica, UUID> {
    List<Metrica> findAllByOrderByCategoriaIdAscNombreAsc();
    List<Metrica> findByCategoriaNombre(String categoriaNombre);

    /**
     * FASE 15 — siguiente valor de metrica_ia_codigo_seq (V27), usado para generar
     * el código único "IA-NNN" de una métrica creada mediante IA. nextval() sobre
     * una secuencia Postgres es atómico: seguro ante llamadas concurrentes.
     */
    @Query(value = "SELECT nextval('metrica_ia_codigo_seq')", nativeQuery = true)
    long siguienteValorSecuenciaCodigoIA();

    boolean existsByCodigo(String codigo);

    /**
     * Busca en el catálogo GLOBAL una métrica cuyo nombre coincida ignorando
     * mayúsculas y espacios extremos (respaldado por el índice único
     * ux_metricas_nombre_global, V31). Es la base tanto de la protección de
     * duplicados como del flujo de reutilización: "Velocidad" en el
     * Proyecto A y "Velocidad" en el Proyecto B son la MISMA fila de
     * Metrica, reutilizada vía ProyectoMetrica — nunca dos filas distintas.
     */
    @Query(value = "SELECT * FROM metricas WHERE lower(trim(nombre)) = lower(trim(:nombre)) LIMIT 1",
           nativeQuery = true)
    Optional<Metrica> findByNombreIgnoreCaseTrimmed(String nombre);
}
