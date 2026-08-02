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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
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

        // Validar frecuencia de captura
        validarFrecuenciaCaptura(v, userId, req.sprintId());

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

    /**
     * Valida que el usuario pueda registrar un valor según la frecuencia de captura configurada:
     * - por_sprint: máximo 1 registro por usuario por sprint
     * - semanal: máximo 1 registro por usuario por semana ISO dentro del sprint
     * - diaria: máximo 1 registro por usuario por día
     * - ilimitada: sin restricción (puede registrar múltiples veces al día)
     */
    private void validarFrecuenciaCaptura(Variable v, String userId, UUID sprintId) {
        String frecuencia = v.getFrecuenciaCaptura() != null ? v.getFrecuenciaCaptura() : "por_sprint";

        // ilimitada → no hay restricción
        if ("ilimitada".equals(frecuencia)) return;

        List<RegistroValor> existentes = registroRepo.findByVariable_IdAndSprintId(v.getId(), sprintId)
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .toList();

        switch (frecuencia) {
            case "por_sprint" -> {
                if (!existentes.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ya registraste un valor para esta variable en este sprint. La frecuencia es: por sprint.");
                }
            }
            case "semanal" -> {
                LocalDate hoy = LocalDate.now();
                int semanaActual = hoy.get(WeekFields.of(Locale.getDefault()).weekOfYear());
                int anioActual = hoy.getYear();
                boolean yaRegistradoEstaSemana = existentes.stream().anyMatch(r -> {
                    LocalDate fechaRegistro = r.getRegistradoAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    return fechaRegistro.get(WeekFields.of(Locale.getDefault()).weekOfYear()) == semanaActual
                            && fechaRegistro.getYear() == anioActual;
                });
                if (yaRegistradoEstaSemana) {
                    throw new IllegalArgumentException(
                            "Ya registraste un valor esta semana. La frecuencia de captura es: semanal.");
                }
            }
            case "diaria" -> {
                LocalDate hoy = LocalDate.now();
                boolean yaRegistradoHoy = existentes.stream().anyMatch(r -> {
                    LocalDate fechaRegistro = r.getRegistradoAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    return fechaRegistro.equals(hoy);
                });
                if (yaRegistradoHoy) {
                    throw new IllegalArgumentException(
                            "Ya registraste un valor hoy. La frecuencia de captura es: diaria.");
                }
            }
            default -> {
                // Frecuencia desconocida, tratar como por_sprint
                if (!existentes.isEmpty()) {
                    throw new IllegalArgumentException("Ya registraste un valor para esta variable en este sprint.");
                }
            }
        }
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
