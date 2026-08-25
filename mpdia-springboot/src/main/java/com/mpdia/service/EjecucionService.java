// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.RegistrarValorRequest;
import com.mpdia.dto.RegistroValorDto;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EjecucionService {

    private final RegistroValorRepository registroRepo;
    private final VariableRepository      variableRepo;
    private final SprintRepository        sprintRepo;

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

        validarRangoValor(variable, valorNum);

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

    /**
     * FASE 16 — sobrecarga aditiva: permite especificar explícitamente la fecha
     * de captura (registradoAt) en vez de forzar siempre Instant.now(). El
     * método original de arriba NO se modifica ni cambia su comportamiento —
     * esta sobrecarga delega en él cuando fechaCaptura es null.
     *
     * Cuando fechaCaptura viene informada, la clave de "registro vigente" pasa
     * a incluir la fecha exacta: misma variable+sprint+fecha(+userId si
     * individual) actualiza esa fila; una fecha distinta crea una fila nueva.
     * Esto permite que coexistan varias capturas de la misma variable dentro
     * del mismo sprint (una por fecha), en vez de la única fila "vigente" que
     * asumía el comportamiento previo.
     */
    @Transactional
    public RegistroValor guardarOActualizarValor(
            Variable variable,
            UUID sprintId,
            String userId,
            BigDecimal valorNum,
            String valorTexto,
            Boolean valorBool,
            String observacion,
            Instant fechaCaptura) {
        return guardarOActualizarValor(
                variable, sprintId, userId, valorNum, valorTexto, valorBool, observacion,
                fechaCaptura, null);
    }

    /**
     * Revisión de Ejecución — corrige el bug reportado al editar una captura
     * 'por_sprint': el camino de arriba (sin registroId) decidía si una
     * escritura era "edición" únicamente comparando la fecha nueva contra las
     * fechas ya persistidas — si el usuario cambiaba la fecha al editar, esa
     * comparación fallaba, el backend la trataba como una captura nueva en
     * conflicto y la rechazaba ("Editá ese valor en vez de crear una captura
     * nueva", sobre EL MISMO registro que se estaba editando).
     *
     * Esta sobrecarga aditiva recibe explícitamente el UUID del RegistroValor
     * que se está editando (null si es una captura nueva, nunca una edición):
     * - registroId != null: EDICIÓN. Se localiza esa fila por ID (nunca por
     *   fecha) y se actualiza SIEMPRE esa misma fila, sin importar si la
     *   fecha cambió. La comprobación de duplicados excluye ese registro —
     *   cualquier otro registro que siga en conflicto es SIEMPRE una fila
     *   distinta, nunca la que se edita.
     * - registroId == null: CREACIÓN. Comportamiento IDÉNTICO al existente
     *   (sin ningún cambio) — reenviar la misma fecha exacta ya en uso sigue
     *   actualizando esa fila (upsert por fecha, preexistente); cualquier
     *   otro conflicto de frecuencia se rechaza igual que antes.
     */
    @Transactional
    public RegistroValor guardarOActualizarValor(
            Variable variable,
            UUID sprintId,
            String userId,
            BigDecimal valorNum,
            String valorTexto,
            Boolean valorBool,
            String observacion,
            Instant fechaCaptura,
            UUID registroId) {

        if (fechaCaptura == null) {
            return guardarOActualizarValor(
                    variable, sprintId, userId, valorNum, valorTexto, valorBool, observacion);
        }

        validarRangoValor(variable, valorNum);

        RegistroValor registroAEditar = null;
        if (registroId != null) {
            registroAEditar = registroRepo.findById(registroId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El registro que intentás editar ya no existe."));
            boolean perteneceAEstaCombinacion =
                    sprintId.equals(registroAEditar.getSprintId())
                    && variable.getId().equals(registroAEditar.getVariable().getId())
                    && (!"individual".equals(variable.getTipoAlcance())
                            || userId.equals(registroAEditar.getUserId()));
            if (!perteneceAEstaCombinacion) {
                throw new IllegalArgumentException(
                        "El registro que intentás editar no corresponde a esta variable/sprint.");
            }
        }

        validarCapturaConFecha(variable, sprintId, userId, fechaCaptura, registroId);

        RegistroValor r;
        if (registroAEditar != null) {
            r = registroAEditar;
        } else {
            Optional<RegistroValor> existente = "individual".equals(variable.getTipoAlcance())
                    ? registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdAndRegistradoAt(
                            sprintId, variable.getId(), userId, fechaCaptura)
                    : registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(
                            sprintId, variable.getId(), fechaCaptura);
            r = existente.orElseGet(RegistroValor::new);
        }

        r.setVariable(variable);
        r.setSprintId(sprintId);
        r.setUserId(userId);
        r.setValorNum(valorNum);
        r.setValorTexto(valorTexto);
        r.setValorBool(valorBool);
        r.setObservacion(observacion);
        r.setRegistradoAt(fechaCaptura);

        return registroRepo.save(r);
    }

    /**
     * Rechaza un valor numérico fuera del rango [escalaMin, escalaMax] de la
     * variable, cuando esos límites están definidos (derivados de la escala
     * de la parametrización — ver VariableDinamicaService.parseEscala()).
     * Aplica a ambos caminos de escritura por igual: nunca debe poder
     * persistirse un valor fuera de rango, sea cual sea el camino usado.
     * Variables sin escala definida (escalaMin/Max null) no se restringen.
     */
    private void validarRangoValor(Variable variable, BigDecimal valorNum) {
        if (valorNum == null) return;

        java.math.BigDecimal min = variable.getEscalaMin();
        java.math.BigDecimal max = variable.getEscalaMax();

        if (min != null && valorNum.compareTo(min) < 0) {
            throw new IllegalArgumentException(
                    "El valor (" + valorNum + ") es menor al mínimo permitido (" + min +
                    ") para '" + variable.getNombre() + "'.");
        }
        if (max != null && valorNum.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "El valor (" + valorNum + ") es mayor al máximo permitido (" + max +
                    ") para '" + variable.getNombre() + "'.");
        }
    }

    /**
     * Validaciones exclusivas del camino con fechaCaptura explícita (el que
     * usa la pantalla de Ejecución vía VariableDinamicaService): el camino de
     * 7 argumentos siempre captura "ahora" y siempre actualiza una única fila
     * vigente por sprint+variable(+usuario), por lo que nunca puede violar
     * rango de fechas ni frecuencia — solo este camino puede crear filas con
     * fechas distintas dentro del mismo sprint, que es donde ambas reglas
     * aplican.
     */
    private void validarCapturaConFecha(Variable variable, UUID sprintId, String userId, Instant fechaCaptura, UUID registroId) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));

        LocalDate dia = fechaCaptura.atZone(ZoneOffset.UTC).toLocalDate();
        if (dia.isBefore(sprint.getFechaInicio())) {
            throw new IllegalArgumentException(
                    "La fecha de captura (" + dia + ") es anterior al inicio del sprint (" +
                    sprint.getFechaInicio() + ").");
        }
        if (sprint.getFechaFin() != null && dia.isAfter(sprint.getFechaFin())) {
            throw new IllegalArgumentException(
                    "La fecha de captura (" + dia + ") es posterior al fin del sprint (" +
                    sprint.getFechaFin() + ").");
        }

        validarFrecuencia(variable, sprintId, userId, fechaCaptura, dia, registroId);
    }

    /**
     * Impide crear una captura nueva cuando la frecuencia de la variable no
     * lo permite: 'diaria' admite una por día, 'semanal' una por semana ISO,
     * 'por_sprint' una sola para todo el sprint. 'ilimitada' no restringe.
     *
     * registroId distingue creación de edición (ver el overload de 9
     * argumentos de guardarOActualizarValor()):
     * - registroId == null (creación): comportamiento preexistente, sin
     *   cambios — reenviar la MISMA fecha exacta ya en uso nunca cuenta como
     *   conflicto (esa escritura actualiza esa fila vía upsert por fecha).
     * - registroId != null (edición): ese registro se excluye del chequeo de
     *   duplicados sea cual sea su fecha — es la fila que se está
     *   actualizando, nunca "otro" registro. Cualquier fila restante que
     *   siga en conflicto es SIEMPRE un registro distinto y se rechaza, sin
     *   el atajo de "misma fecha" (que aquí ya no aplica: la fila se
     *   localiza y actualiza por ID, no por fecha).
     */
    private void validarFrecuencia(Variable variable, UUID sprintId, String userId, Instant fechaCaptura, LocalDate dia, UUID registroId) {
        String frecuencia = variable.getFrecuenciaCaptura();
        if (frecuencia == null || "ilimitada".equals(frecuencia)) {
            return;
        }

        List<RegistroValor> existentes = registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId());
        if ("individual".equals(variable.getTipoAlcance())) {
            existentes = existentes.stream().filter(r -> userId.equals(r.getUserId())).toList();
        }

        if (registroId != null) {
            existentes = existentes.stream().filter(r -> !registroId.equals(r.getId())).toList();
        } else {
            // Si ya existe una fila con esta fecha exacta, esta escritura la
            // ACTUALIZA (no crea una nueva) — es siempre una edición válida, sin
            // importar si además hay otras filas de fechas distintas por drift
            // histórico previo a esta validación (esas no se tocan ni se borran).
            boolean actualizaFilaExistente = existentes.stream().anyMatch(r -> r.getRegistradoAt().equals(fechaCaptura));
            if (actualizaFilaExistente) {
                return;
            }
        }

        for (RegistroValor r : existentes) {
            LocalDate diaExistente = r.getRegistradoAt().atZone(ZoneOffset.UTC).toLocalDate();
            boolean mismaVentana = switch (frecuencia) {
                case "diaria" -> diaExistente.equals(dia);
                case "semanal" -> mismaSemanaIso(diaExistente, dia);
                case "por_sprint" -> true;
                default -> false;
            };

            if (mismaVentana) {
                throw new IllegalArgumentException(
                        "Ya existe un valor registrado para '" + variable.getNombre() + "' " +
                        describirVentana(frecuencia) + " (fecha " + diaExistente +
                        "). Editá ese valor en vez de crear una captura nueva.");
            }
        }
    }

    private boolean mismaSemanaIso(LocalDate a, LocalDate b) {
        return a.get(WeekFields.ISO.weekBasedYear()) == b.get(WeekFields.ISO.weekBasedYear())
                && a.get(WeekFields.ISO.weekOfWeekBasedYear()) == b.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    private String describirVentana(String frecuencia) {
        return switch (frecuencia) {
            case "diaria" -> "hoy";
            case "semanal" -> "esta semana";
            case "por_sprint" -> "en este sprint";
            default -> "en este período";
        };
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
