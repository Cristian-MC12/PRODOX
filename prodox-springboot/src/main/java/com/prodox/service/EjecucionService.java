// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.RegistrarValorRequest;
import com.prodox.dto.RegistroValorDto;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.RegistroValor;
import com.prodox.entity.Sprint;
import com.prodox.entity.Variable;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.RegistroValorRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EjecucionService {

    private final RegistroValorRepository registroRepo;
    private final VariableRepository      variableRepo;
    private final SprintRepository        sprintRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final CalculoMetricaService   calculoMetricaService;

    /**
     * Revisión de seguridad: ninguno de estos endpoints validaba pertenencia al
     * proyecto (IDOR confirmado en auditoría del módulo Ejecución) ni que
     * sprintId/variableId fueran consistentes entre sí — un sprint del
     * Proyecto A podía combinarse con una variable del Proyecto B, y
     * cualquier usuario autenticado (miembro o no) podía leer/escribir
     * registro_valores de cualquier proyecto con solo conocer los IDs.
     * validarAcceso/validarScrumMaster y validarMismoProyecto son expuestos
     * como públicos porque VariableDinamicaService (el camino real que usa
     * la pantalla de Ejecución) ya depende de este servicio y los reutiliza
     * en vez de duplicar el criterio de autorización.
     */
    public List<RegistroValorDto> listarPorSprint(String userId, UUID sprintId) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        validarAcceso(userId, sprint.getProyectoId());
        return registroRepo.findBySprintId(sprintId)
                .stream().map(this::toDto).toList();
    }

    public List<RegistroValorDto> listarPorVariable(String userId, UUID variableId, UUID sprintId) {
        Variable variable = variableRepo.findById(variableId)
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        validarMismoProyecto(sprint, variable);
        validarAcceso(userId, sprint.getProyectoId());
        return registroRepo.findByVariable_IdAndSprintId(variableId, sprintId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public RegistroValorDto registrar(String userId, RegistrarValorRequest req) {
        Variable v = variableRepo.findById(req.variableId())
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada."));
        Sprint sprint = sprintRepo.findById(req.sprintId())
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        validarMismoProyecto(sprint, v);
        // Revisión de captura individual: cada miembro del proyecto puede
        // registrar su propio dato cuando la variable es 'individual'; las
        // variables 'grupal' conservan la restricción original (solo el
        // Scrum Master registra el valor colectivo del equipo).
        validarPuedeRegistrar(userId, v);

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

    /** Ambos deben pertenecer al mismo proyecto — no basta con que cada ID exista individualmente. */
    public void validarMismoProyecto(Sprint sprint, Variable variable) {
        if (!sprint.getProyectoId().equals(variable.getProyectoId())) {
            throw new IllegalArgumentException("El sprint y la variable no pertenecen al mismo proyecto.");
        }
    }

    /** Mismo patrón de autorización que AnalyticsController/SprintController: solo membresía. */
    public void validarAcceso(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    /** Mismo patrón que AIInsightsService.validateScrumMasterAccess: membresía + rol de líder del proyecto. */
    public void validarScrumMaster(String userId, UUID proyectoId) {
        ProjectMember member = projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId)
                .orElseThrow(() -> new SecurityException("No tienes acceso a este proyecto"));
        if (!"scrum_master".equals(member.getRol())) {
            throw new SecurityException("Solo el Scrum Master del proyecto puede registrar valores");
        }
    }

    /**
     * Revisión de autorización condicional por parametrización: quién puede
     * registrar un valor depende del alcance/responsable definido en la
     * parametrización aprobada de la métrica, reflejado en
     * Variable.tipoAlcance (fuente de verdad ya existente — no se inventa un
     * campo nuevo):
     * - 'individual' (alcance "EQUIPO" en la parametrización): cada
     *   integrante del proyecto —Scrum Member o Scrum Master por igual,
     *   porque el Scrum Master también es parte del equipo— registra SU
     *   PROPIO valor. Basta con membresía (mismo criterio que validarAcceso).
     * - cualquier otro tipoAlcance (hoy solo 'grupal', alcance "SCRUM MASTER"
     *   en la parametrización): únicamente el Scrum Master del proyecto
     *   puede registrar el valor — un Scrum Member se rechaza aunque llame
     *   directamente al endpoint.
     *
     * Esto es intencional y NO es lo mismo que decidir CÓMO se calcula el
     * resultado (eso lo decide tipoOperacion/fórmula/agregación en
     * CalculoMetricaService, sin relación con este método). El userId con el
     * que se guarda el registro sigue siendo SIEMPRE el del JWT autenticado
     * (nunca uno enviado por el cliente).
     */
    public void validarPuedeRegistrar(String userId, Variable variable) {
        if ("individual".equals(variable.getTipoAlcance())) {
            validarAcceso(userId, variable.getProyectoId());
        } else {
            validarScrumMaster(userId, variable.getProyectoId());
        }
    }

    /**
     * FASE 16.11: único punto de escritura de registro_valores para todo el
     * sistema (antes había tres caminos independientes — este servicio, el
     * upsert embebido en MetricaAcademicaService.ejecutarMetricaAcademica(),
     * y VariableDinamicaService.guardarValores() — cada uno con su propio
     * criterio de inserción/actualización, lo que permitía duplicados como
     * el encontrado en producción para "Cambios de alcance por sprint").
     *
     * Revisión de captura universal: la clave de "registro vigente" para
     * CUALQUIER variable —sin importar su tipoAlcance— incluye SIEMPRE al
     * usuario (findFirstBySprintIdAndVariable_IdAndUserId...): cada miembro
     * tiene su propio registro vigente por variable+sprint, nunca comparte
     * fila con otro miembro. Antes, las variables con tipoAlcance distinto de
     * 'individual' (hoy solo 'grupal') usaban una clave sin usuario — un
     * único valor vigente "compartido" — lo que hacía que el registro de un
     * segundo miembro SOBRESCRIBIERA el del primero en vez de coexistir. Esa
     * era precisamente la causa por la que solo tenía sentido permitir que el
     * Scrum Master capturara esas variables: al abrir la captura a cualquier
     * miembro sin corregir esta clave, se habría perdido el dato de quien
     * hubiera registrado antes.
     *
     * Si ya existe un registro vigente de ESTE usuario se actualiza (UPDATE);
     * si no, se crea uno nuevo (INSERT). Nunca se borra ni se toca el
     * registro de otro usuario: si varios miembros registran su propio valor
     * para la misma variable+sprint, cada uno conserva su propia fila — es
     * precisamente esa lista de registros por-miembro la que
     * CalculoMetricaService combina según la operación/fórmula aprobada para
     * la métrica (ver CalculoMetricaService.resolverValorPorVariable()).
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

        Optional<RegistroValor> vigente = registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), userId);

        RegistroValor r = vigente.orElseGet(RegistroValor::new);
        r.setVariable(variable);
        r.setSprintId(sprintId);
        r.setUserId(userId);
        r.setValorNum(valorNum);
        r.setValorTexto(valorTexto);
        r.setValorBool(valorBool);
        r.setObservacion(observacion);
        r.setRegistradoAt(Instant.now());

        RegistroValor guardado = registroRepo.save(r);
        recalcularMetricaAsociada(variable, sprintId, userId);
        return guardado;
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
            // Revisión de captura universal: un usuario NUNCA puede editar el
            // registro de otro, sin importar el tipoAlcance de la variable —
            // antes esta comprobación se saltaba para variables no
            // individuales (hoy solo 'grupal'), lo que habría permitido que
            // cualquier miembro sobrescribiera el registro de otro apenas se
            // le permitiera capturar esas variables.
            boolean perteneceAEstaCombinacion =
                    sprintId.equals(registroAEditar.getSprintId())
                    && variable.getId().equals(registroAEditar.getVariable().getId())
                    && userId.equals(registroAEditar.getUserId());
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
            // Siempre scopeado por usuario: cada miembro tiene su propia fila
            // para esta variable+sprint+fecha, nunca comparte una con otro
            // miembro (ver guardarOActualizarValor() de 7 argumentos, arriba).
            Optional<RegistroValor> existente = registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdAndRegistradoAt(
                    sprintId, variable.getId(), userId, fechaCaptura);
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

        RegistroValor guardado = registroRepo.save(r);
        recalcularMetricaAsociada(variable, sprintId, userId);
        return guardado;
    }

    /**
     * Dispara el recálculo automático del resultado de la métrica dueña de esta
     * variable para el sprint recién capturado (ver
     * CalculoMetricaService.recalcularSilenciosamente): así Evaluación puede
     * mostrar el resultado calculado del equipo sin depender exclusivamente del
     * botón manual "Calcular".
     *
     * Corrección del bug de visibilidad transaccional (métricas EQUIPO con 2+
     * capturas, ej. SUMA de A=22+B=12+C=25 terminaba en 34, no 59): antes este
     * método llamaba a calculoMetricaService.recalcularSilenciosamente() de
     * forma inmediata, todavía DENTRO de la transacción de guardarOActualizarValor()
     * (que a su vez sigue abierta dentro de la de VariableDinamicaService.
     * guardarValores()). Como recalcularSilenciosamente() está anotado
     * @Transactional(REQUIRES_NEW), Spring suspendía esa transacción externa
     * —todavía sin COMMIT— y abría una transacción nueva e independiente para
     * el recálculo. Bajo READ COMMITTED (Postgres), esa transacción nueva podía
     * ver todo lo ya confirmado de peticiones anteriores, pero NUNCA el propio
     * registro que la transacción externa (la de ESTA misma captura) todavía no
     * había confirmado — así que cada recálculo automático excluía sistemáticamente
     * el valor de quien lo acababa de disparar, sin importar su rol.
     *
     * La corrección NO toca REQUIRES_NEW (sigue siendo necesario para que un
     * fallo del recálculo nunca revierta la captura) ni ninguna fórmula/
     * agregación: solo difiere CUÁNDO se dispara. Si hay una transacción activa
     * (el caso real siempre, vía @Transactional de guardarOActualizarValor()),
     * se registra un TransactionSynchronization cuyo afterCommit() dispara el
     * recálculo — es decir, después de que la fila recién guardada ya sea
     * visible para cualquier otra transacción, incluida la REQUIRES_NEW del
     * propio recálculo. afterCommit() se ejecuta de forma síncrona en el mismo
     * hilo, como parte del propio proceso de commit de Spring, así que el
     * llamador (guardarOActualizarValor/guardarValores) no retorna hasta que el
     * recálculo ya terminó — sin cambios de comportamiento observable para quien
     * llama, solo se corrige qué datos ve el recálculo. Se prefiere este
     * mecanismo sobre @TransactionalEventListener(AFTER_COMMIT) por ser más
     * acotado: no requiere introducir un ApplicationEvent ni un listener nuevo,
     * mantiene el disparo confinado exactamente a esta clase (la misma que ya
     * lo hacía), y preserva intacta la firma/API de recalcularSilenciosamente().
     * Si no hay transacción activa (ej. una llamada directa fuera de un
     * @Transactional real, como en pruebas unitarias sin contexto Spring), se
     * conserva el comportamiento previo exacto: disparo inmediato.
     *
     * Nunca falla ni revierte la captura del valor: ahora es imposible que lo
     * haga, porque para cuando el recálculo corre, la transacción de la
     * captura ya hizo COMMIT.
     */
    private void recalcularMetricaAsociada(Variable variable, UUID sprintId, String userId) {
        try {
            UUID metricaId = variable.getMetrica().getId();
            UUID proyectoId = variable.getProyectoId();

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispararRecalculoSilencioso(metricaId, proyectoId, sprintId, userId);
                    }
                });
            } else {
                dispararRecalculoSilencioso(metricaId, proyectoId, sprintId, userId);
            }
        } catch (Exception e) {
            // Mismo contrato previo: cualquier fallo al preparar el recálculo
            // (incluida una Variable de prueba sin Metrica asociada) nunca debe
            // afectar la captura ya guardada.
            log.debug("No se pudo preparar el recálculo automático tras la captura: {}", e.getMessage());
        }
    }

    private void dispararRecalculoSilencioso(UUID metricaId, UUID proyectoId, UUID sprintId, String userId) {
        try {
            calculoMetricaService.recalcularSilenciosamente(metricaId, proyectoId, sprintId, userId);
        } catch (Exception e) {
            log.debug("No se pudo disparar el recálculo automático tras la captura: {}", e.getMessage());
        }
    }

    /** Tolerancia para comparar restos de división decimal (error de representación en punto flotante/BigDecimal). */
    private static final BigDecimal EPSILON_PASO = new BigDecimal("0.0000001");

    /**
     * Rechaza un valor numérico que no respete la escala estructurada de la
     * variable (escalaMin/escalaMax/escalaPaso/escalaTipo — corrección del manejo
     * de escalas, copiados desde MetricParametrizacion al aprobar, ver
     * ParametrizacionService/VariableDinamicaService.crearVariablesDesdeParametrizacion()).
     * Aplica a ambos caminos de escritura por igual (las dos sobrecargas de
     * guardarOActualizarValor() llaman a este método): nunca debe poder
     * persistirse un valor que viole la escala, sea cual sea el camino usado.
     * Variables sin escala estructurada (escalaMin/Max/Paso/Tipo todos null,
     * parametrización histórica — ver migración V32) no se restringen: el
     * backend nunca inventa un límite que la parametrización no definió.
     */
    private void validarRangoValor(Variable variable, BigDecimal valorNum) {
        if (valorNum == null) return;

        BigDecimal min  = variable.getEscalaMin();
        BigDecimal max  = variable.getEscalaMax();
        BigDecimal paso = variable.getEscalaPaso();
        String     tipo = variable.getEscalaTipo();

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
        if ("NUMERICA_ENTERA".equals(tipo) && valorNum.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "El valor (" + valorNum + ") debe ser un número entero para '" +
                    variable.getNombre() + "'.");
        }
        if (paso != null && paso.compareTo(BigDecimal.ZERO) > 0) {
            // Punto de referencia para el paso: escalaMin si está definido (caso
            // habitual — y ya validado arriba que valorNum >= min, así que el resto
            // siempre cae en [0, paso)), o 0 en el caso teórico de un paso definido
            // sin mínimo.
            BigDecimal referencia = min != null ? min : BigDecimal.ZERO;
            BigDecimal resto = valorNum.subtract(referencia).remainder(paso).abs();
            if (resto.compareTo(EPSILON_PASO) > 0) {
                throw new IllegalArgumentException(
                        "El valor (" + valorNum + ") no respeta el paso permitido (" + paso +
                        ") para '" + variable.getNombre() + "'.");
            }
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
     *
     * Revisión de captura universal: la ventana de frecuencia se evalúa
     * SIEMPRE contra los registros del propio usuario, sin importar el
     * tipoAlcance de la variable — antes, las variables no individuales (hoy
     * solo 'grupal') comparaban contra los registros de TODOS los usuarios,
     * lo que habría impedido que un segundo miembro registrara su propio
     * valor "por_sprint" solo porque otro miembro ya había registrado el
     * suyo.
     */
    private void validarFrecuencia(Variable variable, UUID sprintId, String userId, Instant fechaCaptura, LocalDate dia, UUID registroId) {
        String frecuencia = variable.getFrecuenciaCaptura();
        if (frecuencia == null || "ilimitada".equals(frecuencia)) {
            return;
        }

        List<RegistroValor> existentes = registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId())
                .stream().filter(r -> userId.equals(r.getUserId())).toList();

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
