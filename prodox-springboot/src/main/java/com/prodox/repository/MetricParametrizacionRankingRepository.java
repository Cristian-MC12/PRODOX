// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.MetricParametrizacionRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MetricParametrizacionRankingRepository extends JpaRepository<MetricParametrizacionRanking, UUID> {

    /**
     * Entradas de ranking de una métrica, de mayor a menor cantidad de usos.
     * El llamador limita a 3 (Top3) — sin límite acá para no atar el
     * repositorio a ese número de negocio.
     */
    List<MetricParametrizacionRanking> findByMetricaIdOrderByUsosDesc(UUID metricaId);

    /**
     * Auditoría (distinguir "Usar" de un guardado manual): true si
     * parametrizacionCanonicaId es REALMENTE la canónica vigente del ranking
     * de esta métrica. Usado por MetricRankingService para validar el id que
     * el cliente afirma haber usado desde "Usar" — nunca se confía en el
     * valor del request sin esta verificación.
     */
    boolean existsByMetricaIdAndParametrizacionCanonicaId(UUID metricaId, UUID parametrizacionCanonicaId);
}
