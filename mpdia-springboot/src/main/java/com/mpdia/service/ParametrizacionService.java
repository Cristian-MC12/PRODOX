package com.mpdia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.AprobarParametrizacionRequest;
import com.mpdia.dto.GuardarPropuestaRequest;
import com.mpdia.dto.ParametrizacionRequest;
import com.mpdia.dto.PropuestaParametrizacionDto;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.ProjectMember;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.Variable;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.VariableRepository;
import com.mpdia.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Usa Gemini para generar 1 propuesta de parametrización.
 * Gestiona el ciclo de vida: propuesta → aprobación → versionado.
 * 
 * FASE 16.5: Gemini como asistente (1 propuesta)
 * FASE 16.6: Aprobación formal y versionado
 */
@Service
@RequiredArgsConstructor
public class ParametrizacionService {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final MetricParametrizacionRepository parametrizacionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final VariableRepository variableRepository;
    private final MetricaRepository metricaRepository;

    /**
     * Catálogo oficial de tipos de operación soportados por el motor de
     * cálculo determinista, según especifica el comentario de la migración
     * V24 (metric_parametrizaciones.tipo_operacion) y la especificación
     * metodológica de Fase 16.8.7 (docs/FASE16_8_7_ESPECIFICACION_METODOLOGICA.md).
     * NO agregar valores aquí sin implementar antes su cálculo real.
     */
    static final Set<String> TIPOS_OPERACION_SOPORTADOS = Set.of("SUMA", "PROMEDIO", "DIRECTO", "FORMULA");

    /**
     * Formato exigido para el identificador técnico de una variable
     * (Fase 16.10-E): snake_case corto, sin espacios/tildes/puntuación,
     * máximo 120 caracteres para caber en variables.nombre VARCHAR(120).
     * Se aplica tanto a nombreVariable (cuando Gemini/el usuario lo informan
     * explícitamente) como al resultado del fallback de extracción sobre
     * indicadorVariable, para que ningún nombre técnico llegue nunca a un
     * INSERT que pueda fallar con DataException/500 por overflow.
     */
    private static final java.util.regex.Pattern NOMBRE_VARIABLE_PATTERN =
        java.util.regex.Pattern.compile("^[a-z][a-z0-9_]{0,119}$");

    /**
     * tipoOperacion es un campo académico opcional (puede ser null para
     * parametrizaciones no académicas), pero si viene informado debe
     * pertenecer al catálogo soportado — de lo contrario la parametrización
     * quedaría aprobada sin que la ejecución pueda calcularla nunca.
     */
    private void validarTipoOperacion(String tipoOperacion) {
        if (tipoOperacion != null && !TIPOS_OPERACION_SOPORTADOS.contains(tipoOperacion)) {
            throw new TipoOperacionInvalidoException(
                "Tipo de operación no soportado: " + tipoOperacion +
                ". Valores permitidos: " + TIPOS_OPERACION_SOPORTADOS);
        }
    }

    /**
     * nombreVariable es opcional: si está ausente (null), la variable se
     * seguirá resolviendo mediante el fallback de extracción existente sobre
     * indicadorVariable (compatibilidad con parametrizaciones/flujos
     * anteriores a Fase 16.10-E). Pero si el llamador SÍ lo envía, debe ser
     * un identificador técnico válido — nunca una cadena vacía ni un texto
     * demasiado largo — porque de lo contrario la parametrización quedaría
     * aprobada sin poder materializar nunca su Variable.
     */
    /**
     * FASE 3 (Defectos/FAT/Deuda técnica): nombreVariable admite una lista
     * separada por comas cuando la fórmula académica necesita más de una
     * variable (ej. FAT: "acat,acr"). Cada nombre individual se valida con la
     * misma regla estricta que un nombre único — nunca se relaja el formato,
     * solo se permite declarar varios de una vez en el mismo campo explícito.
     * Un nombreVariable sin comas (caso de siempre) se comporta exactamente
     * igual que antes: una lista de un solo elemento.
     */
    private void validarNombreVariable(String nombreVariable) {
        if (nombreVariable == null) {
            return; // ausente: se resuelve más adelante por fallback
        }
        if (nombreVariable.isBlank()) {
            throw new NombreVariableInvalidoException(
                "nombreVariable no puede ser una cadena vacía. Si no se dispone de un " +
                "identificador técnico, omitir el campo (no enviar cadena vacía).");
        }
        for (String nombreIndividual : nombreVariable.split(",", -1)) {
            validarNombreVariableIndividual(nombreIndividual.trim(), nombreVariable);
        }
    }

    /**
     * FASE 13: visibilidad de paquete (no ya `private`) para que
     * VariableDinamicaService.crearVariablesDesdeParametrizacion() reutilice
     * exactamente esta misma regla — sin duplicar el patrón NOMBRE_VARIABLE_PATTERN
     * con un segundo comportamiento independiente (ver auditoría de Fase 12).
     */
    static void validarNombreVariableIndividual(String nombre, String nombreVariableOriginal) {
        if (nombre.isBlank()) {
            throw new NombreVariableInvalidoException(
                "nombreVariable contiene un elemento vacío en la lista separada por comas: '" +
                nombreVariableOriginal + "'.");
        }
        if (nombre.length() > 120) {
            throw new NombreVariableInvalidoException(
                "nombreVariable excede el máximo de 120 caracteres (longitud real: " +
                nombre.length() + "). Debe ser un identificador técnico corto " +
                "(ej: impedimentos_registrados), no una descripción o frase.");
        }
        if (!NOMBRE_VARIABLE_PATTERN.matcher(nombre).matches()) {
            throw new NombreVariableInvalidoException(
                "nombreVariable '" + nombre + "' no tiene formato técnico válido. " +
                "Debe ser snake_case: minúsculas, dígitos y guiones bajos, iniciar con letra, " +
                "sin espacios, sin tildes ni puntuación.");
        }
    }

    /**
     * Genera UNA propuesta de parametrización usando Gemini.
     * 
     * FASE 16.5: Evolucionado de "3 propuestas" a "asistente que propone 1 parametrización".
     * La IA actúa como asistente experto, no como generador de opciones múltiples.
     * 
     * Retorna List con 1 elemento para mantener compatibilidad con frontend.
     * En caso de error con Gemini, retorna propuesta genérica (nunca falla).
     */
    public List<PropuestaParametrizacionDto> generarPropuestas(ParametrizacionRequest request) {
        String prompt = buildPrompt(request);
        try {
            String raw = geminiService.generate(prompt);
            return parsePropuestas(raw, request);
        } catch (Exception e) {
            System.err.println("=== ERROR GEMINI ===");
            System.err.println(e.getMessage());
            System.err.println("===================");
            return fallbackPropuestas(request);
        }
    }

    private List<PropuestaParametrizacionDto> fallbackPropuestas(ParametrizacionRequest r) {
        return List.of(
            new PropuestaParametrizacionDto(
                "Medición directa por sprint",
                "Medir " + r.metricaNombre() + " de forma directa y práctica durante el sprint.",
                "Al cierre del sprint, el Scrum Master o equipo recopila el valor observado según criterios definidos por el equipo.",
                r.metricaNombre() + " (valor registrado según ocurrencia durante el sprint)",
                "Escala numérica (definir rango según contexto del equipo)",
                "por_sprint",
                "Propuesta generada automáticamente - requiere validación",
                "Σ(eventos_observados)",
                "SUMA",
                "unidades",
                "PROPUESTA de IA generada automáticamente. Requiere validación y ajuste del equipo según su contexto específico. Esta es una parametrización genérica que debe adaptarse.",
                "eventos_observados"
            )
        );
    }

    private String buildPrompt(ParametrizacionRequest r) {
        // Construcción segura del prompt sin usar .formatted() 
        // para evitar conflictos con caracteres % en el contenido
        String factorNombre = r.factorNombre();
        String factorCategoria = r.factorCategoria();
        String metricaNombre = r.metricaNombre();
        String metricaDescripcion = r.metricaDescripcion();
        
        return """
            Eres un asistente experto en métricas de productividad para equipos Scrum y metodologías ágiles.
            
            Tu objetivo es AYUDAR al equipo a parametrizar la siguiente métrica:
            
            Factor:      """ + factorNombre + " (Categoría: " + factorCategoria + ")" + """
            
            Métrica:     """ + metricaNombre + """
            
            Descripción: """ + metricaDescripcion + """
            
            
            Genera UNA propuesta estructurada de parametrización basándote en:
            - La definición de la métrica
            - Buenas prácticas de Scrum/Agile
            - Simplicidad y practicidad para el equipo
            
            REGLAS IMPORTANTES:
            1. NO inventes datos, resultados históricos o benchmarks
            2. NO afirmes que una escala es "oficial" sin fundamento
            3. Si algo no está definido claramente, propón algo razonable pero indica que es una PROPUESTA
            4. Prioriza la SIMPLICIDAD sobre la complejidad
            5. La parametrización debe ser PRÁCTICA y APLICABLE
            6. Diferencia claramente entre información existente y propuesta
            7. Para fuenteAcademica: solo propón si existe referencia verificable (Scrum Guide, libros reconocidos)
            8. formulaAcademica debe ser una fórmula formal matemática (ej: Σ(x), x/y, etc.)
            9. tipoOperacion debe ser EXACTAMENTE uno de estos 4 valores, sin excepción: SUMA, PROMEDIO, DIRECTO, FORMULA.
               NO inventes ni uses otros valores (por ejemplo CONTEO, PORCENTAJE, RATIO u OTRO no existen como operación soportada).
            10. Si la métrica describe un total o número ya agregado de eventos u ocurrencias durante el sprint
                (ej: "número de X", "cantidad de Y", "total de Z"), usa tipoOperacion=SUMA sobre el valor capturado.
            11. unidadResultado debe indicar la unidad del resultado (problemas, puntos, horas, porcentaje, etc.)
            12. nombreVariable debe ser un identificador técnico corto en snake_case (máximo 120
                caracteres, solo minúsculas/dígitos/guiones bajos, sin espacios, sin tildes, sin
                puntuación) que representa la variable principal usada para calcular la métrica.
                NO es una frase descriptiva, NO repite indicadorVariable textualmente, NO incluye
                explicaciones: es el nombre corto que usarías como identificador de variable de
                programación (ej: problemas_reportados, impedimentos_registrados, defectos_encontrados).

            Responde ÚNICAMENTE con un array JSON con UN objeto (para compatibilidad),
            sin texto adicional, sin markdown, sin explicaciones fuera del JSON:
            [
              {
                "titulo": "Nombre descriptivo de la parametrización (máx 8 palabras)",
                "objetivo": "Qué se quiere lograr midiendo esta métrica en el sprint",
                "procedimiento": "Fórmula o procedimiento paso a paso claro y específico para medir",
                "indicadorVariable": "Indicador principal y variables necesarias para el cálculo (texto descriptivo, para humanos)",
                "nombreVariable": "identificador_tecnico_snake_case_corto (ej: problemas_reportados)",
                "escala": "Escala de medición: numérica, porcentual, ordinal, etc. con rango específico",
                "frecuenciaCaptura": "por_sprint | semanal | diaria | ilimitada",
                "fuenteAcademica": "Referencia académica verificable o 'Propuesta basada en prácticas ágiles'",
                "formulaAcademica": "Fórmula matemática formal (ej: Σ(problemas_reportados))",
                "tipoOperacion": "SUMA | PROMEDIO | DIRECTO | FORMULA",
                "unidadResultado": "Unidad del resultado (problemas, puntos, horas, %, etc.)",
                "justificacion": "Por qué esta propuesta es adecuada para equipos Scrum (menciona que es una PROPUESTA que requiere validación del equipo)"
              }
            ]
            
            IMPORTANTE: Genera EXACTAMENTE 1 objeto en el array. Todos los campos son obligatorios.
            La justificación debe dejar claro que es una propuesta de IA que requiere validación humana.
            """;
    }

    private List<PropuestaParametrizacionDto> parsePropuestas(String raw, ParametrizacionRequest r) {
        try {
            String cleaned = raw
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
            int start = cleaned.indexOf('[');
            int end   = cleaned.lastIndexOf(']') + 1;
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end);
            }
            return objectMapper.readValue(cleaned,
                    new TypeReference<List<PropuestaParametrizacionDto>>() {});
        } catch (Exception e) {
            System.err.println("Error parseando respuesta de Gemini: " + e.getMessage());
            // Fallback con una propuesta genérica completa
            return List.of(new PropuestaParametrizacionDto(
                "Medición directa de " + r.metricaNombre(),
                "Medir " + r.metricaNombre() + " durante el sprint.",
                "Recopilar el valor al cierre del sprint y comparar con el objetivo.",
                r.metricaNombre() + " (variable principal)",
                "Escala numérica de 0 a 100",
                "por_sprint",
                "Propuesta basada en prácticas ágiles - requiere validación",
                "Σ(observaciones)",
                "SUMA",
                "unidades",
                "Propuesta generada automáticamente. Requiere validación del equipo.",
                "observaciones"
            ));
        }
    }
    
    /**
     * Guarda una propuesta de parametrización generada por IA.
     * Estado inicial: "propuesta" (no aprobada).
     * 
     * FASE 16.6: NO aprueba automáticamente, requiere acción explícita del usuario.
     */
    @Transactional
    public MetricParametrizacion guardarPropuesta(GuardarPropuestaRequest request) {
        // Validar autorización
        String userId = getUserId();
        String userEmail = getUserEmail();
        validarMiembroProyecto(userId, request.proyectoId());
        validarTipoOperacion(request.tipoOperacion());
        validarNombreVariable(request.nombreVariable());

        // FASE 16.10-F: la siguiente versión se calcula sobre la versión MÁXIMA
        // existente para esta métrica/proyecto, sin importar su status (propuesta,
        // aprobada o inactiva). findUltimaVersionAprobada() (usada antes aquí)
        // solo ve filas 'aprobada' — si ya existe una propuesta huérfana sin
        // aprobar ocupando la siguiente versión numérica, intentar reutilizarla
        // colisiona con la unicidad de V25 (ux_parametrizacion_proyecto_metrica_
        // version) y falla con DataIntegrityViolationException/500. Usando el
        // máximo real (findHistorialVersiones(), ya devuelve todas las versiones
        // ordenadas DESC por version) esa colisión ya no puede ocurrir, y ninguna
        // versión existente se reutiliza ni se modifica.
        Integer siguienteVersion = 1;
        var historial = parametrizacionRepository.findHistorialVersiones(
            request.metricaId(),
            request.proyectoId()
        );

        if (!historial.isEmpty()) {
            siguienteVersion = historial.get(0).getVersion() + 1;
        }

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setVersion(siguienteVersion);
        parametrizacion.setMetricaId(request.metricaId());
        parametrizacion.setProyectoId(request.proyectoId());
        parametrizacion.setUserId(userId);
        parametrizacion.setUserEmail(userEmail);
        parametrizacion.setObjetivo(request.objetivo());
        parametrizacion.setProcedimiento(request.procedimiento());
        parametrizacion.setIndicadorVariable(request.indicadorVariable());
        parametrizacion.setEscala(request.escala());
        parametrizacion.setFrecuenciaCaptura(request.frecuenciaCaptura() != null 
            ? request.frecuenciaCaptura() 
            : "por_sprint");
        
        // Campos académicos
        parametrizacion.setFuenteAcademica(request.fuenteAcademica());
        parametrizacion.setFormulaAcademica(request.formulaAcademica());
        parametrizacion.setTipoOperacion(request.tipoOperacion());
        parametrizacion.setUnidadResultado(request.unidadResultado());
        
        parametrizacion.setPropuestaIAJson(request.propuestaIAJson());
        parametrizacion.setStatus("propuesta"); // NO aprobada automáticamente
        parametrizacion.setCreatedAt(Instant.now());

        MetricParametrizacion guardada = parametrizacionRepository.save(parametrizacion);
        // FASE 16.10-E: no se persiste como columna (ver MetricParametrizacion.nombreVariable),
        // se propaga en la respuesta para que el frontend lo conserve como fuente de verdad
        // al aprobar. Queda documentado de forma durable dentro de propuestaIAJson.
        guardada.setNombreVariable(request.nombreVariable());
        return guardada;
    }
    
    /**
     * Aprueba formalmente una parametrización.
     * 
     * - Guarda snapshot de configuración aprobada (reproducibilidad)
     * - Marca versiones anteriores como INACTIVA
     * - Cambia status a APROBADA
     * - Registra quién y cuándo aprobó
     */
    @Transactional
    public MetricParametrizacion aprobarParametrizacion(
        UUID parametrizacionId, 
        AprobarParametrizacionRequest request
    ) {
        // Obtener parametrización
        MetricParametrizacion parametrizacion = parametrizacionRepository.findById(parametrizacionId)
            .orElseThrow(() -> new IllegalArgumentException("Parametrización no encontrada"));
        
        // Validar autorización
        String userId = getUserId();
        String userEmail = getUserEmail();
        validarMiembroProyecto(userId, parametrizacion.getProyectoId());
        
        // Validar que esté en estado propuesta
        if (!"propuesta".equals(parametrizacion.getStatus())) {
            throw new IllegalStateException("Solo se pueden aprobar parametrizaciones en estado 'propuesta'");
        }

        validarTipoOperacion(request.tipoOperacion());
        if (request.tipoOperacion() == null || request.tipoOperacion().isBlank()) {
            throw new TipoOperacionInvalidoException(
                "No se puede aprobar la parametrización sin un tipoOperacion definido: " +
                "el motor de cálculo no podría ejecutar esta métrica. " +
                "Valores permitidos: " + TIPOS_OPERACION_SOPORTADOS);
        }
        validarNombreVariable(request.nombreVariable());

        // FASE 16.10-G: la versión a desactivar es la última con status='aprobada'
        // para este proyecto/métrica — no version-1. Buscar por version-1 (lógica
        // previa) asumía que la versión aprobada vigente siempre ocupa el número
        // inmediatamente anterior al de la nueva versión, algo que dejó de ser
        // cierto desde Fase 16.10-F: guardarPropuesta() calcula la siguiente
        // versión sobre el máximo histórico total (cualquier status), por lo que
        // pueden existir propuestas huérfanas en números de versión intermedios
        // entre la última aprobada real y la nueva versión a aprobar.
        // findUltimaVersionAprobada() ya modela exactamente "última versión
        // aprobada" sin depender del número de versión.
        var versionAnterior = parametrizacionRepository.findUltimaVersionAprobada(
            parametrizacion.getMetricaId(),
            parametrizacion.getProyectoId()
        );

        if (versionAnterior.isPresent()) {
            MetricParametrizacion anterior = versionAnterior.get();
            anterior.setStatus("inactiva");
            parametrizacionRepository.save(anterior);
        }
        
        // Actualizar con configuración aprobada
        parametrizacion.setObjetivo(request.objetivo());
        parametrizacion.setProcedimiento(request.procedimiento());
        parametrizacion.setIndicadorVariable(request.indicadorVariable());
        parametrizacion.setEscala(request.escala());
        parametrizacion.setFrecuenciaCaptura(request.frecuenciaCaptura() != null 
            ? request.frecuenciaCaptura() 
            : parametrizacion.getFrecuenciaCaptura());
        
        // Campos académicos
        parametrizacion.setFuenteAcademica(request.fuenteAcademica());
        parametrizacion.setFormulaAcademica(request.formulaAcademica());
        parametrizacion.setTipoOperacion(request.tipoOperacion());
        parametrizacion.setUnidadResultado(request.unidadResultado());
        parametrizacion.setNombreVariable(request.nombreVariable());

        // Crear snapshot JSON de configuración aprobada
        try {
            String snapshot = objectMapper.writeValueAsString(new ConfiguracionAprobadaSnapshot(
                parametrizacion.getVersion(),
                request.objetivo(),
                request.procedimiento(),
                request.indicadorVariable(),
                request.escala(),
                request.frecuenciaCaptura(),
                request.fuenteAcademica(),
                request.formulaAcademica(),
                request.tipoOperacion(),
                request.unidadResultado(),
                userEmail,
                Instant.now(),
                request.nombreVariable()
            ));
            parametrizacion.setConfiguracionAprobadaJson(snapshot);
        } catch (Exception e) {
            System.err.println("Error creando snapshot: " + e.getMessage());
        }

        // Aprobar
        parametrizacion.setStatus("aprobada");
        parametrizacion.setRevisadoPor(userId);
        parametrizacion.setRevisadoAt(Instant.now());

        MetricParametrizacion guardada = parametrizacionRepository.save(parametrizacion);

        // FASE 16.10-B: Crear variables automáticamente al aprobar
        // FASE 16.10-E: nombreVariable (si viene informado y válido) tiene prioridad
        // sobre el fallback de extracción desde indicadorVariable.
        crearVariablesDesdeParametrizacion(guardada, request.nombreVariable());

        return guardada;
    }
    
    /**
     * Obtiene la última versión aprobada de una parametrización.
     * Esencial para cálculos reproducibles.
     */
    public MetricParametrizacion obtenerUltimaVersionAprobada(UUID metricaId, UUID proyectoId) {
        return parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId)
            .orElseThrow(() -> new IllegalStateException(
                "No existe parametrización aprobada para esta métrica"));
    }
    
    /**
     * Obtiene el historial de versiones de una parametrización.
     */
    public List<MetricParametrizacion> obtenerHistorialVersiones(UUID metricaId, UUID proyectoId) {
        return parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId);
    }
    
    // === Helpers ===
    
    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return auth.getName();
    }
    
    private String getUserEmail() {
        // En este caso, userId es el email
        return getUserId();
    }
    
    private void validarMiembroProyecto(String userId, UUID proyectoId) {
        boolean exists = projectMemberRepository
            .existsByProyectoIdAndUserId(proyectoId, userId);
        
        if (!exists) {
            throw new IllegalStateException("Usuario no pertenece al proyecto");
        }
    }
    
    /**
     * Record interno para snapshot JSON
     */
    private record ConfiguracionAprobadaSnapshot(
        Integer version,
        String objetivo,
        String procedimiento,
        String indicadorVariable,
        String escala,
        String frecuenciaCaptura,
        String fuenteAcademica,
        String formulaAcademica,
        String tipoOperacion,
        String unidadResultado,
        String aprobadoPor,
        Instant aprobadoEn,
        String nombreVariable
    ) {}

    /**
     * FASE 16.10-B: Crea variables automáticamente al aprobar parametrización.
     * FASE 16.10-E: prioriza nombreVariable (identificador técnico explícito,
     * validado por validarNombreVariable antes de llegar aquí) sobre el
     * fallback de extracción desde indicadorVariable — que es texto humano
     * y no una fuente confiable de nombre técnico (ver investigación de
     * SIG-VEL-02). El fallback se conserva por compatibilidad con
     * parametrizaciones que no informan nombreVariable, pero su resultado
     * también se valida antes de usarse: nunca debe llegar un INSERT que
     * pueda fallar con DataException/500 por overflow o formato inválido.
     *
     * Ejemplos (solo aplican al fallback, sin nombreVariable explícito):
     * - "problemas_reportados" → 1 variable
     * - "horas_trabajadas, horas_planificadas" → 2 variables
     */
    private void crearVariablesDesdeParametrizacion(MetricParametrizacion parametrizacion, String nombreVariableExplicito) {
        // Verificar que no existan variables ya creadas
        List<Variable> existentes = variableRepository
            .findByParametrizacionIdAndParametrizacionVersion(
                parametrizacion.getId(),
                parametrizacion.getVersion()
            );

        if (!existentes.isEmpty()) {
            // Ya existen variables, no crear duplicados
            return;
        }

        String[] nombresVariables;
        if (nombreVariableExplicito != null && !nombreVariableExplicito.isBlank()) {
            // Ya validado por validarNombreVariable() en aprobarParametrizacion()
            // (admite lista separada por comas — ver validarNombreVariable()).
            nombresVariables = java.util.Arrays.stream(nombreVariableExplicito.split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);
        } else {
            // Compatibilidad: sin nombreVariable explícito, fallback sobre indicadorVariable.
            String indicador = parametrizacion.getIndicadorVariable();
            if (indicador == null || indicador.isBlank()) {
                throw new IllegalStateException(
                    "indicadorVariable no está definido en la parametrización");
            }

            // Parsear nombres de variables
            // Formato esperado: "nombre_variable" o "var1, var2, var3"
            nombresVariables = extraerNombresVariables(indicador);

            if (nombresVariables.length == 0) {
                throw new IllegalStateException(
                    "No se pudieron extraer nombres de variables desde: " + indicador);
            }

            // FASE 16.10-E: el fallback puede producir nombres inválidos o
            // demasiado largos (caso real SIG-VEL-02: indicadorVariable en
            // prosa sin snake_case). Validar ANTES de intentar persistir,
            // para rechazar con un error claro en vez de un DataException/500.
            for (String nombreFallback : nombresVariables) {
                validarNombreVariable(nombreFallback);
            }
        }

        // Obtener métrica
        Metrica metrica = metricaRepository.findById(parametrizacion.getMetricaId())
            .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada"));
        
        // Crear una Variable por cada nombre extraído
        for (String nombreVar : nombresVariables) {
            Variable variable = new Variable();
            variable.setProyectoId(parametrizacion.getProyectoId());
            variable.setMetrica(metrica);
            variable.setNombre(nombreVar);
            variable.setDescripcion(generarDescripcionVariable(nombreVar, metrica));
            variable.setTipoAlcance("grupal");
            variable.setTipoIndicador(determinarTipoIndicador(metrica.getCategoria().getNombre()));
            variable.setFrecuencia("por_sprint");
            variable.setCardinalidad("unico");
            variable.setTipoDato("numerico");
            variable.setActiva(true);
            variable.setFormulaTexto(parametrizacion.getFormulaAcademica());
            variable.setFrecuenciaCaptura(parametrizacion.getFrecuenciaCaptura());
            variable.setParametrizacionId(parametrizacion.getId());
            variable.setParametrizacionVersion(parametrizacion.getVersion());
            variable.setCreatedAt(Instant.now());
            
            variableRepository.save(variable);
        }
    }
    
    /**
     * Extrae nombres de variables desde el campo indicadorVariable.
     * 
     * Estrategia de extracción:
     * 1. Buscar identificadores tipo snake_case (ej: problemas_reportados)
     * 2. Si no encuentra, usar el primer token antes de espacios/paréntesis
     * 3. Soportar múltiples variables separadas por coma
     */
    private String[] extraerNombresVariables(String indicador) {
        // Limpiar y normalizar
        String limpio = indicador.trim();
        
        // Separar por coma si hay múltiples variables
        String[] partes = limpio.split(",");
        
        List<String> nombres = new ArrayList<>();
        
        for (String parte : partes) {
            String nombreExtraido = extraerNombreVariable(parte.trim());
            if (nombreExtraido != null && !nombreExtraido.isBlank()) {
                nombres.add(nombreExtraido);
            }
        }
        
        return nombres.toArray(new String[0]);
    }
    
    /**
     * Extrae un nombre de variable individual.
     * 
     * Busca patrón snake_case (palabras unidas por _).
     * Fallback: primera palabra en minúsculas.
     */
    private String extraerNombreVariable(String texto) {
        // Buscar patrón snake_case (ej: problemas_reportados)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[a-z]+(_[a-z]+)+");
        java.util.regex.Matcher matcher = pattern.matcher(texto);
        
        if (matcher.find()) {
            return matcher.group();
        }
        
        // Fallback: primera palabra en minúsculas, reemplazar espacios por _
        String[] palabras = texto.toLowerCase()
            .replaceAll("[^a-z0-9 ]", "")
            .trim()
            .split("\\s+");
        
        if (palabras.length > 0) {
            return String.join("_", palabras);
        }
        
        return null;
    }
    
    /**
     * Genera descripción legible para una variable.
     */
    private String generarDescripcionVariable(String nombreVariable, Metrica metrica) {
        // Convertir snake_case a formato legible
        String legible = nombreVariable.replace("_", " ");
        legible = legible.substring(0, 1).toUpperCase() + legible.substring(1);
        
        return legible + " de " + metrica.getNombre();
    }
    
    /**
     * Determina tipo de indicador basado en categoría de métrica.
     */
    private String determinarTipoIndicador(String categoriaNombre) {
        return switch (categoriaNombre) {
            case "Calidad" -> "calidad";
            case "Productividad" -> "productividad";
            case "Cumplimiento" -> "cumplimiento";
            case "Flexibilidad" -> "flexibilidad";
            case "Sociohumano" -> "sociohumano";
            default -> "calidad";
        };
    }
}
