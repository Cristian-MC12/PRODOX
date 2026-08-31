// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.GuardarValoresRequest;
import com.mpdia.dto.VariableConValorDto;
import com.mpdia.dto.VariablesMetricaResponse;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para captura dinámica de variables desde parametrizaciones aprobadas.
 * Fase 16.7: Materialización on-demand de variables.
 */
@Service
@RequiredArgsConstructor
public class VariableDinamicaService {

    private final MetricParametrizacionRepository parametrizacionRepo;
    private final VariableRepository variableRepo;
    private final RegistroValorRepository registroRepo;
    private final MetricaRepository metricaRepo;
    private final SprintRepository sprintRepo;
    private final ProyectoRepository proyectoRepo;
    private final ObjectMapper objectMapper;
    private final EjecucionService ejecucionService;

    /**
     * Obtiene las variables de una métrica para captura en un sprint.
     * Materializa variables on-demand desde la parametrización aprobada.
     */
    @Transactional
    public VariablesMetricaResponse obtenerVariables(UUID metricaId, UUID proyectoId, UUID sprintId, String userId) {
        // 1. Validar proyecto
        Proyecto proyecto = proyectoRepo.findById(proyectoId)
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        // 2. Validar sprint
        Sprint sprint = sprintRepo.findById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        if (!sprint.getProyectoId().equals(proyectoId)) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }

        // Revisión de seguridad: esta consulta no validaba que el usuario autenticado
        // fuera miembro del proyecto — cualquier usuario podía leer variables/valores
        // de cualquier proyecto con solo conocer metricaId+proyectoId+sprintId.
        ejecucionService.validarAcceso(userId, proyectoId);

        // 3. Buscar parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, proyectoId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe parametrización aprobada para esta métrica. " +
                "Debe aprobar una parametrización antes de capturar valores."
            ));
        
        // 4. Buscar o crear variables
        List<Variable> variables = obtenerOCrearVariables(parametrizacion, proyecto);
        
        // 5. Obtener valores actuales del sprint
        List<VariableConValorDto> variablesConValor = variables.stream()
            .map(v -> construirVariableConValor(v, sprintId, userId))
            .toList();
        
        return new VariablesMetricaResponse(
            parametrizacion.getId(),
            parametrizacion.getVersion(),
            parametrizacion.getStatus(),
            variablesConValor
        );
    }

    /**
     * Materializa (obtiene o crea) las variables versionadas de una parametrización ya
     * aprobada. Reutilizado por el flujo de Verificación (MetricRankingService.verificar())
     * para que, al aprobar, se cree exactamente la variable vinculada a
     * parametrizacion_id+version — nunca una variable genérica paralela (ver diagnóstico
     * FASE 9, bloques 3 y 9).
     */
    @Transactional
    public List<Variable> materializarVariables(MetricParametrizacion parametrizacion) {
        Proyecto proyecto = proyectoRepo.findById(parametrizacion.getProyectoId())
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        return obtenerOCrearVariables(parametrizacion, proyecto);
    }

    /**
     * Obtiene variables existentes o las crea on-demand desde la parametrización.
     */
    private List<Variable> obtenerOCrearVariables(MetricParametrizacion parametrizacion, Proyecto proyecto) {
        // Buscar variables existentes
        List<Variable> existentes = variableRepo.findByParametrizacionIdAndParametrizacionVersion(
            parametrizacion.getId(),
            parametrizacion.getVersion()
        );
        
        if (!existentes.isEmpty()) {
            return existentes;
        }
        
        // Si no existen, crearlas desde el JSON
        return crearVariablesDesdeParametrizacion(parametrizacion, proyecto);
    }

    /**
     * Crea variables automáticamente desde configuracionAprobadaJson (o, si no existe, desde
     * las columnas planas — FASE 10). FASE 11: indicadorVariable admite una lista separada por
     * comas ("acat, acr"), igual que ParametrizacionService.crearVariablesDesdeParametrizacion()
     * — antes esta versión creaba una única variable con ese texto completo como nombre, lo que
     * impedía calcular métricas FORMULA de más de una variable (FAT, Deuda técnica) cuando se
     * aprobaban por el flujo de Verificación.
     */
    private List<Variable> crearVariablesDesdeParametrizacion(MetricParametrizacion parametrizacion, Proyecto proyecto) {
        try {
            String indicadorVariable;
            String procedimiento;
            String frecuenciaCaptura;
            String nombreVariableExplicito = null;

            String snapshotJson = parametrizacion.getConfiguracionAprobadaJson();
            if (snapshotJson != null && !snapshotJson.isBlank()) {
                // Parametrización aprobada por el flujo académico (ParametrizacionService):
                // usar el snapshot de reproducibilidad.
                JsonNode json = objectMapper.readTree(snapshotJson);
                indicadorVariable = json.get("indicadorVariable").asText();
                procedimiento = json.get("procedimiento").asText();
                frecuenciaCaptura = json.has("frecuenciaCaptura")
                    ? json.get("frecuenciaCaptura").asText()
                    : "por_sprint";
                // El snapshot también guarda el identificador técnico explícito
                // (ConfiguracionAprobadaSnapshot.nombreVariable) cuando el flujo
                // académico lo recibió — priorizarlo evita re-derivar un nombre
                // distinto al que realmente se usó/validó al aprobar.
                if (json.hasNonNull("nombreVariable") && !json.get("nombreVariable").asText().isBlank()) {
                    nombreVariableExplicito = json.get("nombreVariable").asText();
                }
            } else {
                // FASE 10: parametrizaciones aprobadas por el flujo de Verificación
                // (MetricRankingService) no generan snapshot JSON — usar las mismas
                // columnas planas (idéntico significado y nombre que el snapshot).
                indicadorVariable = parametrizacion.getIndicadorVariable();
                procedimiento = parametrizacion.getProcedimiento();
                frecuenciaCaptura = parametrizacion.getFrecuenciaCaptura() != null
                    ? parametrizacion.getFrecuenciaCaptura()
                    : "por_sprint";
            }

            // Buscar métrica
            Metrica metrica = metricaRepo.findById(parametrizacion.getMetricaId())
                .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada"));

            // Causa raíz del error "nombreVariable '...' no tiene formato técnico
            // válido" visto en /verificacion: este flujo (aprobación vía
            // MetricRankingService.verificar(), sin nombreVariable explícito) usaba
            // indicadorVariable — un texto humano libre, ej. "Número de defectos
            // únicos registrados durante el sprint" — directamente como nombre
            // técnico de la Variable, sin normalizarlo. Se reemplaza por
            // ParametrizacionService.extraerNombresVariables(...), la misma
            // extracción/normalización a snake_case ya usada y probada en el flujo
            // académico (ParametrizacionService.crearVariablesDesdeParametrizacion),
            // en vez de duplicar un segundo algoritmo. El nombre visible de la
            // métrica (indicadorVariable, procedimiento) nunca se modifica: solo
            // cambia cómo se deriva el identificador técnico interno.
            String[] nombresVariables;
            if (nombreVariableExplicito != null) {
                nombresVariables = java.util.Arrays.stream(nombreVariableExplicito.split(",", -1))
                    .map(String::trim)
                    .filter(n -> !n.isBlank())
                    .toArray(String[]::new);
            } else {
                nombresVariables = ParametrizacionService.extraerNombresVariables(indicadorVariable);
            }
            if (nombresVariables.length == 0) {
                throw new IllegalStateException("indicadorVariable no está definido en la parametrización");
            }

            // FASE 17 (corrección del defecto documentado): valida cada nombre ANTES de
            // persistir, reutilizando el mismo tipo de excepción (NombreVariableInvalidoException)
            // que ya usaba ParametrizacionService para esta misma situación. Antes, un
            // indicadorVariable demasiado largo (frecuente en propuestas de IA, que describen
            // el indicador en prosa) llegaba sin validar hasta variableRepo.save(...), donde
            // fallaba con DataIntegrityViolationException — error que MetricRankingService.verificar()
            // solo registraba en log, dejando la parametrización marcada "aprobada" sin
            // variable funcional y sin aviso alguno al Scrum Master.
            //
            // FASE 13 (auditoría de Fase 12): esa validación solo comprobaba longitud, no
            // formato — permitía que fragmentos de frase humana (ej. "Problemas reportados
            // en el sprint") o mitades de un indicadorVariable con coma (ej. "...escala
            // numérica de 1 a 5" + "donde 1 es muy bajo...") se persistieran como Variable.nombre
            // sin ser identificadores técnicos. Se reemplaza por
            // ParametrizacionService.validarNombreVariableIndividual(...) — exactamente la misma
            // regla snake_case ya probada en ese servicio, sin duplicar el patrón con un segundo
            // comportamiento independiente.
            for (String nombreVar : nombresVariables) {
                ParametrizacionService.validarNombreVariableIndividual(nombreVar, indicadorVariable);
            }

            List<Variable> resultado = new ArrayList<>();
            for (String nombreVar : nombresVariables) {
                Variable variable = new Variable();
                variable.setProyectoId(proyecto.getId());
                variable.setMetrica(metrica);
                variable.setNombre(nombreVar);
                variable.setDescripcion(procedimiento);  // Usar procedimiento como descripción
                // Revisión de captura por parametrización: el alcance/responsable
                // elegido por el Scrum Master (EQUIPO/SCRUM_MASTER, columna
                // MetricParametrizacion.responsableCaptura) decide QUIÉN captura —
                // ya no está fijo en "grupal" para todas las variables.
                variable.setTipoAlcance(ParametrizacionService.tipoAlcanceDesdeResponsable(
                    parametrizacion.getResponsableCaptura()));
                variable.setTipoDato("numerico");  // Por defecto numérico
                variable.setActiva(true);
                variable.setFrecuenciaCaptura(frecuenciaCaptura);
                variable.setParametrizacionId(parametrizacion.getId());
                variable.setParametrizacionVersion(parametrizacion.getVersion());

                // Corrección del manejo de escalas: escalaMin/escalaMax/escalaPaso/
                // escalaTipo/escalaSinLimite son columnas reales de MetricParametrizacion
                // (ver migración V32) — se copian directamente, sin depender de un regex
                // frágil (\d+-\d+) sobre el texto libre `escala` (que fallaba para casi
                // cualquier redacción, ej. "Numérica, entera (0 o más)"). Válido tanto para
                // el flujo académico (con snapshot JSON) como para el de Verificación (sin
                // snapshot): ambos leen las mismas columnas de `parametrizacion`.
                // escalaTipo=null (parametrización histórica sin estructura) se copia igual
                // como null — Variable queda explícitamente sin restricción, sin inventar rango.
                variable.setEscalaTipo(parametrizacion.getEscalaTipo());
                variable.setEscalaMin(parametrizacion.getEscalaMin());
                variable.setEscalaMax(parametrizacion.getEscalaMax());
                variable.setEscalaPaso(parametrizacion.getEscalaPaso());
                variable.setEscalaSinLimite(parametrizacion.getEscalaSinLimite());

                resultado.add(variableRepo.save(variable));
            }
            return resultado;

        } catch (NombreVariableInvalidoException e) {
            // No envolver: debe propagarse tal cual (es un IllegalArgumentException,
            // GlobalExceptionHandler ya la mapea a HTTP 400 con mensaje claro) en vez
            // de perderse dentro de un RuntimeException genérico de "parseo".
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error parseando configuración aprobada: " + e.getMessage(), e);
        }
    }

    /**
     * Construye DTO con el valor actual del sprint PARA EL USUARIO AUTENTICADO.
     *
     * Corrección de captura por usuario: antes esta consulta era
     * findBySprintIdAndVariable_Id(sprintId, variableId) — sin userId y sin
     * ORDER BY — así que devolvía el registro de CUALQUIER miembro (el que la
     * base de datos devolviera primero), y ese valor precargaba el formulario
     * de Ejecución de cualquier otro miembro que todavía no hubiera
     * registrado el suyo, como si ya estuviera "registrado". La captura
     * siempre se guardó correctamente por (sprint, variable, userId) — ver
     * EjecucionService.guardarOActualizarValor() —, pero esta lectura nunca
     * respetó esa misma clave. Ahora usa el mismo método ya existente y
     * probado que usa la escritura para localizar "mi" registro vigente:
     * findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc.
     *
     * Esto es intencionalmente distinto de "todos los valores del equipo"
     * (usado por CalculoMetricaService para la agregación EQUIPO), que sigue
     * trayendo TODOS los registros sin filtrar por usuario — son dos
     * necesidades distintas y no deben compartir la misma consulta.
     */
    private VariableConValorDto construirVariableConValor(Variable variable, UUID sprintId, String userId) {
        RegistroValor miRegistroVigente = registroRepo
            .findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(sprintId, variable.getId(), userId)
            .orElse(null);

        return new VariableConValorDto(
            variable.getId(),
            variable.getNombre(),
            variable.getDescripcion(),
            variable.getTipoDato(),
            true,  // Por ahora todas obligatorias
            null,  // Unidad no se extrae todavía
            variable.getEscalaMin(),
            variable.getEscalaMax(),
            miRegistroVigente != null ? miRegistroVigente.getValorNum() : null,
            miRegistroVigente != null ? miRegistroVigente.getValorTexto() : null,
            miRegistroVigente != null ? miRegistroVigente.getValorBool() : null,
            variable.getFrecuenciaCaptura(),
            variable.getEscalaTipo(),
            variable.getEscalaPaso(),
            variable.getEscalaSinLimite(),
            variable.getTipoAlcance()
        );
    }

    /**
     * Guarda valores de variables en un sprint.
     */
    @Transactional
    public void guardarValores(UUID metricaId, GuardarValoresRequest request, String userId) {
        // 1. Validar proyecto
        Proyecto proyecto = proyectoRepo.findById(request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        // 2. Validar sprint
        Sprint sprint = sprintRepo.findById(request.sprintId())
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        if (!sprint.getProyectoId().equals(request.proyectoId())) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }

        // Revisión de seguridad: registrar valores no validaba membresía ni rol en
        // absoluto. Revisión de captura individual: quién puede registrar depende
        // del tipoAlcance de CADA variable (validarPuedeRegistrar), no de un único
        // chequeo por request — un mismo guardado puede incluir variables
        // individuales (cualquier miembro) y grupales (solo Scrum Master) a la
        // vez, así que la validación se hace por variable dentro del bucle de
        // abajo, no aquí.

        // 3. Validar parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("No existe parametrización aprobada"));
        
        // 4. Guardar cada valor
        for (GuardarValoresRequest.ValorVariable valorRequest : request.valores()) {
            Variable variable = variableRepo.findById(valorRequest.variableId())
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada"));

            // Individual: cualquier miembro puede registrar su propio dato.
            // Grupal: se conserva la restricción original (solo Scrum Master).
            ejecucionService.validarPuedeRegistrar(userId, variable);

            // Validar que la variable pertenece a la parametrización correcta
            if (!variable.getParametrizacionId().equals(parametrizacion.getId())) {
                throw new IllegalArgumentException("Variable no pertenece a la parametrización aprobada");
            }
            
            // Validar tipo de dato
            validarTipoDato(variable, valorRequest);
            
            // Crear o actualizar registro
            guardarRegistroValor(variable, sprint, userId, valorRequest);
        }
    }

    /**
     * Valida que el valor coincida con el tipo de dato de la variable.
     */
    private void validarTipoDato(Variable variable, GuardarValoresRequest.ValorVariable valor) {
        switch (variable.getTipoDato()) {
            case "numerico":
                if (valor.valorNum() == null) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor numérico");
                }
                break;
            case "texto":
                if (valor.valorTexto() == null || valor.valorTexto().isBlank()) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor de texto");
                }
                break;
            case "booleano":
                if (valor.valorBool() == null) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor booleano");
                }
                break;
        }
    }

    /**
     * Guarda o actualiza un registro de valor (FASE 16.11: único camino de
     * escritura, ver EjecucionService.guardarOActualizarValor()).
     *
     * FASE 16: si el request trae fechaCaptura explícita, se parsea y se
     * propaga a la sobrecarga aditiva de EjecucionService — misma variable +
     * sprint + fecha actualiza esa captura; fecha distinta crea una nueva.
     * Sin fechaCaptura, comportamiento idéntico al existente (Instant.now()).
     *
     * Revisión de Ejecución: registroId (opcional) se propaga tal cual —
     * null es "captura nueva" (sin cambios); informado, identifica de forma
     * inequívoca la fila que se está editando, ver EjecucionService.
     */
    private void guardarRegistroValor(Variable variable, Sprint sprint, String userId,
                                      GuardarValoresRequest.ValorVariable valorRequest) {
        Instant fechaCaptura = valorRequest.fechaCaptura() != null && !valorRequest.fechaCaptura().isBlank()
            ? Instant.parse(valorRequest.fechaCaptura())
            : null;

        ejecucionService.guardarOActualizarValor(
            variable, sprint.getId(), userId,
            valorRequest.valorNum(), valorRequest.valorTexto(),
            valorRequest.valorBool(), valorRequest.observacion(), fechaCaptura,
            valorRequest.registroId());
    }
}
