// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.MetricaDto;
import com.mpdia.dto.PosibleDuplicadoDto;
import com.mpdia.entity.Metrica;
import com.mpdia.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

/**
 * FASE 23 — detección de posibles duplicados CONCEPTUALES en el catálogo
 * global de métricas, como alternativa a ocultar métricas históricas con un
 * flag (visible_en_catalogo): en vez de esconder lo ya creado, se evita que
 * se sigan creando nuevas variantes del mismo concepto hacia adelante.
 *
 * Es una capa ADICIONAL al chequeo de nombre EXACTO existente
 * (MetricaRepository.findByNombreIgnoreCaseTrimmed, usado en
 * MetricaIAService), nunca lo reemplaza: ese chequeo sigue bloqueando
 * siempre que el nombre normalizado coincida. Esta clase solo entra en juego
 * cuando el nombre NO coincide exactamente, para detectar variantes como
 * "Estado de ánimo" / "Clima emocional del equipo" / "Pulso de ánimo del
 * equipo", que muy probablemente miden lo mismo aunque compartan pocas o
 * ninguna palabra literal en el nombre.
 *
 * Diseño deliberadamente simple y explicable (sin dependencias externas,
 * sin embeddings/IA): superposición de palabras (Jaccard) sobre nombre y
 * sobre el texto de "significado" (descripción + objetivo + qué mide +
 * variables sugeridas, cuando estén disponibles), más una señal de
 * categoría, combinadas con pesos fijos. Cada coincidencia devuelve el
 * motivo exacto por el que se considera similar — nunca una caja negra.
 *
 * SINONIMOS es un mapa pequeño y curado, no un tesauro exhaustivo: cubre el
 * vocabulario de "ánimo/clima/moral de equipo" que motivó esta fase (ver
 * catálogo real: IA-001 "Clima Emocional", IA-002 "Pulso de Ánimo",
 * IA-003/IA-007 "Estado de Ánimo"). Se puede ampliar más adelante sin tocar
 * el algoritmo.
 */
@Service
@RequiredArgsConstructor
public class MetricaSimilitudService {

    /** Por debajo de este puntaje (0-100) no se considera un posible duplicado. */
    static final int UMBRAL_POSIBLE_DUPLICADO = 30;

    private static final double PESO_NOMBRE = 0.45;
    private static final double PESO_DESCRIPCION = 0.40;
    private static final double PESO_CATEGORIA = 0.15;

    /**
     * Palabras que no distinguen concepto en este catálogo: artículos,
     * preposiciones, y términos administrativos/genéricos que aparecen en
     * casi cualquier métrica del dominio (p.ej. "equipo", "sprint") o en el
     * texto de respaldo que se genera cuando Gemini no pudo determinar una
     * descripción ("Propuesta generada automáticamente a partir de...").
     */
    private static final Set<String> STOPWORDS = Set.of(
            "de", "del", "la", "el", "los", "las", "un", "una", "y", "en", "por", "para",
            "que", "es", "al", "a", "su", "sus", "sobre", "respecto", "se", "lo", "con",
            "sin", "no", "este", "esta", "general", "durante", "mide", "medir",
            "equipo", "sprint", "editado", "sm",
            "propuesta", "generada", "automaticamente", "partir", "quiero",
            "requiere", "validacion", "ajuste", "determinado"
    );

    /**
     * Sinónimos superficiales curados a mano para el concepto "ánimo del
     * equipo", el caso concreto que motivó esta fase. Palabras distintas que
     * refieren al mismo concepto se normalizan a un token común ANTES de
     * comparar, para no depender solo de coincidencia literal de palabras.
     */
    private static final Map<String, String> SINONIMOS = Map.of(
            "animo", "animo_equipo",
            "moral", "animo_equipo",
            "clima", "animo_equipo",
            "emocional", "animo_equipo",
            "humor", "animo_equipo",
            "pulso", "animo_equipo",
            "sentimiento", "animo_equipo"
    );

    private final MetricaRepository metricaRepository;

    public List<PosibleDuplicadoDto> buscarPosiblesDuplicados(
            String nombre, String descripcion, Short categoriaId,
            String objetivo, String queMide, String variablesSugeridas) {

        List<Metrica> catalogo = metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc();
        if (catalogo.isEmpty()) {
            return List.of();
        }

        Set<String> tokensNombreNueva = tokenizar(nombre);
        String textoSignificado = String.join(" ",
                vacioSiNulo(descripcion), vacioSiNulo(objetivo),
                vacioSiNulo(queMide), vacioSiNulo(variablesSugeridas));
        Set<String> tokensSignificadoNueva = tokenizar(textoSignificado);

        List<PosibleDuplicadoDto> candidatos = new ArrayList<>();
        for (Metrica m : catalogo) {
            double scoreNombre = jaccard(tokensNombreNueva, tokenizar(m.getNombre()));
            double scoreDescripcion = jaccard(tokensSignificadoNueva, tokenizar(m.getDescripcion()));
            boolean mismaCategoria = categoriaId != null && m.getCategoria() != null
                    && categoriaId.equals(m.getCategoria().getId());

            double total = scoreNombre * PESO_NOMBRE
                    + scoreDescripcion * PESO_DESCRIPCION
                    + (mismaCategoria ? PESO_CATEGORIA : 0.0);
            int scorePorcentaje = (int) Math.round(total * 100);

            // Causa raíz de un duplicado real encontrado en catálogo ("Defectos" vs.
            // "Defectos encontrados", categorías 1 y 3): el formulario de Crear métrica
            // con IA no le pide categoría a Gemini y siempre parte de categoriaId=1 hasta
            // que el Scrum Master la cambia manualmente — si no coincide con la categoría
            // real de la métrica existente, PESO_CATEGORIA (15 pts) no suma y el score
            // total cae por debajo del umbral aunque el NOMBRE por sí solo ya sea muy
            // similar (Jaccard 0.5, ej. "defectos" ⊂ "defectos encontrados"). Un nombre
            // fuertemente similar debe bastar por sí mismo para avisar, sin depender de
            // que la categoría (nunca sugerida por la IA) también coincida.
            boolean nombreMuySimilar = scoreNombre >= 0.5;
            if (scorePorcentaje < UMBRAL_POSIBLE_DUPLICADO && !nombreMuySimilar) {
                continue;
            }

            List<String> razones = new ArrayList<>();
            if (scoreNombre >= 0.5) {
                razones.add("nombre muy similar");
            } else if (scoreNombre > 0) {
                razones.add("nombre parcialmente similar");
            }
            if (scoreDescripcion >= 0.3) {
                razones.add("descripción/intención muy similar");
            } else if (scoreDescripcion > 0) {
                razones.add("descripción/intención parcialmente similar");
            }
            if (mismaCategoria) {
                razones.add("misma categoría");
            }

            candidatos.add(new PosibleDuplicadoDto(toMetricaDto(m), scorePorcentaje, razones));
        }

        candidatos.sort(Comparator.comparingInt(PosibleDuplicadoDto::score).reversed());
        return candidatos;
    }

    private MetricaDto toMetricaDto(Metrica m) {
        return new MetricaDto(m.getId(), m.getCodigo(), m.getNombre(), m.getDescripcion(), m.getCategoria().getNombre());
    }

    private String vacioSiNulo(String s) {
        return s == null ? "" : s;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> interseccion = new HashSet<>(a);
        interseccion.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) interseccion.size() / union.size();
    }

    private Set<String> tokenizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Set.of();
        }
        String sinAcentos = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String[] palabras = sinAcentos.replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");

        Set<String> tokens = new LinkedHashSet<>();
        for (String palabra : palabras) {
            if (palabra.isBlank() || STOPWORDS.contains(palabra)) {
                continue;
            }
            tokens.add(SINONIMOS.getOrDefault(palabra, palabra));
        }
        return tokens;
    }
}
