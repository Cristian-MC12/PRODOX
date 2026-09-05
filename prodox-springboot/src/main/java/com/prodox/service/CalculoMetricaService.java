// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CalcularMetricaRequest;
import com.prodox.dto.ResultadoMetricaDto;
import com.prodox.entity.*;
import com.prodox.formula.FormulaEvaluator;
import com.prodox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio para cálculo determinista de métricas.
 * Fase 16.8: NO utiliza Gemini para cálculos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculoMetricaService {
    
    private final MetricaRepository metricaRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;
    private final MetricParametrizacionRepository parametrizacionRepo;
    private final VariableRepository variableRepo;
    private final RegistroValorRepository registroRepo;
    private final ResultadoMetricaRepository resultadoRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final FormulaEvaluator evaluator;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** Mensaje de error usado cuando la excepción original no trae uno propio. */
    private static final String MENSAJE_ERROR_DESCONOCIDO =
        "Error desconocido durante el cálculo de la métrica";

    /** Límite de longitud para mensaje_error (columna TEXT, pero se acota igual
     *  por prudencia — nunca debe crecer sin límite con una traza completa). */
    private static final int MENSAJE_ERROR_MAX_LENGTH = 2000;

    @Transactional
    public ResultadoMetricaDto calcularMetrica(
            UUID metricaId, 
            CalcularMetricaRequest request, 
            String userId) {
        
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

        if (!projectMemberRepo.existsByProyectoIdAndUserId(request.proyectoId(), userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        
        // 2. Obtener parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe parametrización aprobada para esta métrica"));
        
        // 3. Obtener variables de la parametrización
        List<Variable> variables = variableRepo
            .findByParametrizacionIdAndParametrizacionVersion(
                parametrizacion.getId(), 
                parametrizacion.getVersion()
            );
        
        if (variables.isEmpty()) {
            throw new IllegalArgumentException(
                "La parametrización no tiene variables definidas");
        }
        
        // 4. Obtener valores registrados (más reciente primero — necesario para
        // que "el más reciente" de una variable grupal sea determinista, y para
        // poder distinguir "un solo registro" de "varios" en variables individuales).
        Map<UUID, List<BigDecimal>> valoresPorVariable = new HashMap<>();
        for (Variable variable : variables) {
            List<RegistroValor> registros = registroRepo
                .findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(request.sprintId(), variable.getId());

            List<BigDecimal> valores = registros.stream()
                .map(RegistroValor::getValorNum)
                .filter(Objects::nonNull)
                .toList();

            valoresPorVariable.put(variable.getId(), valores);
        }
        
        // 5. Determinar tipo de cálculo y calcular
        //
        // Corrección de lectura de la configuración aprobada: el campo canónico y
        // siempre confiable de la operación aprobada para ESTA métrica es
        // MetricParametrizacion.tipoOperacion (columna propia, validada al aprobar
        // por ParametrizacionService.aprobarParametrizacion()/MetricRankingService.
        // guardarPorMetrica()/guardarPorFactor()) — NUNCA una clave del snapshot
        // configuracionAprobadaJson. Los dos flujos de aprobación reales escriben
        // formas distintas de ese snapshot (ParametrizacionService incluye la clave
        // "tipoOperacion"; MetricRankingService.guardarSnapshotConNombreVariable() no
        // incluye ninguna clave de operación en absoluto), así que leer del JSON
        // ("tipo", antes) nunca podía ser una fuente de verdad fiable — dejaba
        // tipoCalculo en null y este switch producía un NullPointerException. Mismo
        // razonamiento para "variable_id"/"expresion": ninguna clave existe en los
        // snapshots reales, así que se resuelven aquí desde variables (ya scopeada a
        // esta parametrización) y desde MetricParametrizacion.formulaAcademica.
        String configuracionJson = parametrizacion.getConfiguracionAprobadaJson();
        String tipoOperacionAprobado = parametrizacion.getTipoOperacion();

        try {
            BigDecimal resultado;
            String tipoCalculo;
            String expresion = null;

            if ((configuracionJson == null || configuracionJson.isBlank())
                    && (tipoOperacionAprobado == null || tipoOperacionAprobado.isBlank())) {
                // Compatibilidad: parametrización histórica previa a la introducción de
                // tipoOperacion/configuracionAprobadaJson (Fase 16.9.1) — ni snapshot ni
                // tipo de operación definidos. Mismo comportamiento legacy que existía
                // antes de esta corrección, sin cambios.
                tipoCalculo = "directo";
                Map<UUID, BigDecimal> valores = resolverValorPorVariable(variables, valoresPorVariable);
                resultado = evaluator.evaluarDirecto(variables.get(0).getId(), valores);
            } else if (tipoOperacionAprobado == null || tipoOperacionAprobado.isBlank()) {
                // La parametrización SÍ pasó por un flujo moderno (tiene snapshot), pero
                // quedó aprobada sin tipoOperacion definido (posible vía MetricRankingService.
                // verificar(), que no exige este campo al aprobar). Error de negocio claro,
                // nunca un NullPointerException — ver Fase de corrección de lectura.
                throw new IllegalArgumentException(
                    "La parametrización aprobada de esta métrica no tiene una operación de " +
                    "cálculo válida (MetricParametrizacion.tipoOperacion). El motor de cálculo " +
                    "no puede determinar cómo calcular el resultado hasta que el Scrum Master " +
                    "defina la operación aprobada (SUMA, PROMEDIO, DIRECTO o FORMULA).");
            } else {
                String tipoNormalizado = tipoOperacionAprobado.trim().toUpperCase();
                tipoCalculo = tipoNormalizado.toLowerCase();

                resultado = switch (tipoNormalizado) {
                    case "DIRECTO" -> calcularDirecto(variables, valoresPorVariable);
                    case "SUMA" -> calcularSuma(variables, valoresPorVariable);
                    case "PROMEDIO" -> calcularPromedio(variables, valoresPorVariable);
                    case "FORMULA" -> {
                        expresion = parametrizacion.getFormulaAcademica();
                        yield calcularFormula(expresion, variables, valoresPorVariable);
                    }
                    default -> throw new IllegalArgumentException(
                        "Tipo de operación no soportado en la configuración aprobada: " +
                        tipoOperacionAprobado);
                };
            }

            // 5.b Un solo resultado vigente por proyecto+métrica+sprint (Corrección
            // de auditoría, parte B), sin importar la versión de parametrización:
            // antes de insertar el nuevo resultado, CUALQUIER resultado vigente
            // anterior para esta combinación pasa a vigente=false. Nunca se borra
            // ni se modifica su contenido, solo el flag.
            //
            // Antes esta invalidación solo buscaba el vigente de la MISMA
            // parametrizacion_version (findBy...AndParametrizacionVersionAndVigenteTrue),
            // dejando intacto el vigente de una versión ANTERIOR si la métrica se
            // reparametrizó — el índice único parcial de V37 (idx_resultado_vigente_
            // unico) incluye parametrizacion_version en su definición, así que dos
            // versiones distintas SÍ pueden tener cada una su propio vigente=true
            // simultáneo a nivel de esquema. invalidarResultadosVigentes() ya no
            // filtra por versión, así que cierra ese hueco a nivel de aplicación.
            //
            // Limitación conocida, documentada explícitamente: el índice de V37
            // sigue existiendo tal cual — endurecer la garantía a nivel de base de
            // datos (un índice único sin parametrizacion_version) requeriría una
            // migración nueva (V42), fuera de esta fase.
            invalidarResultadosVigentes(request.proyectoId(), metricaId, request.sprintId());

            // 6. Persistir resultado
            ResultadoMetrica resultadoEntity = new ResultadoMetrica();
            resultadoEntity.setProyectoId(request.proyectoId());
            resultadoEntity.setMetrica(metrica);
            resultadoEntity.setSprintId(request.sprintId());
            resultadoEntity.setParametrizacionId(parametrizacion.getId());
            resultadoEntity.setParametrizacionVersion(parametrizacion.getVersion());
            resultadoEntity.setTipoCalculo(tipoCalculo);
            resultadoEntity.setExpresionUtilizada(expresion);
            resultadoEntity.setValoresUtilizados(
                objectMapper.writeValueAsString(valoresPorVariable));
            resultadoEntity.setResultado(resultado);
            resultadoEntity.setEstado("calculado");
            resultadoEntity.setCalculadoPor(userId);
            resultadoEntity.setCalculadoAt(Instant.now());
            resultadoEntity.setVigente(true);
            
            resultadoEntity = resultadoRepo.save(resultadoEntity);
            
            // 7. Retornar DTO
            return new ResultadoMetricaDto(
                resultadoEntity.getId(),
                metrica.getId(),
                metrica.getNombre(),
                request.proyectoId(),
                request.sprintId(),
                parametrizacion.getId(),
                parametrizacion.getVersion(),
                tipoCalculo,
                expresion,
                resultadoEntity.getValoresUtilizados(),
                resultado,
                resultadoEntity.getUnidad(),
                "calculado",
                null,
                resultadoEntity.getCalculadoAt()
            );
            
        } catch (IllegalArgumentException e) {
            persistirResultadoError(metricaId, metrica, request, parametrizacion, valoresPorVariable, userId, e.getMessage());
            throw e;
        } catch (ArithmeticException e) {
            persistirResultadoError(metricaId, metrica, request, parametrizacion, valoresPorVariable, userId, e.getMessage());
            throw new ArithmeticException("Error de cálculo: " + e.getMessage());
        } catch (Exception e) {
            persistirResultadoError(metricaId, metrica, request, parametrizacion, valoresPorVariable, userId, e.getMessage());
            throw new RuntimeException("Error calculando métrica: " + e.getMessage(), e);
        }
    }

    /**
     * Un solo resultado vigente por proyecto+métrica+sprint (Corrección de
     * auditoría, parte B), sin importar la versión de parametrización que lo
     * produjo. Se usa tanto en el camino exitoso (antes de insertar el resultado
     * calculado) como en el camino de error (antes de insertar el resultado con
     * estado="error", ver persistirResultadoError()) — en ambos casos el nuevo
     * resultado refleja el estado del ÚLTIMO intento de cálculo, y cualquier
     * resultado vigente previo (de cualquier versión) pasa a histórico. Nunca se
     * borra ni se modifica el contenido de una fila anterior, solo su flag
     * vigente.
     *
     * saveAndFlush (no save) por cada fila, igual que el código que reemplaza:
     * Hibernate ordena su flush por tipo de operación (INSERTs antes que
     * UPDATEs), no por orden de código, así que un save() normal podría enviar
     * el INSERT del resultado nuevo (vigente=true) antes que el UPDATE que
     * marca el/los anterior(es) como vigente=false.
     */
    private void invalidarResultadosVigentes(UUID proyectoId, UUID metricaId, UUID sprintId) {
        List<ResultadoMetrica> vigentes = resultadoRepo
            .findByProyectoIdAndMetrica_IdAndSprintIdAndVigenteTrue(proyectoId, metricaId, sprintId);

        for (ResultadoMetrica anterior : vigentes) {
            anterior.setVigente(false);
            resultadoRepo.saveAndFlush(anterior);
        }
    }

    /**
     * Corrección de auditoría (parte C): persiste de forma segura un cálculo
     * fallido, en vez de dejar la excepción como único rastro. Antes, el camino
     * automático (EjecucionService.recalcularMetricaAsociada() ->
     * recalcularSilenciosamente()) atrapaba cualquier excepción de calcularMetrica()
     * y solo hacía log.debug() — sin dejar ningún dato consultable, así que un
     * fallo de cálculo (ej. una parametrización re-versionada cuyas variables
     * nuevas no tienen registro_valores todavía — caso real "Creación de un
     * avatar Xabi") era completamente invisible para el usuario.
     *
     * Invalida primero cualquier resultado vigente anterior (misma regla que
     * invalidarResultadosVigentes()) para que ESTE resultado con estado="error"
     * pase a ser el vigente: representa fielmente que el ÚLTIMO intento de
     * cálculo para esta combinación falló — nunca se deja un resultado
     * "calculado" antiguo como si siguiera siendo válido cuando el intento más
     * reciente en realidad falló.
     *
     * resultado=BigDecimal.ZERO es un valor técnico obligatorio por la columna
     * NOT NULL de resultados_metricas — JAMÁS debe interpretarse como un
     * resultado real. Todo consumidor de resultados_metricas debe filtrar
     * estado="calculado" antes de leer el campo resultado (ver
     * EvaluacionService.resultadosCalculadosDeLaMetrica()).
     *
     * Se ejecuta en una transacción NUEVA e independiente (REQUIRES_NEW, vía
     * TransactionTemplate — no @Transactional: este método se invoca por
     * auto-invocación desde el propio catch de calcularMetrica(), y el proxy de
     * Spring no intercepta llamadas self-invocadas, así que una anotación aquí
     * no tendría efecto). Es imprescindible que sea independiente: cuando
     * calcularMetrica() se invoca desde CalculoMetricaController (camino manual),
     * la excepción que se relanza después de este método hace que Spring revierta
     * la transacción de calcularMetrica() — sin una transacción propia, el
     * registro de error se revertiría junto con ella, perdiéndose igual que
     * antes de esta corrección.
     *
     * Nunca lanza: si el propio guardado de este registro de error fallara (caso
     * extremo, ej. problema de conexión a BD), se registra en log.error() y se
     * deja que la excepción original siga su curso sin un segundo fallo
     * enmascarándola.
     */
    private void persistirResultadoError(
            UUID metricaId,
            Metrica metrica,
            CalcularMetricaRequest request,
            MetricParametrizacion parametrizacion,
            Map<UUID, List<BigDecimal>> valoresPorVariable,
            String userId,
            String mensajeError) {

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                // metricaId (el parámetro validado al inicio de calcularMetrica()),
                // no metrica.getId(): mismo identificador que usa el camino exitoso
                // (ver invalidarResultadosVigentes() más abajo en el 5.b), para que
                // ambos caminos localicen exactamente los mismos resultados vigentes.
                invalidarResultadosVigentes(request.proyectoId(), metricaId, request.sprintId());

                ResultadoMetrica resultadoEntity = new ResultadoMetrica();
                resultadoEntity.setProyectoId(request.proyectoId());
                resultadoEntity.setMetrica(metrica);
                resultadoEntity.setSprintId(request.sprintId());
                resultadoEntity.setParametrizacionId(parametrizacion.getId());
                resultadoEntity.setParametrizacionVersion(parametrizacion.getVersion());
                resultadoEntity.setTipoCalculo("error");
                resultadoEntity.setExpresionUtilizada(null);
                resultadoEntity.setValoresUtilizados(serializarValoresParaError(valoresPorVariable));
                resultadoEntity.setResultado(BigDecimal.ZERO); // placeholder técnico, JAMÁS un resultado real
                resultadoEntity.setEstado("error");
                resultadoEntity.setMensajeError(truncarMensajeError(mensajeError));
                resultadoEntity.setCalculadoPor(userId);
                resultadoEntity.setCalculadoAt(Instant.now());
                resultadoEntity.setVigente(true);

                resultadoRepo.save(resultadoEntity);
            });
        } catch (Exception persistError) {
            log.error("No se pudo persistir el resultado de error para métrica {} / sprint {}: {}",
                metrica.getId(), request.sprintId(), persistError.getMessage());
        }
    }

    private String serializarValoresParaError(Map<UUID, List<BigDecimal>> valoresPorVariable) {
        try {
            return objectMapper.writeValueAsString(
                valoresPorVariable != null ? valoresPorVariable : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncarMensajeError(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return MENSAJE_ERROR_DESCONOCIDO;
        }
        return mensaje.length() > MENSAJE_ERROR_MAX_LENGTH
            ? mensaje.substring(0, MENSAJE_ERROR_MAX_LENGTH)
            : mensaje;
    }

    /**
     * DIRECTO/SUMA/PROMEDIO/FORMULA resuelven su(s) variable(s) directamente desde
     * `variables` (ya scopeada a la parametrización aprobada por
     * findByParametrizacionIdAndParametrizacionVersion), no desde una clave
     * "variable_id" del snapshot JSON — ningún flujo de aprobación real escribe esa
     * clave. crearVariablesDesdeParametrizacion() crea exactamente una Variable por
     * cada nombre en nombreVariable/indicadorVariable, y solo FORMULA declara más de
     * un nombre (lista separada por comas); DIRECTO/SUMA/PROMEDIO son de una sola
     * variable, así que tomar variables.get(0) es válido y no una suposición nueva.
     */
    private BigDecimal calcularDirecto(
            List<Variable> variables,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {

        Map<UUID, BigDecimal> valores = resolverValorPorVariable(variables, valoresPorVariable);
        return evaluator.evaluarDirecto(variables.get(0).getId(), valores);
    }

    private BigDecimal calcularSuma(
            List<Variable> variables,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {

        UUID variableId = variables.get(0).getId();
        List<BigDecimal> valores = valoresPorVariable.get(variableId);
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para sumar de la variable: " + variableId);
        }

        return evaluator.evaluarSuma(variableId, valores);
    }

    private BigDecimal calcularPromedio(
            List<Variable> variables,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {

        UUID variableId = variables.get(0).getId();
        List<BigDecimal> valores = valoresPorVariable.get(variableId);
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para promediar de la variable: " + variableId);
        }

        return evaluator.evaluarPromedio(variableId, valores);
    }

    private BigDecimal calcularFormula(
            String formulaAcademica,
            List<Variable> variables,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {

        Map<UUID, BigDecimal> valores = resolverValorPorVariable(variables, valoresPorVariable);
        String expresionEjecutable = construirExpresionEjecutable(formulaAcademica, variables);

        return evaluator.evaluarFormula(expresionEjecutable, valores);
    }

    /**
     * Traduce la fórmula académica en texto humano (ej. "(ACAT / ACR) * 100") a la
     * representación ${uuid} que exige FormulaEvaluator, sustituyendo cada nombre de
     * variable de la parametrización por el UUID real de esa variable. Mismo
     * algoritmo que MetricaAcademicaService.construirExpresionEjecutable — no se
     * inventa una semántica de fórmula nueva, solo se resuelve aquí también porque
     * este servicio, a diferencia de aquel, no recibía la fórmula ya traducida.
     * Sustitución por límites de palabra (no String.replace()), insensible a
     * mayúsculas, y normalizando "×" a "*" (único operador que FormulaTokenizer
     * reconoce) exclusivamente en esta expresión de trabajo temporal.
     */
    private String construirExpresionEjecutable(String formulaAcademica, List<Variable> variables) {
        if (formulaAcademica == null || formulaAcademica.isBlank()) {
            throw new IllegalArgumentException(
                "La parametrización tiene tipoOperacion=FORMULA pero no tiene fórmula " +
                "académica definida (MetricParametrizacion.formulaAcademica).");
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

        String sinVariables = expresion.replaceAll("\\$\\{[^}]*}", "");
        Matcher identificadorSuelto = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(sinVariables);
        if (identificadorSuelto.find()) {
            throw new IllegalArgumentException(
                "La fórmula académica hace referencia a una variable no reconocida: '"
                    + identificadorSuelto.group() + "'. Variables disponibles: "
                    + variables.stream().map(Variable::getNombre).toList());
        }

        return expresion;
    }

    /**
     * Resuelve un único valor por variable para usar en DIRECTO/FORMULA.
     *
     * Revisión de captura universal: la necesidad de reducir varios registros
     * a un único valor ya NO depende del tipoAlcance de la variable — antes
     * solo se exigía Variable.agregacionMiembros para variables 'individual'
     * con 2+ registros, porque solo esas podían tener más de un miembro
     * registrando su propio valor (las 'grupal' solo las capturaba el Scrum
     * Master, así que nunca había ambigüedad). Ahora que cualquier miembro
     * puede registrar su propio valor en CUALQUIER variable, esa misma
     * ambigüedad puede darse en cualquiera:
     * - 0 o 1 registro: sin ambigüedad, se usa ese valor directamente — no
     *   hace falta configurar ninguna reducción.
     * - 2+ registros (varios miembros aportaron su propio dato, o el mismo
     *   miembro capturó varias veces con una frecuencia distinta de
     *   'por_sprint'): se exige Variable.agregacionMiembros y se reduce con
     *   esa regla. Si no está configurada, se rechaza explícitamente en vez
     *   de asumir SUMA/PROMEDIO o tomar el más reciente en silencio — los
     *   registros NUNCA se descartan sin que quede claro cómo se combinaron.
     */
    private Map<UUID, BigDecimal> resolverValorPorVariable(
            List<Variable> variables,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {

        Map<UUID, Variable> variablesPorId = variables.stream()
                .collect(Collectors.toMap(Variable::getId, v -> v));

        Map<UUID, BigDecimal> resultado = new HashMap<>();

        for (Map.Entry<UUID, List<BigDecimal>> entry : valoresPorVariable.entrySet()) {
            List<BigDecimal> valores = entry.getValue();
            if (valores == null || valores.isEmpty()) continue;

            Variable variable = variablesPorId.get(entry.getKey());

            if (valores.size() > 1) {
                String regla = variable != null ? variable.getAgregacionMiembros() : null;
                if (regla == null || regla.isBlank()) {
                    String nombre = variable != null ? variable.getNombre() : entry.getKey().toString();
                    throw new AgregacionMiembrosNoConfiguradaException(
                        "La variable '" + nombre + "' tiene " +
                        valores.size() + " registros para este período, " +
                        "pero no tiene configurada Variable.agregacionMiembros. Configure SUMA, " +
                        "PROMEDIO, CONTEO, MIN o MAX antes de calcular esta métrica.");
                }
                resultado.put(entry.getKey(), aplicarAgregacionMiembros(regla, valores, nombreDe(variable, entry.getKey())));
            } else {
                resultado.put(entry.getKey(), valores.get(0)); // único registro — sin cambios de comportamiento
            }
        }

        return resultado;
    }

    private String nombreDe(Variable variable, UUID variableId) {
        return variable != null ? variable.getNombre() : variableId.toString();
    }

    private BigDecimal aplicarAgregacionMiembros(String regla, List<BigDecimal> valores, String nombreVariable) {
        return switch (regla) {
            case "SUMA" -> valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "PROMEDIO" -> valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valores.size()), 4, RoundingMode.HALF_UP);
            case "CONTEO" -> BigDecimal.valueOf(valores.size());
            case "MIN" -> valores.stream().min(Comparator.naturalOrder()).orElseThrow();
            case "MAX" -> valores.stream().max(Comparator.naturalOrder()).orElseThrow();
            default -> throw new AgregacionMiembrosNoConfiguradaException(
                "La variable '" + nombreVariable + "' tiene configurada una agregacionMiembros " +
                "no soportada: '" + regla + "'. Valores permitidos: SUMA, PROMEDIO, CONTEO, MIN, MAX.");
        };
    }

    /**
     * Recalcula una métrica sin propagar ningún error hacia el llamador — pensado
     * para dispararse automáticamente justo después de que un miembro registre un
     * valor (ver EjecucionService.guardarOActualizarValor), de forma que Evaluación
     * tenga resultados frescos sin depender exclusivamente del botón manual
     * "Calcular". Corre en una transacción NUEVA e independiente (REQUIRES_NEW):
     * si el cálculo falla (ej. parametrización aún no aprobada, o agregación de
     * miembros sin configurar), esa falla queda aislada y NUNCA hace rollback de
     * la captura del valor que la disparó — el registro del miembro siempre se
     * conserva, se haya podido recalcular el resultado del equipo o no.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalcularSilenciosamente(UUID metricaId, UUID proyectoId, UUID sprintId, String userId) {
        try {
            calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), userId);
        } catch (Exception e) {
            log.debug("Recálculo automático omitido para métrica {} / sprint {}: {}",
                    metricaId, sprintId, e.getMessage());
        }
    }
}
