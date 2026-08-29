// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.1: Servicio para métricas académicas parametrizables
package com.mpdia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.*;
import com.mpdia.entity.*;
import com.mpdia.formula.FormulaEvaluator;
import com.mpdia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio especializado para métricas con respaldo académico.
 * 
 * FASE 16.9.1: Implementa el ciclo completo:
 * 1. Propuesta de parametrización (con IA)
 * 2. Aprobación formal
 * 3. Ejecución/captura de valores
 * 4. Cálculo determinista (motor backend, NO IA)
 * 5. Persistencia de resultados
 * 6. Histórico consultable
 * 7. Interpretación IA (opcional, post-cálculo)
 * 
 * REGLA ACADÉMICA:
 * La fuente define la métrica. La implementación debe respetar la fórmula académica.
 * Gemini asiste en parametrización e interpretación, NO en cálculo.
 */
@Service
@RequiredArgsConstructor
public class MetricaAcademicaService {
    
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final MetricaRepository metricaRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;
    private final MetricParametrizacionRepository parametrizacionRepo;
    private final VariableRepository variableRepo;
    private final ResultadoMetricaRepository resultadoRepo;
    private final ProjectMemberRepository projectMemberRepository;
    private final EjecucionService ejecucionService;
    private final FormulaEvaluator formulaEvaluator;
    
    /**
     * Genera una propuesta de parametrización basada en definición académica.
     * Gemini actúa como asistente experto para estructurar la parametrización.
     */
    public PropuestaParametrizacionDto generarPropuestaAcademica(MetricaAcademicaRequest request) {
        String prompt = buildPromptAcademico(request);
        
        try {
            String raw = geminiService.generate(prompt);
            List<PropuestaParametrizacionDto> propuestas = parsePropuestas(raw, request);
            return propuestas.isEmpty() ? fallbackPropuesta(request) : propuestas.get(0);
        } catch (Exception e) {
            System.err.println("=== ERROR GEMINI ===");
            System.err.println(e.getMessage());
            System.err.println("===================");
            return fallbackPropuesta(request);
        }
    }
    
    private String buildPromptAcademico(MetricaAcademicaRequest r) {
        return """
            Eres un asistente experto en métricas de productividad Scrum con conocimiento académico.
            
            MÉTRICA ACADÉMICA:
            Código:      %s
            Nombre:      %s
            Definición:  %s
            
            FUENTE ACADÉMICA:
            %s
            
            FÓRMULA ACADÉMICA:
            %s
            
            TIPO DE OPERACIÓN:
            %s
            
            UNIDAD DE RESULTADO:
            %s
            
            REGLAS CRÍTICAS:
            1. Esta métrica está respaldada por una fuente académica
            2. La fórmula es FIJA y no debe modificarse
            3. Tu rol es ayudar a OPERACIONALIZAR la métrica, no reinventarla
            4. La parametrización debe facilitar la captura de las variables de la fórmula
            5. El procedimiento debe ser claro y práctico para el equipo
            6. NO inventes datos o benchmarks
            7. Deja claro que es una PROPUESTA que requiere validación
            
            Genera UNA propuesta estructurada que ayude al equipo a implementar esta métrica académica.
            
            Responde ÚNICAMENTE con un array JSON con UN objeto:
            [
              {
                "titulo": "Parametrización de [nombre métrica] según [fuente]",
                "objetivo": "Qué se logrará midiendo esta métrica (basado en la definición académica)",
                "procedimiento": "Cómo capturar los valores según la fórmula académica (paso a paso claro y específico)",
                "indicadorVariable": "Variables necesarias para la fórmula (listar cada una)",
                "escala": "Tipo de dato y rango de cada variable (texto legible)",
                "escalaTipo": "NUMERICA_ENTERA o NUMERICA_DECIMAL — nunca otro valor",
                "escalaMin": "valor mínimo permitido (número, ej: 0)",
                "escalaMax": "valor máximo permitido (número), o null si no hay límite superior",
                "escalaPaso": "incremento permitido entre valores válidos (ej: 1, o 0.01 para dos decimales)",
                "escalaSinLimite": "true si no hay máximo superior (ej. conteos), false en caso contrario",
                "escalaDescripcion": "significado de los valores (ej: '0 = Muy malo; 10 = Excelente'), o null si no aplica",
                "justificacion": "Por qué esta parametrización es fiel a la fuente académica y práctica para el equipo (menciona que es PROPUESTA)"
              }
            ]

            REGLAS PARA LA ESCALA: infierí una escala apropiada para lo que la variable
            realmente mide — NO uses 0-10 por defecto para todo. Un conteo de eventos
            (defectos, incidentes, bloqueos) normalmente es NUMERICA_ENTERA, mínimo 0,
            paso 1, sinLimite=true. Un porcentaje normalmente es 0-100 (o 0-1 decimal).
            Una valoración subjetiva puede ser 0-10 o 1-5 entero. Un valor que admite
            decimales (horas, promedios) debe ser NUMERICA_DECIMAL con el paso adecuado.

            IMPORTANTE: El procedimiento debe respetar la fórmula académica exacta.
            """.formatted(
                r.codigoMetrica(),
                r.nombreMetrica(),
                r.definicion() != null ? r.definicion() : "Métrica académica",
                r.fuenteAcademica(),
                r.formulaAcademica(),
                r.tipoOperacion(),
                r.unidadResultado()
            );
    }
    
    private List<PropuestaParametrizacionDto> parsePropuestas(String raw, MetricaAcademicaRequest r) {
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
            return List.of(fallbackPropuesta(r));
        }
    }
    
    private PropuestaParametrizacionDto fallbackPropuesta(MetricaAcademicaRequest r) {
        // Sin respuesta válida de Gemini no hay forma segura de inferir una escala
        // específica para esta métrica académica — se deja explícitamente sin
        // estructurar (escalaTipo=null) en vez de asumir un rango arbitrario.
        return new PropuestaParametrizacionDto(
            "Parametrización de " + r.nombreMetrica(),
            "Medir " + r.nombreMetrica() + " según la fuente académica.",
            "Capturar valores de las variables y aplicar la fórmula: " + r.formulaAcademica(),
            "Variables según fórmula académica",
            "Valores según tipo de operación: " + r.tipoOperacion(),
            r.frecuencia() != null ? r.frecuencia() : "por_sprint",
            r.fuenteAcademica(),
            r.formulaAcademica(),
            r.tipoOperacion(),
            r.unidadResultado(),
            "Propuesta generada automáticamente basada en " + r.fuenteAcademica(),
            "valor_capturado",
            null, null, null, null, null, null
        );
    }
    
    /**
     * Guarda una propuesta de parametrización académica.
     * Incluye metadatos académicos completos.
     */
    @Transactional
    public MetricParametrizacion guardarPropuestaAcademica(
            MetricaAcademicaRequest request,
            PropuestaParametrizacionDto propuesta) {
        
        // Validar autorización
        String userId = getUserId();
        String userEmail = getUserEmail();
        validarMiembroProyecto(userId, request.proyectoId());
        
        // Verificar versión
        Integer siguienteVersion = 1;
        var existente = parametrizacionRepo.findUltimaVersionAprobada(
            request.metricaId(), 
            request.proyectoId()
        );
        
        if (existente.isPresent()) {
            siguienteVersion = existente.get().getVersion() + 1;
        }
        
        // Crear parametrización con datos académicos
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setVersion(siguienteVersion);
        parametrizacion.setMetricaId(request.metricaId());
        parametrizacion.setProyectoId(request.proyectoId());
        parametrizacion.setUserId(userId);
        parametrizacion.setUserEmail(userEmail);
        parametrizacion.setObjetivo(propuesta.objetivo());
        parametrizacion.setProcedimiento(propuesta.procedimiento());
        parametrizacion.setIndicadorVariable(propuesta.indicadorVariable());
        parametrizacion.setEscala(propuesta.escala());
        parametrizacion.setFrecuenciaCaptura(request.frecuencia() != null
            ? request.frecuencia()
            : "por_sprint");

        // Escala estructurada (corrección del manejo de escalas): se valida y se
        // persiste igual que en ParametrizacionService, reutilizando la misma regla.
        ParametrizacionService.validarEscalaEstructurada(
            propuesta.escalaTipo(), propuesta.escalaMin(), propuesta.escalaMax(),
            propuesta.escalaPaso(), propuesta.escalaSinLimite());
        parametrizacion.setEscalaTipo(propuesta.escalaTipo());
        parametrizacion.setEscalaMin(propuesta.escalaMin());
        parametrizacion.setEscalaMax(Boolean.TRUE.equals(propuesta.escalaSinLimite()) ? null : propuesta.escalaMax());
        parametrizacion.setEscalaPaso(propuesta.escalaPaso());
        parametrizacion.setEscalaSinLimite(propuesta.escalaSinLimite());
        parametrizacion.setEscalaDescripcion(propuesta.escalaDescripcion());
        
        // Campos académicos (Fase 16.9.1)
        parametrizacion.setFuenteAcademica(request.fuenteAcademica());
        parametrizacion.setFormulaAcademica(request.formulaAcademica());
        parametrizacion.setTipoOperacion(request.tipoOperacion());
        parametrizacion.setUnidadResultado(request.unidadResultado());
        
        // Snapshot de propuesta IA
        try {
            parametrizacion.setPropuestaIAJson(objectMapper.writeValueAsString(propuesta));
        } catch (Exception e) {
            System.err.println("Error serializando propuesta: " + e.getMessage());
        }
        
        parametrizacion.setStatus("propuesta");
        parametrizacion.setCreatedAt(Instant.now());
        
        return parametrizacionRepo.save(parametrizacion);
    }
    
    /**
     * Ejecuta una métrica académica en un sprint específico.
     * 
     * Flujo:
     * 1. Validar parametrización aprobada
     * 2. Validar variables
     * 3. Guardar valores capturados
     * 4. Calcular resultado (motor determinista)
     * 5. Persistir resultado con trazabilidad completa
     */
    @Transactional
    public ResultadoMetricaDto ejecutarMetricaAcademica(
            UUID metricaId,
            EjecutarMetricaAcademicaRequest request) {
        
        String userId = getUserId();
        
        // 1. Validaciones básicas
        Metrica metrica = metricaRepo.findById(metricaId)
            .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada"));
        
        Proyecto proyecto = proyectoRepo.findById(request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        Sprint sprint = sprintRepo.findById(request.sprintId())
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));
        
        if (!sprint.getProyectoId().equals(request.proyectoId())) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }
        
        validarMiembroProyecto(userId, request.proyectoId());
        
        // 2. Obtener parametrización APROBADA
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, request.proyectoId())
            .orElseThrow(() -> new IllegalStateException(
                "No existe parametrización aprobada para esta métrica"));
        
        // 3. Validar que sea métrica académica
        if (parametrizacion.getTipoOperacion() == null) {
            throw new IllegalStateException(
                "Esta parametrización no tiene tipo de operación definido");
        }
        
        // 4. Obtener variables asociadas a la parametrización aprobada.
        // Las variables se crean al momento de aprobar la parametrización
        // (ParametrizacionService.aprobarParametrizacion), nunca durante la ejecución:
        // la ejecución solo debe leerlas, no adivinarlas.
        List<Variable> variables = variableRepo
            .findByParametrizacionIdAndParametrizacionVersion(
                parametrizacion.getId(),
                parametrizacion.getVersion()
            );

        if (variables.isEmpty()) {
            throw new IllegalStateException(
                "La parametrización aprobada no tiene variables configuradas.");
        }
        
        // 5. Guardar valores capturados
        Map<UUID, BigDecimal> valoresGuardados = new HashMap<>();
        
        for (Variable variable : variables) {
            Object valorObj = request.valores().get(variable.getNombre());
            if (valorObj == null) {
                throw new IllegalArgumentException(
                    "Falta el valor para la variable: " + variable.getNombre());
            }
            
            // Convertir a BigDecimal
            BigDecimal valor = convertirABigDecimal(valorObj);
            
            // Validar según tipo de operación
            validarValor(valor, parametrizacion.getTipoOperacion());

            // Guardar o actualizar valor (FASE 16.11: único camino de escritura,
            // ver EjecucionService.guardarOActualizarValor())
            ejecucionService.guardarOActualizarValor(
                variable, request.sprintId(), userId, valor, null, null, null);

            valoresGuardados.put(variable.getId(), valor);
        }
        
        // 6. Calcular resultado según tipo de operación
        BigDecimal resultado = calcularSegunTipo(
            parametrizacion.getTipoOperacion(),
            valoresGuardados,
            parametrizacion.getFormulaAcademica(),
            variables
        );
        
        // 7. Persistir resultado
        ResultadoMetrica resultadoEntity = new ResultadoMetrica();
        resultadoEntity.setProyectoId(request.proyectoId());
        resultadoEntity.setMetrica(metrica);
        resultadoEntity.setSprintId(request.sprintId());
        resultadoEntity.setParametrizacionId(parametrizacion.getId());
        resultadoEntity.setParametrizacionVersion(parametrizacion.getVersion());
        resultadoEntity.setTipoCalculo(parametrizacion.getTipoOperacion().toLowerCase());
        resultadoEntity.setExpresionUtilizada(parametrizacion.getFormulaAcademica());
        
        try {
            resultadoEntity.setValoresUtilizados(
                objectMapper.writeValueAsString(valoresGuardados));
        } catch (Exception e) {
            resultadoEntity.setValoresUtilizados("{}");
        }
        
        resultadoEntity.setResultado(resultado);
        resultadoEntity.setUnidad(parametrizacion.getUnidadResultado());
        resultadoEntity.setEstado("calculado");
        resultadoEntity.setCalculadoPor(userId);
        resultadoEntity.setCalculadoAt(Instant.now());
        
        resultadoEntity = resultadoRepo.save(resultadoEntity);
        
        // 8. Retornar DTO
        return new ResultadoMetricaDto(
            resultadoEntity.getId(),
            metrica.getId(),
            metrica.getNombre(),
            request.proyectoId(),
            request.sprintId(),
            parametrizacion.getId(),
            parametrizacion.getVersion(),
            parametrizacion.getTipoOperacion().toLowerCase(),
            parametrizacion.getFormulaAcademica(),
            resultadoEntity.getValoresUtilizados(),
            resultado,
            parametrizacion.getUnidadResultado(),
            "calculado",
            null,
            resultadoEntity.getCalculadoAt()
        );
    }
    
    /**
     * Obtiene el histórico de resultados de una métrica en un proyecto.
     */
    public List<ResultadoMetricaDto> obtenerHistorico(UUID metricaId, UUID proyectoId) {
        validarMiembroProyecto(getUserId(), proyectoId);

        List<ResultadoMetrica> resultados = resultadoRepo
            .findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(metricaId, proyectoId);
        
        return resultados.stream()
            .map(r -> new ResultadoMetricaDto(
                r.getId(),
                r.getMetrica().getId(),
                r.getMetrica().getNombre(),
                r.getProyectoId(),
                r.getSprintId(),
                r.getParametrizacionId(),
                r.getParametrizacionVersion(),
                r.getTipoCalculo(),
                r.getExpresionUtilizada(),
                r.getValoresUtilizados(),
                r.getResultado(),
                r.getUnidad(),
                r.getEstado(),
                r.getMensajeError(),
                r.getCalculadoAt()
            ))
            .toList();
    }
    
    /**
     * Solicita interpretación IA de un resultado ya calculado.
     * Gemini NO calcula, solo interpreta.
     */
    public InterpretacionIADto solicitarInterpretacionIA(UUID resultadoId) {
        ResultadoMetrica resultado = resultadoRepo.findById(resultadoId)
            .orElseThrow(() -> new IllegalArgumentException("Resultado no encontrado"));

        validarMiembroProyecto(getUserId(), resultado.getProyectoId());

        // Obtener histórico para contexto
        List<ResultadoMetrica> historico = resultadoRepo
            .findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(
                resultado.getMetrica().getId(),
                resultado.getProyectoId()
            );
        
        // Obtener parametrización para fuente académica
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findById(resultado.getParametrizacionId())
            .orElse(null);
        
        String prompt = buildPromptInterpretacion(resultado, historico, parametrizacion);
        
        try {
            String interpretacion = geminiService.generate(prompt);
            
            return new InterpretacionIADto(
                resultadoId,
                resultado.getMetrica().getNombre(),
                resultado.getResultado(),
                resultado.getUnidad(),
                interpretacion,
                Instant.now()
            );
        } catch (Exception e) {
            return new InterpretacionIADto(
                resultadoId,
                resultado.getMetrica().getNombre(),
                resultado.getResultado(),
                resultado.getUnidad(),
                "No se pudo generar interpretación: " + e.getMessage(),
                Instant.now()
            );
        }
    }
    
    private String buildPromptInterpretacion(
            ResultadoMetrica resultado,
            List<ResultadoMetrica> historico,
            MetricParametrizacion parametrizacion) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("Eres un asistente experto en métricas de productividad Scrum.\n\n");
        
        sb.append("MÉTRICA: ").append(resultado.getMetrica().getNombre()).append("\n");
        
        if (parametrizacion != null) {
            if (parametrizacion.getFuenteAcademica() != null) {
                sb.append("FUENTE ACADÉMICA: ").append(parametrizacion.getFuenteAcademica()).append("\n");
            }
            if (parametrizacion.getFormulaAcademica() != null) {
                sb.append("FÓRMULA: ").append(parametrizacion.getFormulaAcademica()).append("\n");
            }
        }
        
        sb.append("\nRESULTADO ACTUAL: ").append(resultado.getResultado());
        if (resultado.getUnidad() != null) {
            sb.append(" ").append(resultado.getUnidad());
        }
        sb.append("\n");
        
        if (historico.size() > 1) {
            sb.append("\nHISTÓRICO (últimos ").append(Math.min(5, historico.size() - 1)).append(" sprints):\n");
            int count = 0;
            for (ResultadoMetrica h : historico) {
                if (!h.getId().equals(resultado.getId()) && count < 5) {
                    sb.append("- ").append(h.getResultado());
                    if (h.getUnidad() != null) {
                        sb.append(" ").append(h.getUnidad());
                    }
                    sb.append(" (").append(h.getCalculadoAt().toString().substring(0, 10)).append(")\n");
                    count++;
                }
            }
        }
        
        sb.append("\nREGLAS IMPORTANTES:\n");
        sb.append("1. NO calcules el resultado (ya está calculado)\n");
        sb.append("2. NO inventes benchmarks sin referencia académica\n");
        sb.append("3. NO afirmes que un valor es 'bueno' o 'malo' sin fundamento\n");
        sb.append("4. Describe la evolución objetivamente\n");
        sb.append("5. Identifica tendencias si existen\n");
        sb.append("6. Usa lenguaje contextual y profesional\n");
        sb.append("7. Si hay incremento/decremento, calcula el porcentaje de cambio\n\n");
        
        sb.append("Proporciona una interpretación concisa y profesional del resultado (máximo 4 párrafos).");
        
        return sb.toString();
    }
    
    // === Métodos auxiliares ===

    private BigDecimal convertirABigDecimal(Object valor) {
        if (valor instanceof BigDecimal) {
            return (BigDecimal) valor;
        }
        if (valor instanceof Number) {
            return BigDecimal.valueOf(((Number) valor).doubleValue());
        }
        if (valor instanceof String) {
            try {
                return new BigDecimal((String) valor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Valor no es un número válido: " + valor);
            }
        }
        throw new IllegalArgumentException(
            "Tipo de valor no soportado: " + valor.getClass().getName());
    }
    
    private void validarValor(BigDecimal valor, String tipoOperacion) {
        if (valor == null) {
            throw new IllegalArgumentException("El valor no puede ser nulo");
        }
        
        // Para SUMA, no permitir valores negativos
        if ("SUMA".equals(tipoOperacion) && valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "El valor no puede ser negativo para operación SUMA");
        }
    }
    
    private BigDecimal calcularSegunTipo(
            String tipoOperacion,
            Map<UUID, BigDecimal> valores,
            String formulaAcademica,
            List<Variable> variables) {

        if (valores.isEmpty()) {
            throw new IllegalArgumentException("No hay valores para calcular");
        }

        return switch (tipoOperacion) {
            case "DIRECTO" -> valores.values().iterator().next();
            case "SUMA" -> valores.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            case "PROMEDIO" -> {
                BigDecimal suma = valores.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                yield suma.divide(
                    BigDecimal.valueOf(valores.size()),
                    4,
                    java.math.RoundingMode.HALF_UP
                );
            }
            case "FORMULA" -> {
                String expresion = construirExpresionEjecutable(formulaAcademica, variables);
                yield formulaEvaluator.evaluarFormula(expresion, valores);
            }
            default -> throw new IllegalArgumentException(
                "Tipo de operación no soportado: " + tipoOperacion);
        };
    }

    /**
     * Traduce una fórmula académica en texto humano (ej. "(ACAT / ACR) * 100")
     * a la representación ${uuid} que exige FormulaEvaluator, sustituyendo cada
     * nombre de variable de la parametrización por el UUID real de esa variable.
     *
     * Sustitución segura: por límites de palabra (\b, no String.replace()) y
     * ordenando los nombres de mayor a menor longitud, para que un nombre que
     * sea subcadena de otro (ej. "A" dentro de "ACAT") nunca se sustituya primero
     * por error. Cualquier identificador que quede sin sustituir se interpreta
     * como una variable referenciada en la fórmula pero ausente en la
     * parametrización, y se rechaza explícitamente en vez de dejarlo llegar,
     * sin control, al tokenizer.
     *
     * FASE 3: el match nombre↔fórmula es insensible a mayúsculas (ej. la fórmula
     * puede citar "ACAT" mientras el nombre técnico de la variable, restringido
     * a snake_case minúscula por validarNombreVariable(), es "acat"). Esto NO
     * toca el motor (FormulaEvaluator/FormulaParser/FormulaTokenizer/ASTNode):
     * solo cambia cómo este método decide qué ${uuid} sustituir.
     *
     * FASE 3: también normaliza el signo "×" (U+00D7, multiplicación en
     * notación académica) a "*" (único operador que FormulaTokenizer
     * reconoce), exclusivamente sobre esta expresión de trabajo temporal.
     * formula_academica NUNCA se reescribe con esta normalización: el texto
     * original (incluido "×") se preserva íntegro para trazabilidad, tanto en
     * la parametrización como en expresionUtilizada del resultado, que se
     * toma directamente de parametrizacion.getFormulaAcademica() y no de esta
     * expresión traducida.
     */
    private String construirExpresionEjecutable(String formulaAcademica, List<Variable> variables) {
        if (formulaAcademica == null || formulaAcademica.isBlank()) {
            throw new IllegalStateException(
                "La parametrización no tiene fórmula académica definida");
        }

        List<Variable> ordenadas = variables.stream()
            .sorted(Comparator.comparingInt((Variable v) -> v.getNombre().length()).reversed())
            .toList();

        String expresion = formulaAcademica.replace('×', '*');
        for (Variable variable : ordenadas) {
            Pattern patron = Pattern.compile(
                "\\b" + Pattern.quote(variable.getNombre()) + "\\b",
                Pattern.CASE_INSENSITIVE);
            String reemplazo = Matcher.quoteReplacement("${" + variable.getId() + "}");
            expresion = patron.matcher(expresion).replaceAll(reemplazo);
        }

        // Ignorar el contenido ya sustituido (${uuid}) al buscar identificadores
        // sueltos, porque los UUID contienen letras hexadecimales que de otro
        // modo parecerían nombres de variable sin resolver.
        String sinVariables = expresion.replaceAll("\\$\\{[^}]*}", "");
        Matcher identificadorSuelto = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(sinVariables);
        if (identificadorSuelto.find()) {
            throw new IllegalStateException(
                "La fórmula académica hace referencia a una variable no reconocida: '"
                    + identificadorSuelto.group() + "'. Variables disponibles: "
                    + variables.stream().map(Variable::getNombre).toList());
        }

        return expresion;
    }
    
    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return auth.getName();
    }
    
    private String getUserEmail() {
        return getUserId();
    }
    
    private void validarMiembroProyecto(String userId, UUID proyectoId) {
        boolean exists = projectMemberRepository
            .existsByProyectoIdAndUserId(proyectoId, userId);
        
        if (!exists) {
            throw new IllegalStateException("Usuario no pertenece al proyecto");
        }
    }
}
