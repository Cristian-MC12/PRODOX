// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regla ÚNICA de PRODOX para decidir los identificadores técnicos (Variable.nombre) de
 * una parametrización aprobada. La usan los tres caminos que crean variables versionadas:
 * Verificación (MetricRankingService), flujo académico (ParametrizacionService) y
 * materialización bajo demanda (VariableDinamicaService) — igual para una métrica del
 * catálogo que para una creada por el usuario, porque ambas son filas de `metricas` con
 * la misma información estructurada (nombre, descripcion, codigo, factor, categoria).
 *
 * Orden de prioridad:
 * 1. Identificador(es) técnico(s) explícito(s) válido(s) — lista separada por comas
 *    permitida ("acat, acr").
 * 2. indicadorVariable que YA es una lista de identificadores técnicos
 *    ("deuda_gestionada, deuda_identificada").
 * 3. Identificadores snake_case escritos dentro del indicador en prosa
 *    ("Número de problema_reportado_individual por sprint") — compatibilidad con la
 *    extracción previa, necesaria para fórmulas que referencian esos nombres.
 * 4. Nombre de la métrica (dato estructurado, presente en toda métrica) → identificador
 *    corto: "Índice de Colaboración Percibida del Equipo (ICPE)" →
 *    "colaboracion_percibida_equipo".
 * 5. Indicador en prosa → identificador corto (solo si no hay nombre de métrica).
 *
 * En texto libre la coma es puntuación, nunca separador de variables: una escala Likert
 * "(1=totalmente en desacuerdo, 5=totalmente de acuerdo)" no crea variables extra.
 * No se usa hash: la unicidad real de Variable.nombre es por (parametrización, versión,
 * nombre) — índice ux_variables_parametrizacion_version_nombre (V26) — y un identificador
 * generado es siempre uno solo por parametrización, así que no puede colisionar.
 * Metrica.codigo NO se usa: no es semántico ("CAL-DEF", "IA-007") ni válido como
 * snake_case.
 */
final class NombreVariableGenerador {

    /** Máximo de palabras significativas de un identificador generado. */
    static final int MAX_PALABRAS = 3;

    private static final Pattern IDENTIFICADOR_EMBEBIDO =
            Pattern.compile("(?<![A-Za-z0-9_])[a-z][a-z0-9]*(?:_[a-z0-9]+)+(?![A-Za-z0-9_])");

    /** Contenido entre paréntesis (acrónimos, escalas); también un paréntesis sin cerrar. */
    private static final Pattern PARENTESIS = Pattern.compile("\\([^)]*\\)?");

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");

    private static final Set<String> PALABRAS_VACIAS = Set.of(
            "a", "al", "ante", "con", "contra", "de", "del", "desde", "durante", "e", "el", "en",
            "entre", "es", "esta", "estas", "este", "esto", "estos", "hacia", "hasta", "la", "las",
            "lo", "los", "o", "para", "por", "que", "se", "segun", "sin", "sobre", "su", "sus",
            "tan", "u", "un", "una", "unas", "uno", "unos", "y", "cada", "como", "cual", "cuanto",
            "muy", "mas", "the", "of", "and", "per");

    /**
     * Palabras genéricas de medición: se descartan solo al PRINCIPIO del texto y solo si
     * queda al menos una palabra con contenido; en otra posición aportan significado.
     */
    private static final Set<String> PALABRAS_GENERICAS = Set.of(
            "indice", "nivel", "numero", "cantidad", "total", "grado", "porcentaje", "tasa",
            "valor", "respuesta", "pregunta", "medida", "medicion", "promedio", "suma", "conteo",
            "escala", "puntaje", "puntuacion", "ratio");

    private NombreVariableGenerador() {
    }

    /**
     * Identificadores técnicos (1 o N) para una parametrización. Nunca devuelve una
     * lista vacía ni un identificador inválido.
     */
    static List<String> resolver(String nombreVariableExplicito, String indicadorVariable, String nombreMetrica) {
        if (esListaDeIdentificadores(nombreVariableExplicito)) {
            return separarLista(nombreVariableExplicito);
        }
        if (nombreVariableExplicito != null && !nombreVariableExplicito.isBlank()) {
            // El usuario escribió algo, pero no es un identificador válido (ej.
            // "Colaboración percibida"): se respeta su intención normalizándolo.
            String normalizado = generarIdentificadorCorto(nombreVariableExplicito);
            if (normalizado != null) {
                return List.of(normalizado);
            }
        }
        if (esListaDeIdentificadores(indicadorVariable)) {
            return separarLista(indicadorVariable);
        }
        List<String> embebidos = identificadoresEmbebidos(indicadorVariable);
        if (!embebidos.isEmpty()) {
            return embebidos;
        }
        String desdeMetrica = generarIdentificadorCorto(nombreMetrica);
        if (desdeMetrica != null) {
            return List.of(desdeMetrica);
        }
        String desdeIndicador = generarIdentificadorCorto(indicadorVariable);
        return List.of(desdeIndicador != null ? desdeIndicador : "valor");
    }

    /** true si texto es un identificador técnico válido o una lista de ellos separada por comas. */
    static boolean esListaDeIdentificadores(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        try {
            ParametrizacionService.validarNombreVariable(texto);
            return true;
        } catch (NombreVariableInvalidoException e) {
            return false;
        }
    }

    /**
     * Identificador corto, estable y determinista a partir de texto libre: sin tildes
     * (normalización Unicode NFD), sin paréntesis, sin palabras vacías ni repetidas,
     * máximo MAX_PALABRAS palabras significativas. null si el texto no aporta nada.
     *
     * - Las palabras genéricas de medición ("índice", "nivel", "número"...) solo se
     *   descartan como PREFIJO ("Índice de satisfacción del equipo" ->
     *   satisfaccion_equipo). En cualquier otra posición aportan significado y se
     *   conservan ("Velocidad con valor" -> velocidad_valor, distinto de "Velocidad").
     * - Las palabras repetidas se conservan una sola vez, en el orden de su primera
     *   aparición ("Tasa de éxito, éxito y más éxito" -> exito).
     * - Los números se descartan cuando hay palabras, pero se conservan si son la única
     *   información del texto ("360°" -> v_360).
     */
    static String generarIdentificadorCorto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String sinParentesis = PARENTESIS.matcher(texto).replaceAll(" ");
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(sinParentesis, Normalizer.Form.NFD))
                .replaceAll("");
        String[] tokens = sinTildes.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");

        Set<String> palabras = new LinkedHashSet<>();
        Set<String> numeros = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.isEmpty() || PALABRAS_VACIAS.contains(token)) {
                continue;
            }
            (token.chars().allMatch(Character::isDigit) ? numeros : palabras).add(token);
        }
        List<String> contenido = new ArrayList<>(palabras.isEmpty() ? numeros : palabras);
        if (contenido.isEmpty()) {
            return null;
        }
        int inicio = 0;
        while (inicio < contenido.size() && PALABRAS_GENERICAS.contains(contenido.get(inicio))) {
            inicio++;
        }
        List<String> candidatas = inicio < contenido.size() ? contenido.subList(inicio, contenido.size()) : contenido;
        List<String> elegidas = candidatas.subList(0, Math.min(MAX_PALABRAS, candidatas.size()));

        String identificador = String.join("_", elegidas);
        if (!Character.isLetter(identificador.charAt(0))) {
            identificador = "v_" + identificador;
        }
        if (identificador.length() > 120) {
            identificador = identificador.substring(0, 120).replaceAll("_+$", "");
        }
        return identificador;
    }

    private static List<String> separarLista(String lista) {
        return Arrays.stream(lista.split(",")).map(String::trim).toList();
    }

    private static List<String> identificadoresEmbebidos(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        Set<String> encontrados = new LinkedHashSet<>();
        Matcher m = IDENTIFICADOR_EMBEBIDO.matcher(texto);
        while (m.find()) {
            if (m.group().length() <= 120) {
                encontrados.add(m.group());
            }
        }
        return List.copyOf(encontrados);
    }
}
