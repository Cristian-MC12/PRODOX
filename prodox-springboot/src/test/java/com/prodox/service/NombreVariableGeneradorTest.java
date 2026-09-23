// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla general de identificadores técnicos de variables (NombreVariableGenerador),
 * compartida por Verificación, flujo académico y materialización bajo demanda.
 */
class NombreVariableGeneradorTest {

    private static final String PATRON_VALIDO = "^[a-z][a-z0-9_]{0,119}$";
    private static final String HASH_VISIBLE = ".*_[0-9a-f]{10}$";

    private static final String INDICADOR_ICPE =
            "Respuesta a la pregunta ¿Qué tan de acuerdo estás con que existe un alto nivel de "
            + "colaboración y apoyo mutuo en el equipo? (1=totalmente en desacuerdo, 5=totalmente de acuerdo)";

    @Test
    @DisplayName("Regresión bug original: indicador Likert con coma -> 1 variable, no 2")
    void likertConComa_unaSolaVariable() {
        List<String> nombres = NombreVariableGenerador.resolver(null, INDICADOR_ICPE,
                "Índice de Colaboración Percibida del Equipo (ICPE)");

        assertThat(nombres).containsExactly("colaboracion_percibida_equipo");
    }

    @Test
    @DisplayName("TEST 10 / segundo problema: el identificador nunca es la frase completa ni lleva hash")
    void nuncaFraseCompletaNiHash() {
        String nombre = NombreVariableGenerador.resolver(null, INDICADOR_ICPE,
                "Índice de Colaboración Percibida del Equipo (ICPE)").get(0);

        assertThat(nombre).doesNotStartWith("respuesta_a_la_pregunta").doesNotMatch(HASH_VISIBLE);
    }

    @Test
    @DisplayName("Una escala en texto libre con coma no crea variables extra")
    void escalaEnTextoLibre_unaVariable() {
        assertThat(NombreVariableGenerador.resolver(null,
                "Nivel de satisfacción del equipo (1=muy bajo, 5=muy alto)", "Satisfacción del equipo"))
                .containsExactly("satisfaccion_equipo");
    }

    @Test
    @DisplayName("TEST 5: lista explícita de 3 identificadores -> 3 variables")
    void listaDeTres() {
        assertThat(NombreVariableGenerador.resolver(null,
                "apoyo_recibido, apoyo_brindado, participacion_activa", "Colaboración"))
                .containsExactly("apoyo_recibido", "apoyo_brindado", "participacion_activa");
    }

    @Test
    @DisplayName("TEST 6: lista explícita de 2 identificadores -> 2 variables")
    void listaDeDos() {
        assertThat(NombreVariableGenerador.resolver(null, "deuda_gestionada, deuda_identificada", "Deuda técnica"))
                .containsExactly("deuda_gestionada", "deuda_identificada");
    }

    @Test
    @DisplayName("TEST 7: identificador manual válido tiene prioridad sobre indicador y nombre de métrica")
    void identificadorManualTienePrioridad() {
        assertThat(NombreVariableGenerador.resolver("colaboracion_percibida", INDICADOR_ICPE,
                "Índice de Colaboración Percibida del Equipo (ICPE)"))
                .containsExactly("colaboracion_percibida");
    }

    @Test
    @DisplayName("Lista manual de identificadores tiene prioridad (FORMULA de varias variables)")
    void listaManualTienePrioridad() {
        assertThat(NombreVariableGenerador.resolver("acat, acr", "texto libre", "Aprendizaje organizacional (FAT)"))
                .containsExactly("acat", "acr");
    }

    @Test
    @DisplayName("Identificador snake_case escrito dentro de un indicador en prosa se respeta (compatibilidad SIG-SC-02)")
    void identificadorEmbebidoEnProsa() {
        assertThat(NombreVariableGenerador.resolver(null,
                "Problemas_Reportados_Cliente_Sprint (variable: 'problema_reportado_individual')", "Problemas reportados"))
                .containsExactly("problema_reportado_individual");
    }

    @ParameterizedTest(name = "TEST 2/3/8: \"{0}\" -> {1}")
    @DisplayName("Nombres de métrica del catálogo y nuevos -> identificador corto, válido y sin tildes")
    @CsvSource(delimiter = '|', value = {
        "Índice de Colaboración Percibida del Equipo (ICPE) | colaboracion_percibida_equipo",
        "Índice de satisfacción del equipo                  | satisfaccion_equipo",
        "Errores por sprint                                  | errores_sprint",
        "Velocidad                                           | velocidad",
        "Estado de ánimo                                     | estado_animo",
        "Aprendizaje organizacional (FAT)                    | aprendizaje_organizacional",
        "Participación activa en ceremonias                  | participacion_activa_ceremonias",
        "Comprensión de roles                                | comprension_roles",
        "Nivel                                               | nivel",
        "Año de adopción ñandú                               | ano_adopcion_nandu"
    })
    void nombreDeMetrica_identificadorCorto(String nombreMetrica, String esperado) {
        String nombre = NombreVariableGenerador.resolver(null, "Texto libre del indicador, con coma", nombreMetrica).get(0);

        assertThat(nombre).isEqualTo(esperado).matches(PATRON_VALIDO);
    }

    @Test
    @DisplayName("TEST 9: texto largo -> identificador corto (máx. 3 palabras) y válido")
    void textoLargo_identificadorCorto() {
        String largo = "Medición del grado de cumplimiento de los compromisos adquiridos por cada integrante "
                + "del equipo durante la planificación del sprint, considerando retrasos, bloqueos y dependencias";

        String nombre = NombreVariableGenerador.generarIdentificadorCorto(largo);

        assertThat(nombre).isEqualTo("cumplimiento_compromisos_adquiridos").matches(PATRON_VALIDO);
    }

    @Test
    @DisplayName("Sin nombre de métrica (parametrización por factor): se genera desde el indicador, corto y válido")
    void sinNombreDeMetrica_desdeIndicador() {
        List<String> nombres = NombreVariableGenerador.resolver(null,
                "Número de impedimentos que bloquearon al equipo (1=pocos, 5=muchos)", null);

        assertThat(nombres).containsExactly("impedimentos_bloquearon_equipo");
    }

    @Test
    @DisplayName("Sin ninguna información aprovechable: 'valor', nunca vacío ni inválido")
    void sinInformacion_valor() {
        assertThat(NombreVariableGenerador.resolver(null, "(1, 5)", null)).containsExactly("valor");
    }

    @Test
    @DisplayName("Texto que empieza por número recibe prefijo para ser un identificador válido")
    void empiezaPorNumero_prefijo() {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("360 grados")).isEqualTo("grados");
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("3d impresión")).isEqualTo("v_3d_impresion");
    }

    // ── Correcciones de la auditoría sobre las métricas reales (C4, E2, E3) ──────────

    @Test
    @DisplayName("C4: 'Velocidad' -> velocidad")
    void velocidad() {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("Velocidad")).isEqualTo("velocidad");
    }

    @Test
    @DisplayName("C4: 'Velocidad con valor' -> velocidad_valor (una palabra genérica fuera del prefijo aporta significado)")
    void velocidadConValor_noColisionaConVelocidad() {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("Velocidad con valor")).isEqualTo("velocidad_valor");
        assertThat(NombreVariableGenerador.resolver(null, "Suma de story points ponderados por valor", "Velocidad con valor"))
                .containsExactly("velocidad_valor")
                .doesNotContain(NombreVariableGenerador.generarIdentificadorCorto("Velocidad"));
    }

    @ParameterizedTest(name = "C1/C2 se mantiene: \"{0}\" -> {1}")
    @DisplayName("Las palabras genéricas se siguen descartando cuando son prefijo de medición")
    @CsvSource(delimiter = '|', value = {
        "Índice de satisfacción del equipo | satisfaccion_equipo",
        "Nivel de satisfacción del equipo  | satisfaccion_equipo",
        "Satisfacción del equipo           | satisfaccion_equipo",
        "Número de impedimentos del sprint | impedimentos_sprint",
        "Valor de Entregas Aceptadas       | entregas_aceptadas",
        "Índice del nivel de valor total   | indice_nivel_valor"
    })
    void genericasComoPrefijo(String nombre, String esperado) {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto(nombre)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("E2: palabras repetidas se conservan una sola vez, en el orden de su primera aparición")
    void palabrasRepetidas() {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("éxito éxito éxito")).isEqualTo("exito");
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("Tasa de éxito, éxito y más éxito")).isEqualTo("exito");
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("Calidad del código y calidad del proceso"))
                .isEqualTo("calidad_codigo_proceso");
    }

    @Test
    @DisplayName("E3: un nombre solo numérico conserva la información ('360°' -> v_360), no 'valor'")
    void soloNumeros_conservaInformacion() {
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("360°")).isEqualTo("v_360");
        assertThat(NombreVariableGenerador.resolver(null, null, "360°")).containsExactly("v_360");
        assertThat(NombreVariableGenerador.generarIdentificadorCorto("Evaluación 360")).isEqualTo("evaluacion");
    }

    @Test
    @DisplayName("Regresión: métrica de catálogo sin identificador técnico explícito (FSH-SAT-01)")
    void metricaCatalogoSinIdentificadorExplicito() {
        assertThat(NombreVariableGenerador.resolver(null, null, "Satisfacción del equipo"))
                .containsExactly("satisfaccion_equipo");
    }

    @Test
    @DisplayName("Regresión: métrica nueva (IA) sin identificador manual y con indicador en prosa")
    void metricaNuevaSinIdentificadorManual() {
        assertThat(NombreVariableGenerador.resolver(null,
                "Suma de Product Backlog Items aceptados por el Product Owner en el sprint", "Valor de Entregas Aceptadas"))
                .containsExactly("entregas_aceptadas");
    }

    @Test
    @DisplayName("Determinista: el mismo texto siempre produce el mismo identificador")
    void determinista() {
        assertThat(NombreVariableGenerador.resolver(null, INDICADOR_ICPE, "Índice de Colaboración Percibida del Equipo (ICPE)"))
                .isEqualTo(NombreVariableGenerador.resolver(null, INDICADOR_ICPE, "Índice de Colaboración Percibida del Equipo (ICPE)"));
    }
}
