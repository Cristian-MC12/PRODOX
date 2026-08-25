// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service.copilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 12.2 — Tests del guardrail de dominio del AI Agile Copilot.
 */
class CopilotDomainGuardTest {

    private final CopilotDomainGuard guard = new CopilotDomainGuard();

    // ── Fuera de dominio (sección 7) ─────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "cuánto es 2 + 2",
        "CUANTO ES 2 MAS 2",
        "cuál es la capital de Francia",
        "cuéntame un chiste",
        "escribe un poema",
        "quién ganó el partido de ayer",
        "ayúdame con mi tarea de matemáticas",
        "explícame álgebra",
        // FASE 12.9 — aritmética con números escritos en palabras (antes se colaba como
        // "mensaje ambiguo corto" y llegaba a Gemini en vez de ser rechazada localmente).
        "cuanto es dos por dos",
        "cuánto es dos más dos",
        "tres menos uno"
    })
    void mensajesFueraDeDominio_isInDomainFalse(String mensaje) {
        assertThat(guard.isInDomain(mensaje)).isFalse();
    }

    // ── Dentro de dominio (sección 8, incluye ambiguos) ──────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "¿Cómo estuvo el último sprint?",
        "Analiza el sprint activo",
        "¿Qué riesgos detectas?",
        "¿Qué deberíamos revisar en la retrospectiva?",
        "Compara los últimos sprints",
        "¿Cómo están los defectos?",
        "¿Cómo está la deuda técnica?",
        "¿Qué métricas están empeorando?",
        "¿Qué deberíamos mejorar?",
        "¿Cómo vamos?",
        "¿Hay problemas?",
        "¿Qué pasó?"
    })
    void mensajesDentroDeDominio_isInDomainTrue(String mensaje) {
        assertThat(guard.isInDomain(mensaje)).isTrue();
    }

    // ── Regla de prioridad (sección 4): señal ajena vence a palabra del dominio ──

    @Test
    void chisteSobreElSprint_esFueraDeDominio() {
        assertThat(guard.isInDomain("Cuéntame un chiste sobre el sprint")).isFalse();
    }

    @Test
    void explicarMatematicasUsandoDefectos_esFueraDeDominio() {
        assertThat(guard.isInDomain("Explícame matemáticas usando defectos")).isFalse();
    }

    // ── Normalización (sección 11): mayúsculas/tildes/puntuación no cambian el resultado ──

    @Test
    void normalizacion_mayusculasTildesYPuntuacionNoAlteranClasificacion() {
        boolean conMayusculasYTildes = guard.isInDomain("¿CÓMO ESTÁ LA DEUDA TÉCNICA?");
        boolean sinAcentosNiSignos = guard.isInDomain("como esta la deuda tecnica");

        assertThat(conMayusculasYTildes).isTrue();
        assertThat(sinAcentosNiSignos).isTrue();
        assertThat(conMayusculasYTildes).isEqualTo(sinAcentosNiSignos);
    }

    @Test
    void normalizacion_espaciosRepetidosNoAlteranClasificacion() {
        assertThat(guard.isInDomain("cuánto    es      2   +   2")).isFalse();
    }

    // ── Casos límite ──────────────────────────────────────────────────────

    @Test
    void mensajeVacio_esFueraDeDominio() {
        assertThat(guard.isInDomain("")).isFalse();
        assertThat(guard.isInDomain("   ")).isFalse();
    }

    @Test
    void mensajeNulo_esFueraDeDominio() {
        assertThat(guard.isInDomain(null)).isFalse();
    }

    @Test
    void respuestaFueraDeDominio_mencionaSprintYMetricas() {
        String respuesta = guard.respuestaFueraDeDominio();
        assertThat(respuesta).contains("AI Agile Copilot de MPDIA");
        assertThat(respuesta).contains("sprint");
        assertThat(respuesta).contains("métricas");
    }
}
