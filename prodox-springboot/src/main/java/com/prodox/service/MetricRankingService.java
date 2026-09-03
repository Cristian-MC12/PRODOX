// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.GuardarParametrizacionRequest;
import com.prodox.dto.MetricParametrizacionDto;
import com.prodox.dto.RankingMetricaDto;
import com.prodox.dto.TopParametrizacionDto;
import com.prodox.entity.Factor;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.MetricUsoRanking;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.FactorRepository;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.MetricUsoRankingRepository;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricRankingService {

    private final MetricParametrizacionRepository parametrizacionRepo;
    private final MetricUsoRankingRepository      rankingRepo;
    private final FactorRepository                factorRepo;
    private final MetricaRepository               metricaRepo;
    private final PlaneacionService               planeacionService;
    private final VariableDinamicaService         variableDinamicaService;
    private final ProjectMemberRepository         projectMemberRepo;
    private final ObjectMapper                    objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Scrum Master aprueba o rechaza una parametrización.
     * Solo usuarios con rol scrum_master (del proyecto de la parametrización)
     * pueden llamar esto. userId se usa solo para validar el rol; revisadoPor
     * (email) se conserva sin cambios como valor histórico del campo de auditoría.
     */
    @Transactional
    public MetricParametrizacionDto verificar(com.prodox.dto.VerificarParametrizacionRequest req,
                                              String userId, String revisadoPor) {
        MetricParametrizacion p = parametrizacionRepo.findById(req.parametrizacionId())
                .orElseThrow(() -> new IllegalArgumentException("Parametrización no encontrada."));

        validarScrumMaster(userId, p.getProyectoId());

        if ("aprobar".equals(req.accion())) {
            // FASE 10: antes de aprobar esta versión, desactivar la versión previamente
            // aprobada de la MISMA métrica+proyecto (igual que
            // ParametrizacionService.aprobarParametrizacion()), para que nunca coexistan
            // dos filas 'aprobada' de la misma métrica en el mismo proyecto.
            if (p.getMetricaId() != null && p.getProyectoId() != null) {
                // Corrección solicitada: el usuario NO debe preocuparse por el límite de 120
                // caracteres del Identificador técnico. Si falta o es inválido (típicamente
                // porque nunca se editó, o porque indicadorVariable es una descripción larga),
                // NO se rechaza la aprobación — se genera y persiste uno seguro automáticamente
                // (asegurarNombreVariable, más abajo) antes de continuar. indicadorVariable/
                // objetivo/procedimiento nunca se tocan acá.
                asegurarNombreVariable(p);
                parametrizacionRepo.findUltimaVersionAprobada(p.getMetricaId(), p.getProyectoId())
                        .filter(anterior -> !anterior.getId().equals(p.getId()))
                        .ifPresent(anterior -> {
                            anterior.setStatus("inactiva");
                            parametrizacionRepo.save(anterior);
                        });
            }
            p.setStatus("aprobada");
            // Si la parametrización tiene metricaId y proyectoId, aprobar la métrica
            // en Planeación para generar la variable de Ejecución
            if (p.getMetricaId() != null && p.getProyectoId() != null) {
                try {
                    // FASE 10: materializar PRIMERO la variable versionada (parametrizacion_id +
                    // version) de esta parametrización concreta. planeacionService.aprobar()
                    // (más abajo) solo crea su variable genérica si NINGUNA variable existe
                    // todavía para la métrica+proyecto — al crear la versionada primero, ese
                    // camino queda cerrado y no se duplica (ver diagnóstico FASE 9, bloques 3/9).
                    variableDinamicaService.materializarVariables(p);

                    // Verificar si ya está aprobada antes de intentar aprobar
                    var existing = planeacionService.listarSeleccionadas(p.getProyectoId()).stream()
                            .filter(m -> m.metricaId().equals(p.getMetricaId()))
                            .findFirst();

                    if (existing.isEmpty() || !existing.get().aprobada()) {
                        planeacionService.aprobar(p.getProyectoId(), p.getMetricaId(), revisadoPor);
                    }
                } catch (NombreVariableInvalidoException e) {
                    // Corrección del defecto documentado en FASE 17: antes, esta excepción
                    // caía en el catch genérico de abajo y solo se registraba en log,
                    // dejando la parametrización marcada "aprobada" en BD sin variable
                    // funcional y sin aviso alguno al Scrum Master. Se relanza para que
                    // la transacción completa de verificar() haga rollback (la
                    // parametrización NO queda persistida como 'aprobada') y el Scrum
                    // Master reciba el mensaje claro vía HTTP 400 (GlobalExceptionHandler
                    // ya mapea IllegalArgumentException, superclase de
                    // NombreVariableInvalidoException, a 400 con el mensaje original).
                    throw e;
                } catch (Exception e) {
                    // Log para diagnosticar si falla la generación de variable
                    System.err.println("[VERIFICAR] Error al aprobar métrica en Planeación: "
                            + e.getMessage() + " | metricaId=" + p.getMetricaId()
                            + " | proyectoId=" + p.getProyectoId());
                }
            }
        } else if ("rechazar".equals(req.accion())) {
            if (req.motivoRechazo() == null || req.motivoRechazo().isBlank()) {
                throw new IllegalArgumentException("El motivo de rechazo es obligatorio.");
            }
            p.setStatus("rechazada");
            p.setMotivoRechazo(req.motivoRechazo());
        } else {
            throw new IllegalArgumentException("Acción inválida. Use 'aprobar' o 'rechazar'.");
        }
        p.setRevisadoPor(revisadoPor);
        p.setRevisadoAt(java.time.Instant.now());

        return toDto(parametrizacionRepo.save(p), p.getFactor());
    }

    /**
     * Lista todas las parametrizaciones pendientes de verificación (para el Scrum Master).
     */
    public List<MetricParametrizacionDto> getPendientes() {
        return parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente").stream()
                .map(p -> toDto(p, p.getFactor()))
                .toList();
    }

    /**
     * Lista parametrizaciones pendientes de UN proyecto (Verificación).
     *
     * Corrección de aislamiento: antes, cuando no se informaba proyectoId (pantalla de
     * Verificación sin proyecto activo en localStorage), este método devolvía TODAS las
     * parametrizaciones con proyecto_id NULL sin importar de qué usuario fueran —
     * permitiendo que propuestas huérfanas/históricas de otros usuarios aparecieran como
     * si fueran del proyecto que el Scrum Master está revisando (ver auditoría de datos
     * con proyecto_id NULL). Ahora proyectoId es obligatorio para obtener resultados: sin
     * proyecto activo se devuelve una lista vacía en vez de inferir una asociación que no
     * existe. Con proyectoId informado, el filtro por igualdad exacta (proyectoId.equals)
     * ya excluía por sí solo tanto los de otros proyectos como los de proyecto_id NULL —
     * ese caso no tenía el defecto y no cambia de comportamiento acá.
     */
    public List<MetricParametrizacionDto> getPendientesPorProyecto(UUID proyectoId, String userId) {
        if (proyectoId == null) {
            return List.of();
        }
        validarScrumMaster(userId, proyectoId);
        return parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente").stream()
                .filter(p -> proyectoId.equals(p.getProyectoId()))
                .map(p -> toDto(p, p.getFactor()))
                .toList();
    }

    /**
     * Resumen persistente (consultado en BD) de pendientes/aprobadas/rechazadas de un
     * proyecto. FASE 10: para que los contadores de Verificación reflejen el estado real
     * al entrar o recargar la pantalla, en vez de depender solo de la memoria de sesión
     * del componente (ver diagnóstico FASE 9, bloque 4). Requiere ser miembro del proyecto.
     */
    public com.prodox.dto.ResumenVerificacionDto getResumenPorProyecto(UUID proyectoId, String userId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        return new com.prodox.dto.ResumenVerificacionDto(
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "pendiente"),
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "aprobada"),
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "rechazada")
        );
    }

    /**
     * Actualiza los campos editables de una parametrización pendiente.
     * Solo el Scrum Master puede editar parametrizaciones antes de aprobarlas.
     */
    @Transactional
    public MetricParametrizacionDto actualizar(UUID parametrizacionId,
                                               com.prodox.dto.ActualizarParametrizacionRequest req,
                                               String userId) {
        MetricParametrizacion p = parametrizacionRepo.findById(parametrizacionId)
                .orElseThrow(() -> new IllegalArgumentException("Parametrización no encontrada."));

        validarScrumMaster(userId, p.getProyectoId());

        if (!"pendiente".equals(p.getStatus())) {
            throw new IllegalStateException("Solo se pueden editar parametrizaciones en estado 'pendiente'");
        }

        ParametrizacionService.validarEscalaEstructurada(req.escalaTipo(), req.escalaMin(), 
            req.escalaMax(), req.escalaPaso(), req.escalaSinLimite());

        p.setObjetivo(req.objetivo());
        p.setProcedimiento(req.procedimiento());
        p.setIndicadorVariable(req.indicadorVariable());
        p.setEscala(req.escala());
        p.setEscalaTipo(req.escalaTipo());
        p.setEscalaMin(req.escalaMin());
        p.setEscalaMax(Boolean.TRUE.equals(req.escalaSinLimite()) ? null : req.escalaMax());
        p.setEscalaPaso(req.escalaPaso());
        p.setEscalaSinLimite(req.escalaSinLimite());
        p.setEscalaDescripcion(req.escalaDescripcion());
        resolverYGuardarNombreVariable(p, req.nombreVariable());

        MetricParametrizacion updated = parametrizacionRepo.save(p);
        return toDto(updated, updated.getFactor());
    }

    /** Longitud máxima del Identificador técnico — regla existente, sin cambios. */
    private static final int NOMBRE_VARIABLE_MAX = 120;
    /** Longitud del sufijo hash usado para desambiguar identificadores generados. */
    private static final int HASH_SUFFIX_LEN = 10;

    /**
     * Resuelve el Identificador técnico (nombreVariable) para una edición y lo persiste
     * en el snapshot.
     *
     * Corrección solicitada: el usuario NO debe preocuparse por el límite de 120
     * caracteres. Si informa un valor explícito y ya es válido (formato snake_case,
     * máx. 120), se usa tal cual. Si no informa nada, o lo que informó no es válido
     * (típicamente porque pegó una descripción larga), NUNCA se rechaza la edición:
     * se genera automáticamente un identificador corto, determinista y válido a
     * partir de ese mismo texto (o de indicadorVariable si no escribió nada) —
     * ver generarNombreVariableSeguro(). indicadorVariable/objetivo/procedimiento
     * nunca se truncan ni se modifican acá; el snapshot se guarda en la misma
     * columna jsonb ya usada por el flujo académico (sin migraciones nuevas).
     */
    private void resolverYGuardarNombreVariable(MetricParametrizacion p, String nombreVariableCrudo) {
        String explicito = nombreVariableCrudo != null ? nombreVariableCrudo.trim() : null;
        String base = (explicito != null && !explicito.isBlank()) ? explicito : p.getIndicadorVariable();
        String resuelto = esNombreVariableValido(base) ? base : generarNombreVariableSeguro(base);
        guardarSnapshotConNombreVariable(p, resuelto);
    }

    /**
     * Garantiza que la parametrización tenga un Identificador técnico válido antes de
     * aprobar, sin rechazar nunca la aprobación por este motivo (mismo criterio que
     * resolverYGuardarNombreVariable). Cubre el caso de una parametrización que nunca
     * pasó por "Editar": ahí se genera uno a partir de indicadorVariable en este mismo
     * momento. Si ya tiene uno válido guardado, no hace nada (no lo regenera ni lo
     * cambia — estabilidad para parametrizaciones ya editadas).
     */
    private void asegurarNombreVariable(MetricParametrizacion p) {
        String actual = leerNombreVariableGuardado(p);
        if (esNombreVariableValido(actual)) {
            return;
        }
        String base = (actual != null && !actual.isBlank()) ? actual : p.getIndicadorVariable();
        guardarSnapshotConNombreVariable(p, generarNombreVariableSeguro(base));
    }

    private void guardarSnapshotConNombreVariable(MetricParametrizacion p, String nombreVariable) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("indicadorVariable", p.getIndicadorVariable());
            snapshot.put("procedimiento", p.getProcedimiento());
            snapshot.put("frecuenciaCaptura",
                    p.getFrecuenciaCaptura() != null ? p.getFrecuenciaCaptura() : "por_sprint");
            snapshot.put("nombreVariable", nombreVariable);
            p.setConfiguracionAprobadaJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error construyendo snapshot de configuración", e);
        }
    }

    /** true si candidato ya cumple, tal cual, la regla existente (ParametrizacionService). */
    private boolean esNombreVariableValido(String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return false;
        }
        try {
            ParametrizacionService.validarNombreVariable(candidato);
            return true;
        } catch (NombreVariableInvalidoException e) {
            return false;
        }
    }

    /**
     * Genera un Identificador técnico corto, determinista y válido (máx. 120
     * caracteres) a partir de un texto libre (indicadorVariable, o lo que el usuario
     * haya escrito en el campo). Reutiliza la extracción/normalización a snake_case
     * ya existente (ParametrizacionService.extraerNombresVariables — soporta listas
     * separadas por coma para métricas FORMULA de más de una variable, igual que el
     * resto del sistema; no se duplica ese algoritmo). El único caso nuevo es cuando
     * el resultado de esa extracción sigue siendo inválido (típicamente >120
     * caracteres, por una descripción larga): en vez de rechazar, o de recortar
     * simplemente los primeros 120 caracteres (lo que podría hacer colisionar dos
     * descripciones distintas que comparten el mismo prefijo), se recorta a un
     * prefijo legible cortado en un límite de palabra y se le agrega un sufijo hash
     * determinista (SHA-256 del candidato completo) — dos textos distintos casi
     * nunca terminan en el mismo identificador, y el mismo texto siempre genera el
     * mismo identificador.
     */
    private String generarNombreVariableSeguro(String texto) {
        String base = (texto != null && !texto.isBlank()) ? texto : "variable";
        String[] derivados = ParametrizacionService.extraerNombresVariables(base);
        if (derivados.length == 0) {
            return acortarConHashDeterminista(base);
        }
        List<String> resultado = new java.util.ArrayList<>();
        for (String candidato : derivados) {
            resultado.add(esNombreVariableValido(candidato) ? candidato : acortarConHashDeterminista(candidato));
        }
        return String.join(",", resultado);
    }

    /**
     * Recorta candidatoInvalido a un prefijo legible (cortado en el último "_" antes
     * del límite, nunca a mitad de palabra) y le agrega un sufijo hash determinista
     * de HASH_SUFFIX_LEN caracteres — nunca un substring(0,120) simple, precisamente
     * para no colisionar cuando dos descripciones distintas comparten prefijo.
     */
    private String acortarConHashDeterminista(String candidatoInvalido) {
        String hash = sha256Hex(candidatoInvalido).substring(0, HASH_SUFFIX_LEN);
        int presupuestoPrefijo = NOMBRE_VARIABLE_MAX - HASH_SUFFIX_LEN - 1; // -1 por el "_"
        String prefijo = candidatoInvalido.length() > presupuestoPrefijo
                ? candidatoInvalido.substring(0, presupuestoPrefijo)
                : candidatoInvalido;
        int ultimoGuion = prefijo.lastIndexOf('_');
        if (ultimoGuion > 0) {
            prefijo = prefijo.substring(0, ultimoGuion);
        }
        // Defensa adicional por si candidatoInvalido era inválido por FORMATO (no solo
        // longitud): quedarse solo con [a-z0-9_] y forzar que empiece con una letra,
        // igual que exige la regla existente.
        prefijo = prefijo.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (prefijo.isEmpty() || !Character.isLetter(prefijo.charAt(0))) {
            prefijo = "v" + prefijo;
        }
        String seguro = prefijo + "_" + hash;
        return esNombreVariableValido(seguro) ? seguro : ("var_" + hash);
    }

    private static String sha256Hex(String texto) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Lee el Identificador técnico ya guardado (si lo hay) desde el snapshot
     * configuracionAprobadaJson de una parametrización. Ausencia o error de parseo
     * se tratan igual (null): la parametrización se considera sin identificador.
     */
    private String leerNombreVariableGuardado(MetricParametrizacion p) {
        String snapshotJson = p.getConfiguracionAprobadaJson();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode json = objectMapper.readTree(snapshotJson);
            return json.hasNonNull("nombreVariable") ? json.get("nombreVariable").asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Guarda la parametrización de una métrica para un proyecto (flujo "Enviar al Scrum Master").
     *
     * FASE 10: la parametrización es INMUTABLE (ver MetricParametrizacion, comentario de clase):
     * cada envío crea una versión NUEVA para metricaId+proyectoId, igual que
     * ParametrizacionService.guardarPropuesta() hace para el flujo académico. Nunca se busca
     * "la parametrización existente del usuario" de forma global (eso permitía que reenviar la
     * misma métrica sobrescribiera en el sitio una fila de OTRO proyecto, o degradara a
     * "pendiente" una versión ya aprobada — ver diagnóstico FASE 9, bloque 1).
     */
    @Transactional
    public MetricParametrizacionDto guardar(GuardarParametrizacionRequest req,
                                            String userId, String userEmail) {
        if (req.proyectoId() != null
                && !projectMemberRepo.existsByProyectoIdAndUserId(req.proyectoId(), userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        ParametrizacionService.validarEscalaEstructurada(req.escalaTipo(), req.escalaMin(), req.escalaMax(),
            req.escalaPaso(), req.escalaSinLimite());
        ParametrizacionService.validarResponsableCaptura(req.responsableCaptura());
        if (req.metricaId() != null) {
            return guardarPorMetrica(req, userId, userEmail);
        } else if (req.factorId() != null) {
            return guardarPorFactor(req, userId, userEmail);
        }
        throw new IllegalArgumentException("Debe especificar metricaId o factorId.");
    }

    /**
     * Crea una nueva versión de parametrización para metricaId+proyectoId. La siguiente versión
     * se calcula sobre el máximo histórico real (cualquier status), igual que
     * ParametrizacionService.guardarPropuesta(), para no colisionar con la restricción de
     * unicidad (proyecto+métrica+versión) ni pisar una propuesta/aprobada existente.
     */
    private MetricParametrizacionDto guardarPorMetrica(GuardarParametrizacionRequest req,
                                                        String userId, String userEmail) {
        if (req.proyectoId() == null) {
            throw new IllegalArgumentException("proyectoId es obligatorio para parametrizar una métrica.");
        }

        // Corrección de duplicados en Verificación: serializa, por (proyectoId, metricaId),
        // toda la sección crítica "leer historial -> decidir version/duplicado -> insertar"
        // que sigue debajo. Sin esto, dos peticiones que se solapan (doble clic, doble envío
        // por dos pantallas distintas, reintento de red) pueden leer AMBAS el mismo historial
        // antes de que ninguna haga commit, pasar igual la comprobación de esMismoContenido()
        // (evaluada contra datos aún no confirmados) y terminar creando dos filas 'pendiente'
        // para la misma métrica+proyecto — exactamente el defecto reportado. El lock de
        // advisory de Postgres es de transacción (se libera solo al hacer commit/rollback de
        // esta @Transactional), así que la segunda petición queda bloqueada hasta que la
        // primera termine por completo, y entonces SÍ ve su fila ya confirmada.
        adquirirLockParametrizacion(req.proyectoId(), req.metricaId());

        Integer siguienteVersion = 1;
        var historial = parametrizacionRepo.findHistorialVersiones(req.metricaId(), req.proyectoId());
        if (!historial.isEmpty()) {
            MetricParametrizacion ultima = historial.get(0);
            // FASE 20: si la última versión sigue "pendiente" y tiene EXACTAMENTE el
            // mismo contenido que este envío, es un duplicado (doble clic que superó
            // el guard de frontend, recarga/navegación durante un envío ya en curso,
            // etc. — ver diagnóstico FASE 20) y no una nueva versión legítima.
            // Se devuelve la fila existente en vez de crear una idéntica nueva.
            // Un reenvío con contenido distinto, o sobre una versión ya aprobada o
            // rechazada, sigue creando una versión nueva exactamente como antes.
            if ("pendiente".equals(ultima.getStatus()) && esMismoContenido(ultima, req)) {
                return toDto(ultima, null);
            }
            siguienteVersion = ultima.getVersion() + 1;
        }

        MetricParametrizacion p = new MetricParametrizacion();
        p.setVersion(siguienteVersion);
        p.setUserId(userId);
        p.setUserEmail(userEmail);
        p.setProyectoId(req.proyectoId());
        // Solo asignar metricaId si realmente existe en la tabla metricas
        p.setMetricaId(metricaRepo.existsById(req.metricaId()) ? req.metricaId() : null);
        p.setObjetivo(req.objetivo());
        p.setProcedimiento(req.procedimiento());
        p.setIndicadorVariable(req.indicadorVariable());
        p.setEscala(req.escala());
        p.setEscalaTipo(req.escalaTipo());
        p.setEscalaMin(req.escalaMin());
        p.setEscalaMax(Boolean.TRUE.equals(req.escalaSinLimite()) ? null : req.escalaMax());
        p.setEscalaPaso(req.escalaPaso());
        p.setEscalaSinLimite(req.escalaSinLimite());
        p.setEscalaDescripcion(req.escalaDescripcion());
        p.setMetricaBaseId(req.metricaBaseId());
        // FASE 11: propagar los campos académicos que el usuario completó en el formulario
        // (nunca copiados de otra parametrización) — sin esto, MetricaAcademicaService
        // rechaza el cálculo con 409 "no tiene tipo de operación definido" aun aprobada.
        p.setTipoOperacion(req.tipoOperacion());
        p.setFormulaAcademica(req.formulaAcademica());
        p.setUnidadResultado(req.unidadResultado());
        p.setFuenteAcademica(req.fuenteAcademica());
        // Revisión de frecuencia de captura: antes no se asignaba acá, así que la
        // entidad quedaba siempre en su default "por_sprint" sin importar lo que
        // el usuario eligiera en el formulario de Planeación (ver GuardarParametrizacionRequest).
        p.setFrecuenciaCaptura(req.frecuenciaCaptura() != null ? req.frecuenciaCaptura() : "por_sprint");
        // Revisión de captura por parametrización: independiente de tipoOperacion —
        // decide QUIÉN captura (EQUIPO/SCRUM_MASTER), no CÓMO se calcula.
        p.setResponsableCaptura(req.responsableCaptura() != null
            ? req.responsableCaptura()
            : ParametrizacionService.RESPONSABLE_CAPTURA_DEFAULT);
        p.setStatus("pendiente");

        MetricParametrizacion saved = parametrizacionRepo.save(p);
        return toDto(saved, null);
    }

    /**
     * Corrección de duplicados en Verificación: adquiere un advisory lock de
     * transacción de Postgres (pg_advisory_xact_lock), con clave = hash de
     * (proyectoId, metricaId). No requiere ninguna migración ni tabla nueva —
     * es un lock en memoria del servidor de BD, exclusivamente para la
     * duración de la transacción actual, liberado automáticamente al hacer
     * commit o rollback (nunca queda "colgado" ante una excepción). Dos
     * transacciones concurrentes con la misma clave se serializan: la segunda
     * espera a que la primera termine antes de continuar, así que cuando
     * retoma, su propia lectura de historial ya ve (bajo READ COMMITTED) la
     * fila que la primera haya confirmado.
     */
    private void adquirirLockParametrizacion(java.util.UUID proyectoId, java.util.UUID metricaId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:proyectoId), hashtext(:metricaId))")
                .setParameter("proyectoId", proyectoId.toString())
                .setParameter("metricaId", metricaId.toString())
                .getSingleResult();
    }

    /**
     * FASE 20: compara el contenido relevante de una parametrización ya existente
     * contra un nuevo request, para distinguir un reenvío duplicado (mismo
     * contenido) de una nueva versión legítima (contenido editado).
     */
    private boolean esMismoContenido(MetricParametrizacion existente, GuardarParametrizacionRequest req) {
        String frecuenciaReq = req.frecuenciaCaptura() != null ? req.frecuenciaCaptura() : "por_sprint";
        String responsableReq = req.responsableCaptura() != null
            ? req.responsableCaptura() : ParametrizacionService.RESPONSABLE_CAPTURA_DEFAULT;
        return java.util.Objects.equals(existente.getObjetivo(), req.objetivo())
            && java.util.Objects.equals(existente.getProcedimiento(), req.procedimiento())
            && java.util.Objects.equals(existente.getIndicadorVariable(), req.indicadorVariable())
            && java.util.Objects.equals(existente.getEscala(), req.escala())
            && java.util.Objects.equals(existente.getTipoOperacion(), req.tipoOperacion())
            && java.util.Objects.equals(existente.getFormulaAcademica(), req.formulaAcademica())
            && java.util.Objects.equals(existente.getUnidadResultado(), req.unidadResultado())
            && java.util.Objects.equals(existente.getFuenteAcademica(), req.fuenteAcademica())
            // Revisión de frecuencia de captura: si el usuario solo cambió la
            // frecuencia (todo lo demás igual), no es el mismo contenido — antes
            // esta comparación no incluía frecuenciaCaptura y el reenvío se
            // descartaba silenciosamente devolviendo la versión vieja sin el cambio.
            && java.util.Objects.equals(existente.getFrecuenciaCaptura(), frecuenciaReq)
            // Revisión de captura por parametrización: si el usuario solo cambió el
            // alcance/responsable (todo lo demás igual), es una edición real que
            // cambia quién puede capturar — no un reenvío duplicado.
            && java.util.Objects.equals(existente.getResponsableCaptura(), responsableReq)
            // Corrección del manejo de escalas: si el usuario solo cambió la escala
            // estructurada (todo lo demás igual), es una edición real, no un reenvío
            // duplicado — debe crear una versión nueva.
            && java.util.Objects.equals(existente.getEscalaTipo(), req.escalaTipo())
            && java.util.Objects.equals(existente.getEscalaMin(), req.escalaMin())
            && java.util.Objects.equals(existente.getEscalaMax(),
                    Boolean.TRUE.equals(req.escalaSinLimite()) ? null : req.escalaMax())
            && java.util.Objects.equals(existente.getEscalaPaso(), req.escalaPaso())
            && java.util.Objects.equals(existente.getEscalaSinLimite(), req.escalaSinLimite());
    }

    /**
     * Legacy: parametrización "base" de un factor (sin versionado por métrica/proyecto,
     * usada por el Top 3 / ranking). Se mantiene aislada por proyecto y nunca degrada una
     * fila ya aprobada — si la existente está aprobada, se crea una fila nueva en su lugar
     * en vez de sobrescribirla.
     */
    private MetricParametrizacionDto guardarPorFactor(GuardarParametrizacionRequest req,
                                                       String userId, String userEmail) {
        Factor factor = factorRepo.findById(req.factorId()).orElse(null);

        MetricParametrizacion p = req.proyectoId() != null
                ? parametrizacionRepo.findByUserIdAndFactor_IdAndProyectoId(userId, req.factorId(), req.proyectoId())
                        .orElse(null)
                : null;

        if (p != null && "aprobada".equals(p.getStatus())) {
            p = null;
        }

        if (p == null) {
            p = new MetricParametrizacion();
            p.setFactor(factor);
            p.setUserId(userId);
            p.setUserEmail(userEmail);
            p.setProyectoId(req.proyectoId());
        }

        p.setObjetivo(req.objetivo());
        p.setProcedimiento(req.procedimiento());
        p.setIndicadorVariable(req.indicadorVariable());
        p.setEscala(req.escala());
        p.setEscalaTipo(req.escalaTipo());
        p.setEscalaMin(req.escalaMin());
        p.setEscalaMax(Boolean.TRUE.equals(req.escalaSinLimite()) ? null : req.escalaMax());
        p.setEscalaPaso(req.escalaPaso());
        p.setEscalaSinLimite(req.escalaSinLimite());
        p.setEscalaDescripcion(req.escalaDescripcion());
        p.setMetricaBaseId(req.metricaBaseId());
        p.setResponsableCaptura(req.responsableCaptura() != null
            ? req.responsableCaptura()
            : ParametrizacionService.RESPONSABLE_CAPTURA_DEFAULT);
        p.setStatus("pendiente");
        p.setRevisadoPor(null);
        p.setRevisadoAt(null);
        p.setMotivoRechazo(null);

        MetricParametrizacion saved = parametrizacionRepo.save(p);

        // Actualizar ranking (solo aplica a la parametrización "base" por factor)
        if (factor != null) {
            try {
                MetricUsoRanking ranking = rankingRepo.findById(factor.getId())
                        .orElseGet(() -> {
                            MetricUsoRanking r = new MetricUsoRanking();
                            r.setFactor(factor);
                            r.setUsos(0);
                            return r;
                        });
                ranking.setParametrizacionId(saved.getId());
                ranking.setUsos(ranking.getUsos() + 1);
                ranking.setUpdatedAt(Instant.now());
                rankingRepo.save(ranking);
            } catch (Exception e) {
                // Ignorar errores de ranking para no bloquear la parametrización
                System.err.println("[GUARDAR] Error al actualizar ranking: " + e.getMessage());
            }
        }

        return toDto(saved, factor);
    }

    /**
     * Incrementa el contador de usos de la métrica base cuando otro usuario la selecciona.
     */
    @Transactional
    public void incrementarUso(UUID factorId) {
        rankingRepo.findById(factorId).ifPresent(r -> {
            r.setUsos(r.getUsos() + 1);
            r.setUpdatedAt(Instant.now());
            rankingRepo.save(r);
        });
    }

    /**
     * Devuelve la parametrización base más reciente de un factor.
     */
    public Optional<MetricParametrizacionDto> getBase(UUID factorId) {
        return parametrizacionRepo
                .findTopByFactor_IdAndMetricaBaseIdIsNullOrderByCreatedAtDesc(factorId)
                .map(p -> toDto(p, p.getFactor()));
    }

    /**
     * Top 3 parametrizaciones de un factor, ordenadas por fecha de creación descendente.
     */
    public List<TopParametrizacionDto> getTop3(UUID factorId) {
        Map<UUID, Integer> usosMap = rankingRepo.findAll().stream()
                .filter(r -> r.getParametrizacionId() != null)
                .collect(Collectors.toMap(
                        MetricUsoRanking::getParametrizacionId,
                        MetricUsoRanking::getUsos,
                        (a, b) -> a
                ));

        return parametrizacionRepo.findTop3BaseByFactorId(factorId).stream()
                .map(p -> new TopParametrizacionDto(
                        p.getId(),
                        p.getUserEmail(),
                        p.getObjetivo(),
                        p.getProcedimiento(),
                        p.getIndicadorVariable(),
                        p.getEscala(),
                        usosMap.getOrDefault(p.getId(), 0),
                        p.getCreatedAt(),
                        p.getFrecuenciaCaptura(),
                        p.getFuenteAcademica(),
                        p.getFormulaAcademica(),
                        p.getTipoOperacion(),
                        p.getUnidadResultado(),
                        // Campos de escala estructurada
                        p.getEscalaTipo(),
                        p.getEscalaMin(),
                        p.getEscalaMax(),
                        p.getEscalaPaso(),
                        p.getEscalaSinLimite(),
                        p.getEscalaDescripcion()
                ))
                .toList();
    }

    /**
     * Top 3 parametrizaciones por metricaId (flujo desde Planeación).
     * Los "usos" se calculan como la cantidad total de parametrizaciones
     * guardadas para esta métrica (indica popularidad).
     */
    public List<TopParametrizacionDto> getTop3ByMetricaId(UUID metricaId) {
        List<MetricParametrizacion> todas = parametrizacionRepo.findTop3ByMetricaId(metricaId);
        // Contar usos totales para esta métrica (todas las parametrizaciones de todos los usuarios)
        long usosTotales = parametrizacionRepo.countByMetricaId(metricaId);

        return todas.stream()
                .map(p -> new TopParametrizacionDto(
                        p.getId(),
                        p.getUserEmail(),
                        p.getObjetivo(),
                        p.getProcedimiento(),
                        p.getIndicadorVariable(),
                        p.getEscala(),
                        (int) usosTotales,
                        p.getCreatedAt(),
                        p.getFrecuenciaCaptura(),
                        p.getFuenteAcademica(),
                        p.getFormulaAcademica(),
                        p.getTipoOperacion(),
                        p.getUnidadResultado(),
                        // Campos de escala estructurada
                        p.getEscalaTipo(),
                        p.getEscalaMin(),
                        p.getEscalaMax(),
                        p.getEscalaPaso(),
                        p.getEscalaSinLimite(),
                        p.getEscalaDescripcion()
                ))
                .toList();
    }

    /**
     * Parametrización base más reciente por metricaId.
     */
    public Optional<MetricParametrizacionDto> getBaseByMetricaId(UUID metricaId) {
        return parametrizacionRepo
                .findTopByMetricaIdOrderByCreatedAtDesc(metricaId)
                .map(p -> toDto(p, p.getFactor()));
    }

    /**
     * Top 5 factores más usados para mostrar en la pantalla de Selección.
     */
    public List<RankingMetricaDto> getRanking() {
        return rankingRepo.findTop5ByOrderByUsosDesc().stream()
                .map(r -> new RankingMetricaDto(
                        r.getFactor().getId(),
                        r.getFactor().getName(),
                        r.getFactor().getCategory(),
                        r.getUsos(),
                        r.getParametrizacionId()
                ))
                .toList();
    }

    /** true si userId es Scrum Master de proyectoId (rol evaluado por PROYECTO, no global). */
    private boolean esScrumMaster(String userId, UUID proyectoId) {
        return projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId)
                .map(ProjectMember::getRol)
                .filter("scrum_master"::equals)
                .isPresent();
    }

    /** Exige que userId sea Scrum Master de proyectoId, o lanza SecurityException (403). */
    private void validarScrumMaster(String userId, UUID proyectoId) {
        if (proyectoId == null) {
            return;
        }
        if (!esScrumMaster(userId, proyectoId)) {
            throw new SecurityException("Solo el Scrum Master del proyecto puede realizar esta acción.");
        }
    }

    private MetricParametrizacionDto toDto(MetricParametrizacion p, Factor f) {
        UUID   fId        = f != null ? f.getId()       : null;
        String fNombre;
        String fCategoria;

        if (f != null) {
            fNombre    = f.getName();
            fCategoria = f.getCategory();
        } else if (p.getMetricaId() != null) {
            // Flujo desde Planeación: buscar nombre y categoría de la métrica
            var metrica = metricaRepo.findById(p.getMetricaId()).orElse(null);
            fNombre    = metrica != null ? metrica.getNombre()                    : "Métrica";
            fCategoria = metrica != null ? metrica.getCategoria().getNombre()     : "—";
        } else {
            fNombre    = "—";
            fCategoria = "—";
        }

        return new MetricParametrizacionDto(
                p.getId(),
                p.getVersion(),
                fId,
                fNombre,
                fCategoria,
                p.getUserEmail(),
                p.getObjetivo(),
                p.getProcedimiento(),
                p.getIndicadorVariable(),
                p.getEscala(),
                p.getFrecuenciaCaptura(),
                p.getMetricaBaseId(),
                p.getStatus(),
                p.getRevisadoPor(),
                p.getRevisadoAt(),
                p.getMotivoRechazo(),
                p.getProyectoId(),
                p.getCreatedAt(),
                p.getPropuestaIAJson(),
                p.getConfiguracionAprobadaJson(),
                p.getFuenteAcademica(),
                p.getFormulaAcademica(),
                p.getTipoOperacion(),
                p.getUnidadResultado(),
                p.getResponsableCaptura(),
                p.getEscalaTipo(),
                p.getEscalaMin(),
                p.getEscalaMax(),
                p.getEscalaPaso(),
                p.getEscalaSinLimite(),
                p.getEscalaDescripcion()
        );
    }
}
