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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

        // Registros ilimitados: la frecuencia de captura es solo informativa,
        // no restringe la cantidad de registros por usuario/sprint.

        RegistroValor r = guardarOActualizarValor(
                v, req.sprintId(), userId,
                req.valorNum(), req.valorTexto(), req.valorBool(), req.observacion());

        return toDto(r);
    }

    /**
     * FASE 16.11: único punto de escritura de registro_valores para todo el
     * sistema (antes había tres caminos independientes — este servicio, el
     * upsert embebido en MetricaAcademicaService.ejecutarMetricaAcademica(),
     * y VariableDinamicaService.guardarValores() — cada uno con su propio
     * criterio de inserción/actualización, lo que permitía duplicados como
     * el encontrado en producción para "Cambios de alcance por sprint").
     *
     * Para una variable+sprint dados:
     * - variable 'individual': la clave de "registro vigente" incluye al
     *   usuario (findFirstBySprintIdAndVariable_IdAndUserId...), igual que
     *   ya asumía EjecucionComponent al filtrar el "último valor" por
     *   userId para variables individuales.
     * - cualquier otro tipoAlcance (hoy solo 'grupal'): la clave es
     *   variable+sprint sin usuario — un único valor vigente compartido,
     *   igual que ya asumía EjecucionComponent (no filtra por userId al
     *   calcular el "último valor" grupal) y que ya usaba, de hecho,
     *   MetricaAcademicaService antes de esta unificación.
     *
     * Si ya existe un registro vigente se actualiza (UPDATE); si no, se crea
     * uno nuevo (INSERT). Nunca se borra ni se toca ningún otro registro:
     * si para esa misma combinación ya había más de una fila por un
     * duplicado histórico previo, solo la más reciente pasa a ser la
     * vigente hacia adelante — las demás quedan intactas.
     */
    @Transactional
    public RegistroValor guardarOActualizarValor(
            Variable variable,
            UUID sprintId,
            String userId,
            BigDecimal valorNum,
            String valorTexto,
            Boolean valorBool,
            String observacion) {

        Optional<RegistroValor> vigente = "individual".equals(variable.getTipoAlcance())
                ? registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                        sprintId, variable.getId(), userId)
                : registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(
                        sprintId, variable.getId());

        RegistroValor r = vigente.orElseGet(RegistroValor::new);
        r.setVariable(variable);
        r.setSprintId(sprintId);
        r.setUserId(userId);
        r.setValorNum(valorNum);
        r.setValorTexto(valorTexto);
        r.setValorBool(valorBool);
        r.setObservacion(observacion);
        r.setRegistradoAt(Instant.now());

        return registroRepo.save(r);
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
