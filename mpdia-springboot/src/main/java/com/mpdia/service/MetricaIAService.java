// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.CrearMetricaIARequest;
import com.mpdia.dto.MetricaDto;
import com.mpdia.dto.MetricaIACreadaDto;
import com.mpdia.dto.MetricaIAPropuestaDto;
import com.mpdia.dto.PosibleDuplicadoDto;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricaCategoria;
import com.mpdia.repository.MetricaCategoriaRepository;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FASE 15 — "Crear métrica con IA".
 *
 * Regla fundamental (ver FASE 15, autorización de implementación): LA IA
 * PROPONE, EL SCRUM MASTER DECIDE. generarPropuesta() nunca escribe en la
 * base de datos — igual que ParametrizacionService.generarPropuestas() y
 * MetricaAcademicaService.generarPropuestaAcademica(), a los que este método
 * sigue exactamente el mismo patrón (prompt → Gemini.generate() → parseo
 * JSON defensivo → DTO, con fallback que nunca falla).
 *
 * crearDesdeConfirmacion() es la ÚNICA operación de esta clase que persiste
 * algo, y solo se ejecuta cuando el Scrum Master confirma explícitamente
 * ("Usar esta propuesta") con la versión ya revisada/editada. Crea
 * exactamente una fila Metrica (no existe ningún otro punto en el sistema
 * que cree métricas del catálogo en tiempo de ejecución — ver diagnóstico
 * FASE 15) y reutiliza PlaneacionService.seleccionar() sin duplicar su
 * lógica. No aprueba nada: la métrica queda en el mismo estado
 * "seleccionada, no aprobada" que cualquier métrica recién agregada por un
 * Scrum Master, y debe pasar por el flujo existente de
 * parametrización → verificación → aprobación para poder usarse oficialmente.
 */
@Service
@RequiredArgsConstructor
public class MetricaIAService {

    private static final int MAX_INTENTOS_CODIGO = 5;

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final MetricaRepository metricaRepository;
    private final MetricaCategoriaRepository metricaCategoriaRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PlaneacionService planeacionService;
    private final MetricaSimilitudService metricaSimilitudService;

    public MetricaIAPropuestaDto generarPropuesta(String necesidad) {
        String prompt = buildPrompt(necesidad);
        try {
            String raw = geminiService.generate(prompt);
            return parsePropuesta(raw);
        } catch (Exception e) {
            // FASE 19: antes, cualquier fallo de Gemini (503/429/timeout/JSON
            // inválido) se disfrazaba como una propuesta "válida" con todos los
            // campos en "No determinado" — indistinguible para el Scrum Master
            // de una respuesta legítima de la IA. La regla fundamental es que
            // la IA SOLO propone: un fallo real nunca puede convertirse en algo
            // que el usuario apruebe pensando que fue generado correctamente.
            // El detalle técnico queda en el log; el mensaje de la excepción es
            // el que se le muestra al usuario tal cual (GlobalExceptionHandler).
            System.err.println("=== ERROR GEMINI (MetricaIAService.generarPropuesta) ===");
            System.err.println(e.getMessage());
            System.err.println("=========================================================");
            throw new PropuestaIANoDisponibleException(
                    "La IA no pudo generar una propuesta en este momento. Intentá nuevamente en unos segundos.",
                    e);
        }
    }

    @Transactional
    public MetricaIACreadaDto crearDesdeConfirmacion(CrearMetricaIARequest request) {
        String userId = getUserId();
        validarMiembroProyecto(userId, request.proyectoId());

        MetricaCategoria categoria = metricaCategoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + request.categoriaId()));

        // Metrica es el catálogo GLOBAL: antes de crear una fila nueva, se busca
        // si ya existe una con este nombre normalizado. Si existe, NUNCA se crea
        // una segunda — se informa al frontend para que ofrezca reutilizarla
        // (asociándola al proyecto vía PlaneacionService.seleccionar, el mismo
        // flujo que ya usa cualquier métrica del catálogo).
        Optional<Metrica> existente = metricaRepository.findByNombreIgnoreCaseTrimmed(request.nombre());
        if (existente.isPresent()) {
            throw new MetricaDuplicadaEnCatalogoException(
                    "Ya existe una métrica en el catálogo con el nombre '" + request.nombre() +
                    "'. Podés reutilizar la métrica existente en vez de crear una nueva.",
                    toMetricaDto(existente.get()));
        }

        // FASE 23: sin nombre exactamente igual, se busca además si el catálogo ya
        // tiene una métrica que probablemente mide el mismo CONCEPTO (nombre,
        // descripción, objetivo, qué mide, variables sugeridas y categoría — nunca
        // solo palabras del nombre). No bloquea la creación: solo se omite si el
        // Scrum Master ya confirmó explícitamente que quiere crearla como distinta
        // (confirmarCreacionDiferente=true, tras haber visto el aviso).
        if (!Boolean.TRUE.equals(request.confirmarCreacionDiferente())) {
            List<PosibleDuplicadoDto> posibles = metricaSimilitudService.buscarPosiblesDuplicados(
                    request.nombre(), request.descripcion(), request.categoriaId(),
                    request.objetivo(), request.queMide(), request.variablesSugeridas());
            if (!posibles.isEmpty()) {
                throw new MetricaPosibleDuplicadaException(
                        "Ya existe una métrica en el catálogo que parece medir un concepto similar.",
                        posibles);
            }
        }

        Metrica metrica = new Metrica();
        metrica.setCategoria(categoria);
        metrica.setFactor("Creado con IA");
        metrica.setNombre(request.nombre());
        metrica.setDescripcion(request.descripcion());
        metrica.setCodigo(generarCodigoUnico());

        Metrica guardada = guardarConReintentoDeCodigo(metrica);

        planeacionService.seleccionar(request.proyectoId(), guardada.getId());

        return new MetricaIACreadaDto(guardada.getId(), guardada.getCodigo(), guardada.getNombre(), request.proyectoId());
    }

    /**
     * nextval() sobre metrica_ia_codigo_seq (V27) ya es atómico y nunca repite
     * un valor entre transacciones concurrentes — pero por si un código
     * "IA-NNN" ya existiera por otra vía (ej. inserción manual fuera de esta
     * secuencia), se reintenta con un nuevo valor en vez de asumir que un
     * único intento basta.
     *
     * Además cubre la condición de carrera real de nombre duplicado: si dos
     * proyectos confirman simultáneamente la misma métrica nueva, el chequeo
     * previo (findByNombreIgnoreCaseTrimmed) puede no detectarlo — el índice
     * único ux_metricas_nombre_global (V31) sí, al momento del INSERT. En ese
     * caso no se reintenta con otro código (el nombre seguiría chocando): se
     * relee la fila que ganó la carrera y se informa como reutilizable, igual
     * que el caso no-concurrente de arriba.
     */
    private Metrica guardarConReintentoDeCodigo(Metrica metrica) {
        DataIntegrityViolationException ultimoError = null;
        for (int intento = 0; intento < MAX_INTENTOS_CODIGO; intento++) {
            try {
                return metricaRepository.save(metrica);
            } catch (DataIntegrityViolationException e) {
                if (esViolacionDeNombreDuplicado(e)) {
                    Metrica ganadora = metricaRepository.findByNombreIgnoreCaseTrimmed(metrica.getNombre())
                            .orElseThrow(() -> e);
                    throw new MetricaDuplicadaEnCatalogoException(
                            "Ya existe una métrica en el catálogo con el nombre '" + metrica.getNombre() +
                            "'. Podés reutilizar la métrica existente en vez de crear una nueva.",
                            toMetricaDto(ganadora));
                }
                ultimoError = e;
                metrica.setCodigo(generarCodigoUnico());
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un código único para la métrica tras " + MAX_INTENTOS_CODIGO + " intentos.",
                ultimoError);
    }

    private boolean esViolacionDeNombreDuplicado(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        return msg != null && msg.contains("ux_metricas_nombre_global");
    }

    private MetricaDto toMetricaDto(Metrica m) {
        return new MetricaDto(m.getId(), m.getCodigo(), m.getNombre(), m.getDescripcion(), m.getCategoria().getNombre());
    }

    private String generarCodigoUnico() {
        String codigo;
        int intentos = 0;
        do {
            long siguiente = metricaRepository.siguienteValorSecuenciaCodigoIA();
            codigo = "IA-" + String.format("%03d", siguiente);
            intentos++;
        } while (metricaRepository.existsByCodigo(codigo) && intentos < MAX_INTENTOS_CODIGO);
        return codigo;
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return auth.getName();
    }

    private void validarMiembroProyecto(String userId, UUID proyectoId) {
        boolean esMiembro = projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId);
        if (!esMiembro) {
            throw new IllegalStateException("Usuario no pertenece al proyecto");
        }
    }

    private String buildPrompt(String necesidad) {
        return """
            Eres un asistente experto en métricas ágiles/Scrum que ayuda a un Scrum Master a
            estructurar una NUEVA métrica que todavía no existe en el catálogo de su equipo.

            El Scrum Master describió esta necesidad, en sus propias palabras:
            \"""" + necesidad + """
            \"

            Tu tarea es proponer una estructura inicial para esta métrica. Esto es SOLO una
            propuesta: el Scrum Master la revisará, podrá modificar cualquier campo, y solo
            después de su confirmación explícita se creará realmente.

            REGLAS IMPORTANTES:
            1. NO inventes datos, resultados históricos ni benchmarks.
            2. Si no puedes determinar razonablemente un campo con la información dada,
               escribe EXACTAMENTE "No determinado" en ese campo — nunca inventes un valor
               solo para rellenarlo.
            3. Prioriza la SIMPLICIDAD y que sea PRÁCTICA de aplicar por un equipo Scrum real.
            4. tipoOperacionSugerido, si lo propones, debe ser EXACTAMENTE uno de: SUMA,
               PROMEDIO, DIRECTO, FORMULA (o "No determinado" si no aplica claramente).
            5. nombre debe ser corto (máximo 8 palabras), sin inventar siglas técnicas.

            Responde ÚNICAMENTE con un objeto JSON, sin texto adicional, sin markdown, sin
            explicaciones fuera del JSON:
            {
              "nombre": "Nombre corto de la métrica propuesta",
              "descripcion": "Descripción breve de qué es esta métrica",
              "objetivo": "Qué se busca lograr midiendo esto en el sprint",
              "queMide": "Qué representa concretamente el valor medido",
              "variablesSugeridas": "Variable(s) que habría que capturar para calcularla",
              "tipoOperacionSugerido": "SUMA | PROMEDIO | DIRECTO | FORMULA | No determinado",
              "formulaSugerida": "Fórmula o procedimiento sugerido, si aplica, o No determinado",
              "unidadResultado": "Unidad del resultado (ej: puntos, %, horas) o No determinado",
              "fuenteSugerida": "Referencia académica/práctica verificable, o No determinado"
            }
            """;
    }

    private MetricaIAPropuestaDto parsePropuesta(String raw) {
        String cleaned = raw
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}') + 1;
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end);
        }
        try {
            return objectMapper.readValue(cleaned, MetricaIAPropuestaDto.class);
        } catch (Exception e) {
            System.err.println("Error parseando propuesta de métrica IA: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
