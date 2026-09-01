// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service.copilot;

import org.junit.jupiter.api.Test;

import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.COMPARACION_SPRINTS;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.RECOMENDACIONES;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.RESULTADO_METRICA;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.RESULTADO_ULTIMO_SPRINT;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.RETROSPECTIVA;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.RIESGOS;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.TENDENCIAS;
import static com.prodox.service.copilot.CopilotIntentClassifier.IntentType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 12.4/12.5/12.6 — Tests del clasificador local de intención del AI Agile Copilot.
 * El clasificador solo detecta el patrón textual; la resolución contra métricas/BD reales
 * (y el rechazo si es ambigua) ocurre en AICopilotService, no aquí.
 */
class CopilotIntentClassifierTest {

    private final CopilotIntentClassifier classifier = new CopilotIntentClassifier();

    // ── Los 7 ejemplos positivos exigidos por FASE 12.4 ──────────────────────────

    @Test
    void cuantoDioDefectos_esResultadoMetricaConCandidataDefectos() {
        var intent = classifier.classify("¿Cuánto dio Defectos?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("defectos");
    }

    @Test
    void cualFueElResultadoDeDefectos_esResultadoMetrica() {
        var intent = classifier.classify("¿Cuál fue el resultado de Defectos?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("defectos");
    }

    @Test
    void cuantoSacamosEnFat_esResultadoMetricaConCandidataFat() {
        var intent = classifier.classify("¿Cuánto sacamos en FAT?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("fat");
    }

    @Test
    void cualFueElFat_esResultadoMetrica() {
        var intent = classifier.classify("¿Cuál fue el FAT?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("fat");
    }

    @Test
    void cuantaDeudaTecnicaGestionamos_esResultadoMetrica() {
        var intent = classifier.classify("¿Cuánta deuda técnica gestionamos?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("deuda tecnica");
    }

    @Test
    void cuantosImpedimentosTuvimos_esResultadoMetrica() {
        var intent = classifier.classify("¿Cuántos impedimentos tuvimos?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("impedimentos");
    }

    @Test
    void cuantosProblemasReportoElCliente_esResultadoMetrica() {
        var intent = classifier.classify("¿Cuántos problemas reportó el cliente?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("problemas");
    }

    // ── RESULTADO_ULTIMO_SPRINT ───────────────────────────────────────────────────

    @Test
    void cualFueElResultadoDelUltimoSprint_esResultadoUltimoSprint() {
        var intent = classifier.classify("¿Cuál fue el resultado del último sprint?");
        assertThat(intent.type()).isEqualTo(RESULTADO_ULTIMO_SPRINT);
        assertThat(intent.metricaCandidata()).isNull();
    }

    @Test
    void comoSalieronLasMetricasDelUltimoSprint_esResultadoUltimoSprint() {
        var intent = classifier.classify("¿Cómo salieron las métricas del último sprint?");
        assertThat(intent.type()).isEqualTo(RESULTADO_ULTIMO_SPRINT);
    }

    @Test
    void ultimoSprintTienePrioridadSobreElPatronGenericoDeMetrica() {
        // No debe interpretarse como RESULTADO_METRICA con candidata "resultado del ultimo sprint".
        var intent = classifier.classify("¿Cuál fue el resultado del último sprint?");
        assertThat(intent.type()).isEqualTo(RESULTADO_ULTIMO_SPRINT);
    }

    // ── Negativos: fuera del alcance de este clasificador ────────────────────────

    @Test
    void preguntaAmbiguaSinPatronReconocido_esUnknown() {
        assertThat(classifier.classify("¿Cómo vamos?").type()).isEqualTo(UNKNOWN);
        assertThat(classifier.classify("¿Qué pasó?").type()).isEqualTo(UNKNOWN);
        assertThat(classifier.classify("¿Hay problemas?").type()).isEqualTo(UNKNOWN);
    }

    @Test
    void preguntaFueraDeAlcanceDeducidoAunSiendoDeDominio_esUnknown() {
        // "¿Qué riesgos detectas?" (FASE 12.5) y "¿Qué deberíamos mejorar?" (FASE 12.6) YA NO
        // están aquí: fueron reclasificadas intencionalmente como RIESGOS y RECOMENDACIONES
        // respectivamente (ver las secciones correspondientes más abajo) — cambios de alcance
        // intencionales, no regresiones.
        assertThat(classifier.classify("¿Cómo estuvo el sprint?").type()).isEqualTo(UNKNOWN);
        assertThat(classifier.classify("Compara los últimos sprints").type()).isEqualTo(UNKNOWN);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FASE 12.5 — COMPARACION_SPRINTS / TENDENCIAS / RIESGOS
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void mejoramosRespectoAlSprintAnterior_esComparacionSprints() {
        assertThat(classifier.classify("¿Mejoramos respecto al sprint anterior?").type())
                .isEqualTo(COMPARACION_SPRINTS);
    }

    @Test
    void comoNosFueComparadoConElSprintAnterior_esComparacionSprints() {
        assertThat(classifier.classify("¿Cómo nos fue comparado con el sprint anterior?").type())
                .isEqualTo(COMPARACION_SPRINTS);
    }

    @Test
    void comparaEsteSprintConElAnterior_esComparacionSprints() {
        assertThat(classifier.classify("Compara este sprint con el anterior.").type())
                .isEqualTo(COMPARACION_SPRINTS);
    }

    @Test
    void queMetricasHanMejorado_esTendencias() {
        assertThat(classifier.classify("¿Qué métricas han mejorado?").type()).isEqualTo(TENDENCIAS);
    }

    @Test
    void queMetricasEmpeoraron_esTendencias() {
        assertThat(classifier.classify("¿Qué métricas empeoraron?").type()).isEqualTo(TENDENCIAS);
    }

    @Test
    void comoVienenLasMetricas_esTendencias() {
        assertThat(classifier.classify("¿Cómo vienen las métricas?").type()).isEqualTo(TENDENCIAS);
    }

    @Test
    void queRiesgosDetectas_esRiesgos() {
        assertThat(classifier.classify("¿Qué riesgos detectas?").type()).isEqualTo(RIESGOS);
    }

    @Test
    void queRiesgosTenemos_esRiesgos() {
        assertThat(classifier.classify("¿Qué riesgos tenemos?").type()).isEqualTo(RIESGOS);
    }

    @Test
    void hayRiesgosEnElProyecto_esRiesgos() {
        assertThat(classifier.classify("¿Hay riesgos en el proyecto?").type()).isEqualTo(RIESGOS);
    }

    @Test
    void comparaLosUltimosSprints_siguePermaneciendoAmbiguoYUnknown() {
        // Plural y sin "anterior": no se sabe cuántos sprints ni cuáles comparar.
        assertThat(classifier.classify("Compara los últimos sprints").type()).isEqualTo(UNKNOWN);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FASE 12.6 — RECOMENDACIONES / RETROSPECTIVA (Tipo C, separadas y nunca mezcladas)
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void queDeberiamosMejorar_esRecomendaciones() {
        assertThat(classifier.classify("¿Qué deberíamos mejorar?").type()).isEqualTo(RECOMENDACIONES);
    }

    @Test
    void queDeberiamosHacerParaElProximoSprint_esRecomendaciones() {
        assertThat(classifier.classify("¿Qué deberíamos hacer para el próximo sprint?").type())
                .isEqualTo(RECOMENDACIONES);
    }

    @Test
    void queRecomendamos_esRecomendaciones() {
        assertThat(classifier.classify("¿Qué recomendamos?").type()).isEqualTo(RECOMENDACIONES);
    }

    @Test
    void queAccionesDeberiamosConsiderar_esRecomendaciones() {
        assertThat(classifier.classify("¿Qué acciones deberíamos considerar?").type())
                .isEqualTo(RECOMENDACIONES);
    }

    @Test
    void enQueDeberiamosEnfocarnos_esRecomendaciones() {
        assertThat(classifier.classify("¿En qué deberíamos enfocarnos?").type()).isEqualTo(RECOMENDACIONES);
    }

    @Test
    void queRevisarEnRetrospectiva_esRetrospectiva() {
        assertThat(classifier.classify("¿Qué revisar en retrospectiva?").type()).isEqualTo(RETROSPECTIVA);
    }

    @Test
    void queDeberiamosDiscutirEnLaRetrospectiva_esRetrospectiva() {
        assertThat(classifier.classify("¿Qué deberíamos discutir en la retrospectiva?").type())
                .isEqualTo(RETROSPECTIVA);
    }

    @Test
    void queTemasLlevarALaRetrospectiva_esRetrospectiva() {
        assertThat(classifier.classify("¿Qué temas llevar a la retrospectiva?").type())
                .isEqualTo(RETROSPECTIVA);
    }

    // ── FASE 14 — BLOQUE 2: variantes naturales de "revisar ... retrospectiva" que antes
    // caían en UNKNOWN, incluida la EXACTA que sugiere el menú del Copilot. ──────────────

    @Test
    void queDeberiamosRevisarEnLaRetrospectiva_esRetrospectiva() {
        // Esta es literalmente la sugerencia del menú del Copilot (quickPrompts) — antes de
        // FASE 14 cae incorrectamente en UNKNOWN porque el patrón exigía "que revisar en"
        // exacto, sin admitir "deberiamos" en el medio.
        assertThat(classifier.classify("¿Qué deberíamos revisar en la retrospectiva?").type())
                .isEqualTo(RETROSPECTIVA);
    }

    @Test
    void queDeberiamosRevisarParaLaRetrospectiva_esRetrospectiva() {
        assertThat(classifier.classify("¿Qué deberíamos revisar para la retrospectiva?").type())
                .isEqualTo(RETROSPECTIVA);
    }

    @Test
    void variantesRazonablesDeRevisarRetrospectiva_sonRetrospectiva() {
        assertThat(classifier.classify("que deberiamos revisar en la retrospectiva").type())
                .isEqualTo(RETROSPECTIVA);
        assertThat(classifier.classify("QUE DEBERIAMOS REVISAR EN LA RETROSPECTIVA").type())
                .isEqualTo(RETROSPECTIVA);
        assertThat(classifier.classify("que revisar para retrospectiva").type())
                .isEqualTo(RETROSPECTIVA);
        assertThat(classifier.classify("¿qué hay que revisar en la retrospectiva?").type())
                .isEqualTo(RETROSPECTIVA);
    }

    @Test
    void recomendacionesYRetrospectiva_nuncaSeMezclanEntreSi() {
        // Una pregunta de recomendación no debe clasificarse como retrospectiva ni viceversa.
        assertThat(classifier.classify("¿Qué deberíamos mejorar?").type()).isNotEqualTo(RETROSPECTIVA);
        assertThat(classifier.classify("¿Qué revisar en retrospectiva?").type()).isNotEqualTo(RECOMENDACIONES);
    }

    @Test
    void nombreDeMetricaInexistenteIgualSeClasificaComoCandidata_laResolucionEsDeOtraCapa() {
        // El clasificador no sabe si "Bugs Criticos" existe: solo extrae el texto candidato.
        // Es AICopilotService quien debe rechazar si no matchea contra métricas reales.
        var intent = classifier.classify("¿Cuánto dio Bugs Criticos?");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("bugs criticos");
    }

    @Test
    void mensajeVacioONulo_esUnknown() {
        assertThat(classifier.classify("").type()).isEqualTo(UNKNOWN);
        assertThat(classifier.classify("   ").type()).isEqualTo(UNKNOWN);
        assertThat(classifier.classify(null).type()).isEqualTo(UNKNOWN);
    }

    // ── Normalización: mayúsculas/tildes/puntuación no alteran la clasificación ──

    @Test
    void normalizacion_mayusculasTildesYPuntuacionNoAlteranClasificacion() {
        var intent = classifier.classify("CUANTO DIO DEFECTOS");
        assertThat(intent.type()).isEqualTo(RESULTADO_METRICA);
        assertThat(intent.metricaCandidata()).isEqualTo("defectos");
    }
}
