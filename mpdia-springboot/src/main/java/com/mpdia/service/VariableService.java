// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.CrearVariableRequest;
import com.mpdia.dto.VariableDto;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.Variable;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VariableService {

    private final VariableRepository variableRepo;
    private final MetricaRepository  metricaRepo;

    public List<VariableDto> listar(UUID proyectoId) {
        return variableRepo.findByProyectoIdAndActivaTrue(proyectoId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public VariableDto crear(UUID proyectoId, CrearVariableRequest req) {
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
    public void desactivar(UUID variableId) {
        Variable v = variableRepo.findById(variableId)
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));
        v.setActiva(false);
        variableRepo.save(v);
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
                v.getActiva(), v.getCreatedAt());
    }
}
