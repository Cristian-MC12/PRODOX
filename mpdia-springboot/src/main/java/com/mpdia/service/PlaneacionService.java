// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ProyectoMetricaDto;
import com.mpdia.dto.VariableDto;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaneacionService {

    private final MetricaRepository               metricaRepo;
    private final ProyectoMetricaRepository       pmRepo;
    private final VariableRepository              variableRepo;
    private final ProyectoRepository              proyectoRepo;
    private final MetricParametrizacionRepository parametrizacionRepo;

    // ── Consultas ─────────────────────────────────────────────────────────

    /** Todas las métricas del catálogo, indicando si están seleccionadas/aprobadas en el proyecto */
    public List<ProyectoMetricaDto> listarMetricasConEstado(UUID proyectoId) {
        List<ProyectoMetrica> seleccionadas = pmRepo.findByIdProyectoId(proyectoId);

        return metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc().stream()
                .map(m -> {
                    ProyectoMetrica pm = seleccionadas.stream()
                            .filter(s -> s.getId().getMetricaId().equals(m.getId()))
                            .findFirst().orElse(null);
                    boolean tieneVariable = variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, m.getId());
                    return new ProyectoMetricaDto(
                            m.getId(), m.getCodigo(), m.getNombre(), m.getDescripcion(),
                            m.getCategoria().getNombre(),
                            m.getFactor(),
                            pm != null,
                            pm != null && pm.getAprobada(),
                            pm != null ? pm.getAprobadaPor() : null,
                            pm != null ? pm.getAprobadaAt()  : null,
                            tieneVariable
                    );
                }).toList();
    }

    /** Solo las seleccionadas en el proyecto */
    public List<ProyectoMetricaDto> listarSeleccionadas(UUID proyectoId) {
        return pmRepo.findByIdProyectoId(proyectoId).stream()
                .map(pm -> {
                    Metrica m = pm.getMetrica();
                    boolean tieneVariable = variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, m.getId());
                    return new ProyectoMetricaDto(
                            m.getId(), m.getCodigo(), m.getNombre(), m.getDescripcion(),
                            m.getCategoria().getNombre(),
                            m.getFactor(),
                            true,
                            pm.getAprobada(),
                            pm.getAprobadaPor(), pm.getAprobadaAt(),
                            tieneVariable
                    );
                }).toList();
    }

    // ── Selección / deselección ────────────────────────────────────────────

    @Transactional
    public void seleccionar(UUID proyectoId, UUID metricaId) {
        if (pmRepo.existsByIdProyectoIdAndIdMetricaId(proyectoId, metricaId)) return;

        Metrica m = metricaRepo.findById(metricaId)
                .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada."));

        ProyectoMetrica pm = new ProyectoMetrica();
        pm.getId().setProyectoId(proyectoId);
        pm.getId().setMetricaId(metricaId);
        pm.setMetrica(m);
        pmRepo.save(pm);
    }

    @Transactional
    public void deseleccionar(UUID proyectoId, UUID metricaId) {
        ProyectoMetricaId id = new ProyectoMetricaId();
        id.setProyectoId(proyectoId);
        id.setMetricaId(metricaId);
        pmRepo.deleteById(id);
    }

    // ── Aprobación → genera variable automáticamente ──────────────────────

    @Transactional
    public VariableDto aprobar(UUID proyectoId, UUID metricaId, String aprobadaPor) {
        ProyectoMetricaId pmId = new ProyectoMetricaId();
        pmId.setProyectoId(proyectoId);
        pmId.setMetricaId(metricaId);

        // Si no está seleccionada aún, seleccionarla automáticamente
        ProyectoMetrica pm = pmRepo.findById(pmId).orElseGet(() -> {
            Metrica m = metricaRepo.findById(metricaId)
                    .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada."));
            ProyectoMetrica nueva = new ProyectoMetrica();
            nueva.getId().setProyectoId(proyectoId);
            nueva.getId().setMetricaId(metricaId);
            nueva.setMetrica(m);
            return pmRepo.save(nueva);
        });

        pm.setAprobada(true);
        pm.setAprobadaPor(aprobadaPor);
        pm.setAprobadaAt(Instant.now());
        pmRepo.save(pm);

        // Conversión automática: si no existe ya la variable, crearla
        if (!variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)) {
            return generarVariable(proyectoId, pm.getMetrica());
        }
        return toVariableDto(variableRepo.findByProyectoIdAndMetrica_Id(proyectoId, metricaId)
                .orElseThrow());
    }

    @Transactional
    public void desaprobar(UUID proyectoId, UUID metricaId) {
        ProyectoMetricaId pmId = new ProyectoMetricaId();
        pmId.setProyectoId(proyectoId);
        pmId.setMetricaId(metricaId);

        ProyectoMetrica pm = pmRepo.findById(pmId)
                .orElseThrow(() -> new IllegalArgumentException("Métrica no seleccionada."));
        pm.setAprobada(false);
        pm.setAprobadaPor(null);
        pm.setAprobadaAt(null);
        pmRepo.save(pm);

        // Desactivar variable si existía
        variableRepo.findByProyectoIdAndMetrica_Id(proyectoId, metricaId)
                .ifPresent(v -> { v.setActiva(false); variableRepo.save(v); });
    }

    // ── Variables generadas ───────────────────────────────────────────────

    public List<VariableDto> listarVariables(UUID proyectoId) {
        return variableRepo.findByProyectoIdAndActivaTrue(proyectoId)
                .stream().map(this::toVariableDto).toList();
    }

    /**
     * Sincroniza variables: genera las que faltan para métricas ya aprobadas.
     * También auto-aprueba métricas seleccionadas que ya tienen parametrización.
     * Útil para recuperar variables que no se crearon por bugs previos.
     */
    @Transactional
    public List<VariableDto> sincronizarVariables(UUID proyectoId) {
        autoAprobarMetricasConParametrizacion(proyectoId, "sistema-sync");

        // Generar variables para todas las aprobadas
        List<ProyectoMetrica> aprobadas = pmRepo.findByIdProyectoIdAndAprobadaTrue(proyectoId);

        for (ProyectoMetrica pm : aprobadas) {
            UUID metricaId = pm.getId().getMetricaId();
            boolean existe = variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId);
            if (!existe) {
                generarVariable(proyectoId, pm.getMetrica());
            } else {
                variableRepo.findByProyectoIdAndMetrica_Id(proyectoId, metricaId)
                        .filter(v -> !v.getActiva())
                        .ifPresent(v -> { v.setActiva(true); variableRepo.save(v); });
            }
        }
        return listarVariables(proyectoId);
    }

    /**
     * Auto-aprueba métricas seleccionadas que tienen parametrización asociada
     */
    private void autoAprobarMetricasConParametrizacion(UUID proyectoId, String aprobadaPor) {
        List<ProyectoMetrica> seleccionadas = pmRepo.findByIdProyectoId(proyectoId);
        for (ProyectoMetrica pm : seleccionadas) {
            if (!pm.getAprobada()) {
                UUID metricaId = pm.getId().getMetricaId();
                // Verificar si existe parametrización (sin importar el status)
                boolean tieneParam = parametrizacionRepo.existsByMetricaIdAndProyectoId(metricaId, proyectoId);
                if (tieneParam) {
                    pm.setAprobada(true);
                    pm.setAprobadaPor(aprobadaPor);
                    pm.setAprobadaAt(Instant.now());
                    pmRepo.save(pm);
                    
                    // Generar variable si no existe
                    if (!variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)) {
                        generarVariable(proyectoId, pm.getMetrica());
                    } else {
                        // Reactivar si existe pero está inactiva
                        variableRepo.findByProyectoIdAndMetrica_Id(proyectoId, metricaId)
                                .filter(v -> !v.getActiva())
                                .ifPresent(v -> { v.setActiva(true); variableRepo.save(v); });
                    }
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private VariableDto generarVariable(UUID proyectoId, Metrica m) {
        String categoria = m.getCategoria().getNombre().toLowerCase();
        String alcance   = categoria.contains("socio") ? "individual" : "grupal";
        String tipoDato  = categoria.contains("socio") ? "escala"     : "numerico";
        
        // Mapear categoría a tipo_indicador válido según CHECK constraint
        String tipoIndicador;
        switch (categoria) {
            case "significado" -> tipoIndicador = "cumplimiento";
            case "impacto" -> tipoIndicador = "productividad";
            case "flexibilidad" -> tipoIndicador = "flexibilidad";
            case "socio-humano fsh" -> tipoIndicador = "sociohumano";
            default -> tipoIndicador = "calidad";
        }

        Variable v = new Variable();
        v.setProyectoId(proyectoId);
        v.setMetrica(m);
        v.setNombre(m.getNombre());
        v.setDescripcion(m.getDescripcion());
        v.setTipoIndicador(tipoIndicador);
        v.setTipoAlcance(alcance);
        v.setFrecuencia("por_sprint");
        v.setCardinalidad("unico");
        v.setTipoDato(tipoDato);
        if ("escala".equals(tipoDato)) {
            v.setEscalaMin(java.math.BigDecimal.ONE);
            v.setEscalaMax(java.math.BigDecimal.valueOf(5));
        }
        return toVariableDto(variableRepo.save(v));
    }

    private VariableDto toVariableDto(Variable v) {
        // Buscar parametrización asociada para obtener objetivo, procedimiento y escala
        String objetivo = null;
        String procedimiento = null;
        String escalaDefinicion = null;
        
        // Buscar la parametrización más reciente para esta métrica y proyecto
        var parametrizacion = parametrizacionRepo.findTopByMetricaIdOrderByCreatedAtDesc(v.getMetrica().getId())
                .filter(p -> p.getProyectoId() != null && p.getProyectoId().equals(v.getProyectoId()))
                .or(() -> parametrizacionRepo.findTopByMetricaIdOrderByCreatedAtDesc(v.getMetrica().getId()));
        
        if (parametrizacion.isPresent()) {
            objetivo = parametrizacion.get().getObjetivo();
            procedimiento = parametrizacion.get().getProcedimiento();
            escalaDefinicion = parametrizacion.get().getEscala();
        }
        
        return new VariableDto(
                v.getId(), v.getProyectoId(),
                v.getMetrica().getId(),
                v.getMetrica().getNombre(),
                v.getMetrica().getCategoria().getNombre(),
                v.getNombre(), v.getDescripcion(),
                v.getTipoIndicador(),
                v.getTipoAlcance(), v.getFrecuencia(),
                v.getCardinalidad(), v.getTipoDato(),
                v.getEscalaMin(), v.getEscalaMax(),
                v.getActiva(), v.getCreatedAt(),
                v.getFormulaTexto(), v.getFormulaJson(), v.getFrecuenciaCaptura(),
                objetivo, procedimiento, escalaDefinicion);
    }
}
