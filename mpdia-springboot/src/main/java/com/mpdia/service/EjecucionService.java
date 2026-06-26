// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.RegistrarValorRequest;
import com.mpdia.dto.RegistroValorDto;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EjecucionService {

    private final RegistroValorRepository registroRepo;
    private final VariableRepository      variableRepo;

    public List<RegistroValorDto> listarPorSprint(UUID sprintId) {
        return registroRepo.findBySprintId(sprintId)
                .stream().map(this::toDto).toList();
    }

    public List<RegistroValorDto> listarPorVariable(UUID variableId, UUID sprintId) {
        return registroRepo.findByVariable_IdAndSprintId(variableId, sprintId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public RegistroValorDto registrar(String userId, RegistrarValorRequest req) {
        Variable v = variableRepo.findById(req.variableId())
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));

        if (!v.getActiva()) {
            throw new IllegalArgumentException("La variable está inactiva.");
        }

        // Si cardinalidad=unico, solo permite un registro por usuario/sprint/variable
        if ("unico".equals(v.getCardinalidad())) {
            List<RegistroValor> existentes =
                    registroRepo.findByVariable_IdAndSprintId(req.variableId(), req.sprintId());
            boolean yaRegistrado = existentes.stream()
                    .anyMatch(r -> r.getUserId().equals(userId));
            if (yaRegistrado) {
                throw new IllegalArgumentException(
                        "Ya registraste un valor para esta variable en este sprint.");
            }
        }

        RegistroValor r = new RegistroValor();
        r.setVariable(v);
        r.setSprintId(req.sprintId());
        r.setUserId(userId);
        r.setValorNum(req.valorNum());
        r.setValorTexto(req.valorTexto());
        r.setValorBool(req.valorBool());
        r.setObservacion(req.observacion());

        return toDto(registroRepo.save(r));
    }

    private RegistroValorDto toDto(RegistroValor r) {
        return new RegistroValorDto(
                r.getId(),
                r.getVariable().getId(),
                r.getVariable().getNombre(),
                r.getSprintId(),
                r.getUserId(),
                r.getValorNum(),
                r.getValorTexto(),
                r.getValorBool(),
                r.getObservacion(),
                r.getRegistradoAt());
    }
}
