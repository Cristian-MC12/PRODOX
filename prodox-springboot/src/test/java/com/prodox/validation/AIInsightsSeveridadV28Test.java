// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.validation;

import com.prodox.entity.AIInsight;
import com.prodox.repository.AIInsightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FASE 21 — Validación de V28 (ai_insights_severidad_check admite 'CRITICAL').
 *
 * Causa raíz demostrada del defecto "AI Insights no genera insights": V20 dejó
 * el CHECK constraint de severidad en ('LOW','MEDIUM','HIGH'), pero
 * AgileAnalyticsService.determineTrendSeverity() (variación >30%) legítimamente
 * produce 'CRITICAL', y AIInsightsService.generateRiskInsights() copia ese valor
 * directamente a AIInsight.severidad. El INSERT fallaba recién al hacer commit
 * de la transacción (batching de Hibernate), haciendo rollback de TODOS los
 * insights generados en esa llamada — no solo el que tenía severidad CRITICAL.
 *
 * Corre dentro de una transacción que se revierte automáticamente
 * (@Transactional de Spring Test): no persiste ningún dato real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AIInsightsSeveridadV28Test {

    @Autowired
    private AIInsightRepository insightRepo;

    private AIInsight insightConSeveridad(String severidad) {
        AIInsight insight = new AIInsight();
        insight.setProyectoId(UUID.randomUUID());
        insight.setTipo("RISK");
        insight.setTitulo("Test severidad " + severidad);
        insight.setDescripcion("Insight de prueba para validar V28");
        insight.setSeveridad(severidad);
        insight.setConfianza("MEDIUM");
        return insight;
    }

    @Test
    void severidadCritical_sePersisteSinViolarElCheckConstraint() {
        AIInsight guardado = insightRepo.saveAndFlush(insightConSeveridad("CRITICAL"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getSeveridad()).isEqualTo("CRITICAL");
    }

    @Test
    void severidadesExistentesSiguenFuncionandoSinRegresion() {
        for (String severidad : new String[]{"LOW", "MEDIUM", "HIGH"}) {
            AIInsight guardado = insightRepo.saveAndFlush(insightConSeveridad(severidad));
            assertThat(guardado.getSeveridad()).isEqualTo(severidad);
        }
    }

    @Test
    void severidadInvalidaFueraDelDominio_sigueSiendoRechazada() {
        // El constraint no se relajó por completo: solo se amplió a los 4 valores
        // reales del dominio — un valor arbitrario sigue violando el CHECK.
        assertThatThrownBy(() -> insightRepo.saveAndFlush(insightConSeveridad("NO_EXISTE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
