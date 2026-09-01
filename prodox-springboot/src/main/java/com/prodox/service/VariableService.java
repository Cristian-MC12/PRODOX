// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ActualizarFormulaRequest;
import com.prodox.dto.CrearVariableRequest;
import com.prodox.dto.VariableDto;
import com.prodox.entity.Metrica;
import com.prodox.entity.Variable;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Revisión de seguridad: ninguno de estos métodos validaba que el usuario
 * autenticado fuera miembro del proyecto — cualquier usuario podía listar,
 * crear, editar o desactivar variables de cualquier proyecto. Además,
 * desactivar()/actualizarFormula() recibían un proyectoId (vía el
 * controlador) que nunca se usaba para comprobar que la variable realmente
 * perteneciera a ese proyecto — una variable del Proyecto B podía
 * modificarse/desactivarse enviando la URL del Proyecto A.
 *
 * No se encontró en el código ninguna restricción de rol (Scrum Master) para
 * crear/editar/desactivar variables — Planeación (el flujo real que genera
 * variables vía aprobar()) tampoco la tiene ni en su controlador/servicio ni
 * en su UI (planeacion.component.ts no usa esScrumMaster en ningún lugar).
 * Por eso aquí se exige solo membresía para todas las operaciones — el mismo
 * criterio ya usado para las operaciones de solo lectura en Sprints/Analytics/
 * Ejecución — y no se inventa una restricción de Scrum Master sin evidencia.
 */
@Service
@RequiredArgsConstructor
public class VariableService {

    private final VariableRepository variableRepo;
    private final MetricaRepository  metricaRepo;
    private final ProjectMemberRepository projectMemberRepo;

    public List<VariableDto> listar(String userId, UUID proyectoId) {
        validarAcceso(userId, proyectoId);
        return variableRepo.findByProyectoIdAndActivaTrue(proyectoId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public VariableDto crear(String userId, UUID proyectoId, CrearVariableRequest req) {
        validarAcceso(userId, proyectoId);

        if (variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, req.metricaId())) {
            throw new IllegalArgumentException("Ya existe una variable para esta métrica en el proyecto.");
        }

        Metrica metrica = metricaRepo.findById(req.metricaId())
                .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada."));

        Variable v = new Variable();
        v.setProyectoId(proyectoId);
        v.setMetrica(metrica);
        v.setNombre(req.nombre());
        v.setDescripcion(req.descripcion());
        v.setTipoAlcance(req.tipoAlcance()   != null ? req.tipoAlcance()   : "grupal");
        v.setFrecuencia(req.frecuencia()     != null ? req.frecuencia()    : "por_sprint");
        v.setCardinalidad(req.cardinalidad() != null ? req.cardinalidad()  : "unico");
        v.setTipoDato(req.tipoDato()         != null ? req.tipoDato()      : "numerico");
        v.setEscalaMin(req.escalaMin());
        v.setEscalaMax(req.escalaMax());

        return toDto(variableRepo.save(v));
    }

    @Transactional
    public void desactivar(String userId, UUID proyectoId, UUID variableId) {
        Variable v = variableRepo.findById(variableId)
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));
        validarMismoProyecto(v, proyectoId);
        validarAcceso(userId, proyectoId);
        v.setActiva(false);
        variableRepo.save(v);
    }

    @Transactional
    public VariableDto actualizarFormula(String userId, UUID proyectoId, UUID variableId, ActualizarFormulaRequest req) {
        Variable v = variableRepo.findById(variableId)
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));
        validarMismoProyecto(v, proyectoId);
        validarAcceso(userId, proyectoId);
        v.setFormulaTexto(req.formulaTexto());
        v.setFormulaJson(req.formulaJson());
        if (req.frecuenciaCaptura() != null) {
            v.setFrecuenciaCaptura(req.frecuenciaCaptura());
        }
        return toDto(variableRepo.save(v));
    }

    /** La variable debe pertenecer realmente al proyecto sobre el que se está operando. */
    private void validarMismoProyecto(Variable v, UUID proyectoId) {
        if (!v.getProyectoId().equals(proyectoId)) {
            throw new IllegalArgumentException("La variable no pertenece a este proyecto.");
        }
    }

    /** Mismo patrón de autorización que SprintController/AnalyticsController: solo membresía. */
    private void validarAcceso(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    private VariableDto toDto(Variable v) {
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
                v.getFormulaTexto(),
                v.getFormulaJson(),
                v.getFrecuenciaCaptura(),
                null, null, null, // objetivo, procedimiento, escalaDefinicion (no disponibles en este contexto)
                v.getEscalaTipo(), v.getEscalaPaso(), v.getEscalaSinLimite());
    }
}
