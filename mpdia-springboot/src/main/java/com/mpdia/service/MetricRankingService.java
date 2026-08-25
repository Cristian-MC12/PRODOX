// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.RankingMetricaDto;
import com.mpdia.dto.TopParametrizacionDto;
import com.mpdia.entity.Factor;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.MetricUsoRanking;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.MetricUsoRankingRepository;
import com.mpdia.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /**
     * Scrum Master aprueba o rechaza una parametrización.
     * Solo usuarios con rol scrum_master pueden llamar esto.
     */
    @Transactional
    public MetricParametrizacionDto verificar(com.mpdia.dto.VerificarParametrizacionRequest req,
                                              String revisadoPor) {
        MetricParametrizacion p = parametrizacionRepo.findById(req.parametrizacionId())
                .orElseThrow(() -> new IllegalArgumentException("Parametrización no encontrada."));

        if ("aprobar".equals(req.accion())) {
            // FASE 10: antes de aprobar esta versión, desactivar la versión previamente
            // aprobada de la MISMA métrica+proyecto (igual que
            // ParametrizacionService.aprobarParametrizacion()), para que nunca coexistan
            // dos filas 'aprobada' de la misma métrica en el mismo proyecto.
            if (p.getMetricaId() != null && p.getProyectoId() != null) {
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
     * Lista parametrizaciones pendientes filtradas por proyecto.
     */
    public List<MetricParametrizacionDto> getPendientesPorProyecto(UUID proyectoId) {
        return parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente").stream()
                .filter(p -> proyectoId == null || proyectoId.equals(p.getProyectoId()))
                .map(p -> toDto(p, p.getFactor()))
                .toList();
    }

    /**
     * Resumen persistente (consultado en BD) de pendientes/aprobadas/rechazadas de un
     * proyecto. FASE 10: para que los contadores de Verificación reflejen el estado real
     * al entrar o recargar la pantalla, en vez de depender solo de la memoria de sesión
     * del componente (ver diagnóstico FASE 9, bloque 4).
     */
    public com.mpdia.dto.ResumenVerificacionDto getResumenPorProyecto(UUID proyectoId) {
        return new com.mpdia.dto.ResumenVerificacionDto(
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "pendiente"),
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "aprobada"),
                parametrizacionRepo.countByProyectoIdAndStatus(proyectoId, "rechazada")
        );
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
        p.setStatus("pendiente");

        MetricParametrizacion saved = parametrizacionRepo.save(p);
        return toDto(saved, null);
    }

    /**
     * FASE 20: compara el contenido relevante de una parametrización ya existente
     * contra un nuevo request, para distinguir un reenvío duplicado (mismo
     * contenido) de una nueva versión legítima (contenido editado).
     */
    private boolean esMismoContenido(MetricParametrizacion existente, GuardarParametrizacionRequest req) {
        String frecuenciaReq = req.frecuenciaCaptura() != null ? req.frecuenciaCaptura() : "por_sprint";
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
            && java.util.Objects.equals(existente.getFrecuenciaCaptura(), frecuenciaReq);
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
        p.setMetricaBaseId(req.metricaBaseId());
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
                        p.getUnidadResultado()
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
                        p.getUnidadResultado()
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
                p.getUnidadResultado()
        );
    }
}
