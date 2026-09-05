// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.ResultadoMetrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultadoMetricaRepository extends JpaRepository<ResultadoMetrica, UUID> {
    
    /**
     * Obtiene el último resultado calculado para una métrica en un sprint.
     */
    @Query("""
        SELECT r FROM ResultadoMetrica r
        WHERE r.metrica.id = :metricaId
        AND r.sprintId = :sprintId
        ORDER BY r.calculadoAt DESC
        LIMIT 1
        """)
    Optional<ResultadoMetrica> findUltimoResultado(
        @Param("metricaId") UUID metricaId,
        @Param("sprintId") UUID sprintId
    );
    
    /**
     * Obtiene todos los resultados de un sprint.
     */
    List<ResultadoMetrica> findBySprintIdOrderByCalculadoAtDesc(UUID sprintId);
    
    /**
     * Obtiene histórico de resultados de una métrica.
     */
    List<ResultadoMetrica> findByMetrica_IdOrderByCalculadoAtDesc(UUID metricaId);
    
    /**
     * Obtiene resultados de un proyecto y sprint.
     */
    List<ResultadoMetrica> findByProyectoIdAndSprintId(UUID proyectoId, UUID sprintId);
    
    /**
     * Obtiene histórico de resultados de una métrica en un proyecto específico.
     * Ordenado por fecha descendente.
     * Fase 16.9.1: Para métricas académicas.
     */
    List<ResultadoMetrica> findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(
        UUID metricaId,
        UUID proyectoId
    );

    /**
     * Resultado vigente (a lo sumo uno, por el índice único parcial de V37)
     * para una combinación proyecto+métrica+sprint+versión de parametrización.
     * Antes de guardar un nuevo cálculo se marca este como histórico
     * (vigente=false) — ver CalculoMetricaService.
     */
    Optional<ResultadoMetrica> findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
        UUID proyectoId, UUID metricaId, UUID sprintId, Integer parametrizacionVersion
    );

    /**
     * Corrección de auditoría (parte B): TODOS los resultados vigentes para una
     * combinación proyecto+métrica+sprint, sin importar la versión de
     * parametrización que los produjo. La regla funcional es "a lo sumo un
     * vigente por proyecto+métrica+sprint" — el método de arriba (scopeado
     * también por versión) no la garantiza por sí solo: el índice único parcial
     * de V37 (idx_resultado_vigente_unico) todavía incluye parametrizacion_version,
     * por lo que a nivel de esquema pueden coexistir vigentes de versiones
     * distintas (ver CalculoMetricaService.invalidarResultadosVigentes()). Se
     * devuelve List, no Optional, precisamente porque hoy puede haber más de una
     * fila — este método es el que permite detectarlas todas para invalidarlas.
     */
    List<ResultadoMetrica> findByProyectoIdAndMetrica_IdAndSprintIdAndVigenteTrue(
        UUID proyectoId, UUID metricaId, UUID sprintId
    );

    /**
     * Histórico vigente de una métrica en un proyecto, uno por sprint, para
     * alimentar Evaluación/gráficas con el resultado ya calculado en vez de
     * RegistroValor crudo. Fuente del nuevo endpoint GET /api/metricas/{id}/resultados.
     */
    List<ResultadoMetrica> findByProyectoIdAndMetrica_IdAndVigenteTrue(UUID proyectoId, UUID metricaId);
}
