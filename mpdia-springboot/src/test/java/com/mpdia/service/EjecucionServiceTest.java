// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.RegistrarValorRequest;
import com.mpdia.dto.RegistroValorDto;
import com.mpdia.entity.ProjectMember;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FASE 16.11: tests del único camino de escritura de registro_valores
 * (EjecucionService.guardarOActualizarValor()), que reemplaza los tres
 * caminos independientes previos: EjecucionService.registrar() (siempre
 * insertaba), VariableDinamicaService.guardarValores() (siempre insertaba)
 * y el upsert embebido en MetricaAcademicaService.ejecutarMetricaAcademica()
 * (upsert sin distinguir grupal/individual). El objetivo es eliminar el
 * duplicado real encontrado en producción (misma variable+sprint con dos
 * filas en registro_valores) sin tocar los datos históricos existentes.
 */
@ExtendWith(MockitoExtension.class)
class EjecucionServiceTest {

    @Mock private RegistroValorRepository registroRepo;
    @Mock private VariableRepository variableRepo;
    @Mock private SprintRepository sprintRepo;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private EjecucionService service;

    private UUID sprintId;
    private UUID variableId;
    private UUID proyectoId;

    @BeforeEach
    void setUp() {
        service = new EjecucionService(registroRepo, variableRepo, sprintRepo, projectMemberRepo);
        sprintId = UUID.randomUUID();
        variableId = UUID.randomUUID();
        proyectoId = UUID.randomUUID();
    }

    /** Sprint sintético que abarca todo agosto de 2026, usado por los tests de fechaCaptura explícita. */
    private Sprint sprintQueAbarcaAgosto2026() {
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setFechaInicio(LocalDate.of(2026, 8, 1));
        sprint.setFechaFin(LocalDate.of(2026, 8, 31));
        return sprint;
    }

    /** Sprint del proyecto de prueba, sin restricción de fechas (usado por los tests de registrar()). */
    private Sprint sprintDelProyecto() {
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        return sprint;
    }

    private ProjectMember scrumMaster() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId("user-a");
        m.setRol("scrum_master");
        return m;
    }

    private ProjectMember miembroNormal() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId("user-a");
        m.setRol("scrum_member");
        return m;
    }

    private Variable variableGrupal() {
        Variable v = new Variable();
        v.setId(variableId);
        v.setProyectoId(proyectoId);
        v.setNombre("variable_grupal_test");
        v.setTipoAlcance("grupal");
        v.setActiva(true);
        return v;
    }

    private Variable variableIndividual() {
        Variable v = new Variable();
        v.setId(variableId);
        v.setProyectoId(proyectoId);
        v.setNombre("variable_individual_test");
        v.setTipoAlcance("individual");
        v.setActiva(true);
        return v;
    }

    // ========================================
    // A. Primera captura → INSERT
    // ========================================
    @Test
    void guardarOActualizarValor_sinRegistroPrevio_creaUnoNuevo() {
        Variable variable = variableGrupal();
        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        service.guardarOActualizarValor(variable, sprintId, "user-a",
            new BigDecimal("5"), null, null, null);

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull(); // entidad nueva, todavía sin persistir
        assertThat(captor.getValue().getValorNum()).isEqualByComparingTo("5");
    }

    // ========================================
    // B. Segunda captura misma variable/sprint → UPDATE, no INSERT
    // C. el valor final queda actualizado
    // ========================================
    @Test
    void guardarOActualizarValor_conRegistroPrevio_actualizaElMismoRegistro() {
        Variable variable = variableGrupal();
        UUID idExistente = UUID.randomUUID();
        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setUserId("user-a");
        existente.setValorNum(new BigDecimal("5"));

        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.of(existente));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        service.guardarOActualizarValor(variable, sprintId, "user-a",
            new BigDecimal("9"), null, null, "cambiado");

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(idExistente); // MISMA fila (UPDATE)
        assertThat(captor.getValue().getValorNum()).isEqualByComparingTo("9");
        assertThat(captor.getValue().getObservacion()).isEqualTo("cambiado");
    }

    // ========================================
    // D. Dos llamadas seguidas nunca producen dos filas
    // ========================================
    @Test
    void guardarOActualizarValor_dosLlamadasSeguidas_nuncaProduceDosFilas() {
        Variable variable = variableGrupal();
        UUID idGenerado = UUID.randomUUID();

        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> {
            RegistroValor r = inv.getArgument(0);
            if (r.getId() == null) r.setId(idGenerado); // simula el ID asignado por la BD en el INSERT
            return r;
        });

        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.empty());
        RegistroValor primero = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("1"), null, null, null);

        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.of(primero));
        RegistroValor segundo = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("2"), null, null, null);

        assertThat(segundo.getId()).isEqualTo(primero.getId()); // misma fila, no una nueva
        verify(registroRepo, times(2)).save(any(RegistroValor.class));
    }

    // ========================================
    // Semántica grupal vs. individual (conservada del comportamiento previo
    // de EjecucionComponent: "último valor" grupal no filtra por usuario;
    // "último valor" individual sí)
    // ========================================
    @Test
    void guardarOActualizarValor_variableIndividual_usaClaveConUsuario() {
        Variable variable = variableIndividual();
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variableId, "user-a"))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(variable, sprintId, "user-a",
            new BigDecimal("3"), null, null, null);

        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(sprintId, variableId, "user-a");
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(any(), any());
    }

    @Test
    void guardarOActualizarValor_variableIndividual_dosUsuariosDistintosNoColisionan() {
        Variable variable = variableIndividual();
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                eq(sprintId), eq(variableId), anyString()))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(variable, sprintId, "user-a", new BigDecimal("1"), null, null, null);
        service.guardarOActualizarValor(variable, sprintId, "user-b", new BigDecimal("2"), null, null, null);

        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(sprintId, variableId, "user-a");
        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(sprintId, variableId, "user-b");
        verify(registroRepo, times(2)).save(any(RegistroValor.class));
    }

    @Test
    void guardarOActualizarValor_variableGrupal_usaClaveSinUsuario() {
        Variable variable = variableGrupal();
        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(variable, sprintId, "user-a",
            new BigDecimal("3"), null, null, null);

        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId);
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any());
    }

    // ========================================
    // registrar() — comportamiento preexistente conservado
    // ========================================
    @Test
    void registrar_variableNoEncontrada_lanzaExcepcion() {
        UUID id = UUID.randomUUID();
        when(variableRepo.findById(id)).thenReturn(Optional.empty());
        RegistrarValorRequest req = new RegistrarValorRequest(id, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no encontrada");
    }

    @Test
    void registrar_sprintNoEncontrado_lanzaExcepcion() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.empty());
        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sprint no encontrado");
    }

    @Test
    void registrar_variableInactiva_lanzaExcepcion() {
        Variable variable = variableGrupal();
        variable.setActiva(false);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(Optional.of(scrumMaster()));
        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactiva");
    }

    @Test
    void registrar_primeraCaptura_creaRegistroYRetornaDto() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(Optional.of(scrumMaster()));
        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrarValorRequest req = new RegistrarValorRequest(
            variableId, sprintId, new BigDecimal("7"), null, null, "obs");

        RegistroValorDto dto = service.registrar("user-a", req);

        assertThat(dto.variableId()).isEqualTo(variableId);
        assertThat(dto.valorNum()).isEqualByComparingTo("7");
        assertThat(dto.observacion()).isEqualTo("obs");
        verify(registroRepo, times(1)).save(any(RegistroValor.class));
    }

    @Test
    void registrar_segundaCaptura_actualizaEnVezDeInsertar() {
        Variable variable = variableGrupal();
        UUID idExistente = UUID.randomUUID();
        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("7"));

        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(Optional.of(scrumMaster()));
        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.of(existente));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrarValorRequest req = new RegistrarValorRequest(
            variableId, sprintId, new BigDecimal("11"), null, null, null);

        RegistroValorDto dto = service.registrar("user-a", req);

        assertThat(dto.id()).isEqualTo(idExistente);
        assertThat(dto.valorNum()).isEqualByComparingTo("11");
        verify(registroRepo, times(1)).save(any(RegistroValor.class));
    }

    // ========================================================================
    // Revisión de seguridad — autorización y consistencia sprint↔variable↔proyecto
    // ========================================================================

    @Test
    void registrar_sprintYVariableDeProyectosDistintos_seRechaza() {
        Variable variable = variableGrupal(); // proyectoId = proyectoId
        Sprint sprintDeOtroProyecto = new Sprint();
        sprintDeOtroProyecto.setId(sprintId);
        sprintDeOtroProyecto.setProyectoId(UUID.randomUUID()); // OTRO proyecto

        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDeOtroProyecto));

        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no pertenecen al mismo proyecto");

        verify(registroRepo, never()).save(any());
        verifyNoInteractions(projectMemberRepo); // nunca llega a evaluar autorización con datos inconsistentes
    }

    @Test
    void registrar_usuarioExternoAlProyecto_lanzaSecurityException403() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "user-externo")).thenReturn(Optional.empty());

        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-externo", req))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("No tienes acceso a este proyecto");

        verify(registroRepo, never()).save(any());
    }

    @Test
    void registrar_miembroNormalDelProyecto_lanzaSecurityException403() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(Optional.of(miembroNormal()));

        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Solo el Scrum Master");

        verify(registroRepo, never()).save(any());
    }

    @Test
    void listarPorSprint_usuarioExternoAlProyecto_lanzaSecurityException403() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "user-externo")).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorSprint("user-externo", sprintId))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("No tienes acceso a este proyecto");
    }

    @Test
    void listarPorSprint_miembroDelProyecto_permitido() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(true);
        when(registroRepo.findBySprintId(sprintId)).thenReturn(List.of());

        List<RegistroValorDto> resultado = service.listarPorSprint("user-a", sprintId);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarPorVariable_variableDeOtroProyectoQueElSprint_seRechaza() {
        Variable variable = variableGrupal(); // proyectoId = proyectoId
        Sprint sprintDeOtroProyecto = new Sprint();
        sprintDeOtroProyecto.setId(sprintId);
        sprintDeOtroProyecto.setProyectoId(UUID.randomUUID());

        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDeOtroProyecto));

        assertThatThrownBy(() -> service.listarPorVariable("user-a", variableId, sprintId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no pertenecen al mismo proyecto");

        verifyNoInteractions(projectMemberRepo);
    }

    @Test
    void listarPorVariable_usuarioDeOtroProyecto_lanzaSecurityException403() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDelProyecto()));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "user-b")).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorVariable("user-b", variableId, sprintId))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("No tienes acceso a este proyecto");
    }

    @Test
    void validarMismoProyecto_idsInexistentes_seRechazanAntesDeLlegarAAutorizacion() {
        UUID variableInexistente = UUID.randomUUID();
        when(variableRepo.findById(variableInexistente)).thenReturn(Optional.empty());

        RegistrarValorRequest req = new RegistrarValorRequest(variableInexistente, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(projectMemberRepo);
    }

    // ========================================================================
    // FASE 16 — sobrecarga aditiva con fechaCaptura explícita.
    // El método de 7 argumentos de arriba NO se modifica: todos sus tests
    // siguen intactos y pasando exactamente igual.
    // ========================================================================

    // A. fechaCaptura == null delega en el comportamiento existente (mismo
    // criterio de "vigente" por variable+sprint, sin comparar fecha).
    @Test
    void guardarOActualizarValor_conFechaNull_delegaEnElComportamientoExistente() {
        Variable variable = variableGrupal();
        when(registroRepo.findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("5"), null, null, null, null);

        assertThat(resultado.getValorNum()).isEqualByComparingTo("5");
        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, variableId);
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(any(), any(), any());
    }

    // B. Misma variable + sprint + MISMA fecha → actualiza el registro existente.
    @Test
    void guardarOActualizarValor_conFechaExplicita_mismaFecha_actualizaElMismoRegistro() {
        Variable variable = variableGrupal();
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");
        UUID idExistente = UUID.randomUUID();
        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("7"));
        existente.setRegistradoAt(fecha);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(existente));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.of(existente));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("9"), null, null, null, fecha);

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(idExistente); // MISMA fila
        assertThat(captor.getValue().getValorNum()).isEqualByComparingTo("9");
        assertThat(captor.getValue().getRegistradoAt()).isEqualTo(fecha);
    }

    // C. Frecuencia 'diaria' + fecha DIFERENTE (otro día) → crea un segundo
    // registro (nunca sobrescribe el de la otra fecha): dos días distintos son
    // dos ventanas de captura distintas bajo frecuencia diaria.
    @Test
    void guardarOActualizarValor_conFechaExplicita_frecuenciaDiaria_otroDia_creaUnSegundoRegistro() {
        Variable variable = variableGrupal();
        variable.setFrecuenciaCaptura("diaria");
        Instant fechaA = Instant.parse("2026-08-21T00:00:00Z");
        Instant fechaB = Instant.parse("2026-08-22T00:00:00Z");

        RegistroValor registroA = new RegistroValor();
        registroA.setVariable(variable);
        registroA.setSprintId(sprintId);
        registroA.setValorNum(new BigDecimal("7"));
        registroA.setRegistradoAt(fechaA);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(registroA));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fechaB))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("8"), null, null, null, fechaB);

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull(); // fila NUEVA, todavía sin persistir
        assertThat(captor.getValue().getRegistradoAt()).isEqualTo(fechaB);
        // Nunca se buscó/sobrescribió la fila de fechaA en esta llamada.
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fechaA);
    }

    // C2. Frecuencia 'por_sprint' (valor por defecto de Variable) + fecha
    // DIFERENTE dentro del mismo sprint → RECHAZADO: 'por_sprint' admite un
    // único valor para todo el sprint, sin importar la fecha exacta.
    @Test
    void guardarOActualizarValor_conFechaExplicita_frecuenciaPorSprint_otraFecha_rechazaLaCaptura() {
        Variable variable = variableGrupal(); // frecuenciaCaptura por defecto = "por_sprint"
        Instant fechaA = Instant.parse("2026-08-21T00:00:00Z");
        Instant fechaB = Instant.parse("2026-08-22T00:00:00Z");

        RegistroValor registroA = new RegistroValor();
        registroA.setVariable(variable);
        registroA.setSprintId(sprintId);
        registroA.setValorNum(new BigDecimal("7"));
        registroA.setRegistradoAt(fechaA);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(registroA));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("8"), null, null, null, fechaB))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ya existe un valor registrado");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    // C3. Frecuencia 'semanal' + fecha dentro de la MISMA semana ISO pero en
    // otro día → RECHAZADO. Fecha en la semana SIGUIENTE → permitido.
    @Test
    void guardarOActualizarValor_conFechaExplicita_frecuenciaSemanal_respetaVentanaDeSemanaIso() {
        Variable variable = variableGrupal();
        variable.setFrecuenciaCaptura("semanal");
        // 2026-08-17 es lunes (semana ISO 34); 2026-08-19 cae en la misma semana;
        // 2026-08-24 es lunes de la semana ISO siguiente (35).
        Instant fechaLunes = Instant.parse("2026-08-17T00:00:00Z");
        Instant fechaMismaSemana = Instant.parse("2026-08-19T00:00:00Z");
        Instant fechaSemanaSiguiente = Instant.parse("2026-08-24T00:00:00Z");

        RegistroValor registroExistente = new RegistroValor();
        registroExistente.setVariable(variable);
        registroExistente.setSprintId(sprintId);
        registroExistente.setValorNum(new BigDecimal("3"));
        registroExistente.setRegistradoAt(fechaLunes);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(registroExistente));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("4"), null, null, null, fechaMismaSemana))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("esta semana");

        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fechaSemanaSiguiente))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("5"), null, null, null, fechaSemanaSiguiente);

        verify(registroRepo, times(1)).save(any(RegistroValor.class));
    }

    // C3b. Aunque ya existan OTRAS filas de fechas distintas por drift
    // histórico previo a esta validación (ej. datos de antes de que la
    // frecuencia se empezara a exigir), editar la fila que SÍ coincide con la
    // fecha exacta enviada siempre debe permitirse — nunca debe bloquearse
    // una edición legítima solo porque además existan otras filas conflictivas
    // que esta escritura ni toca ni necesita resolver.
    @Test
    void guardarOActualizarValor_conFechaExplicita_editaFilaExistente_aunqueHayaOtrasFilasDeFechaDistinta() {
        Variable variable = variableGrupal(); // frecuenciaCaptura por defecto = "por_sprint"
        Instant fechaVieja = Instant.parse("2026-08-20T00:00:00Z");
        Instant fechaAEditar = Instant.parse("2026-08-23T00:00:00Z");

        RegistroValor filaVieja = new RegistroValor();
        filaVieja.setVariable(variable);
        filaVieja.setSprintId(sprintId);
        filaVieja.setValorNum(new BigDecimal("99"));
        filaVieja.setRegistradoAt(fechaVieja);

        RegistroValor filaAEditar = new RegistroValor();
        filaAEditar.setVariable(variable);
        filaAEditar.setSprintId(sprintId);
        filaAEditar.setValorNum(new BigDecimal("42"));
        filaAEditar.setRegistradoAt(fechaAEditar);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(filaVieja, filaAEditar));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fechaAEditar))
            .thenReturn(Optional.of(filaAEditar));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("50"), null, null, null, fechaAEditar);

        assertThat(resultado.getValorNum()).isEqualByComparingTo("50");
        assertThat(resultado.getRegistradoAt()).isEqualTo(fechaAEditar);
    }

    // C4. Fecha de captura fuera del rango del sprint (antes de fechaInicio o
    // después de fechaFin) → RECHAZADO, sin importar la frecuencia.
    @Test
    void guardarOActualizarValor_conFechaExplicita_fueraDelRangoDelSprint_rechazaLaCaptura() {
        Variable variable = variableGrupal();
        Instant fechaFueraDeRango = Instant.parse("2026-09-05T00:00:00Z"); // sprint termina el 31/08

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("1"), null, null, null, fechaFueraDeRango))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("posterior al fin del sprint");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    // D. Dos fechas distintas coexisten (ambas quedarían en el histórico —
    // verificado aquí a nivel de invocaciones de guardado, ya que el
    // histórico real lo construye EvaluacionService a partir de todo lo
    // persistido).
    @Test
    void guardarOActualizarValor_dosFechasDistintas_generanDosLlamadasDeGuardadoIndependientes() {
        Variable variable = variableGrupal();
        variable.setFrecuenciaCaptura("diaria"); // dos días distintos deben poder coexistir
        Instant fechaA = Instant.parse("2026-08-21T00:00:00Z");
        Instant fechaB = Instant.parse("2026-08-22T00:00:00Z");

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of()).thenAnswer(inv -> {
                RegistroValor rA = new RegistroValor();
                rA.setVariable(variable);
                rA.setSprintId(sprintId);
                rA.setRegistradoAt(fechaA);
                return List.of(rA);
            });
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(eq(sprintId), eq(variableId), any()))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor r1 = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("7"), null, null, null, fechaA);
        RegistroValor r2 = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("8"), null, null, null, fechaB);

        assertThat(r1.getRegistradoAt()).isEqualTo(fechaA);
        assertThat(r2.getRegistradoAt()).isEqualTo(fechaB);
        verify(registroRepo, times(2)).save(any(RegistroValor.class));
    }

    // E. Aislamiento: la búsqueda de "vigente por fecha" siempre incluye el
    // sprintId (que a su vez pertenece a un único proyecto) — una fecha
    // idéntica en OTRO sprint nunca colisiona con esta.
    @Test
    void guardarOActualizarValor_conFechaExplicita_siempreScopeadoPorSprintId() {
        Variable variable = variableGrupal();
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");
        UUID otroSprintId = UUID.randomUUID();

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("1"), null, null, null, fecha);

        verify(registroRepo, times(1))
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha);
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(otroSprintId, variableId, fecha);
    }

    // Variante individual: la clave por fecha también respeta userId.
    @Test
    void guardarOActualizarValor_conFechaExplicita_variableIndividual_usaClaveConUsuarioYFecha() {
        Variable variable = variableIndividual();
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdAndRegistradoAt(
                sprintId, variableId, "user-a", fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("3"), null, null, null, fecha);

        verify(registroRepo, times(1)).findFirstBySprintIdAndVariable_IdAndUserIdAndRegistradoAt(
            sprintId, variableId, "user-a", fecha);
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Validación de rango de valor (escalaMin/escalaMax) — revisión de
    // Ejecución. Un valor fuera del rango de la escala definida en la
    // parametrización (ej. 1-5) debe rechazarse en el backend, sin importar
    // si el frontend ya lo validó — nunca debe poder persistirse.
    // ══════════════════════════════════════════════════════════════════════

    private Variable variableConEscala(BigDecimal min, BigDecimal max) {
        Variable v = variableGrupal();
        v.setEscalaMin(min);
        v.setEscalaMax(max);
        return v;
    }

    @Test
    void guardarOActualizarValor_conFechaExplicita_valorFueraDeRangoPorArriba_rechazaLaCaptura() {
        Variable variable = variableConEscala(new BigDecimal("1"), new BigDecimal("5"));
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("7"), null, null, null, fecha))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mayor al máximo permitido (5)");

        verify(registroRepo, never()).save(any(RegistroValor.class));
        // La validación de rango ocurre ANTES que cualquier consulta de fecha/frecuencia.
        verify(sprintRepo, never()).findById(any());
    }

    @Test
    void guardarOActualizarValor_conFechaExplicita_valorFueraDeRangoPorAbajo_rechazaLaCaptura() {
        Variable variable = variableConEscala(new BigDecimal("1"), new BigDecimal("5"));
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("0"), null, null, null, fecha))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("menor al mínimo permitido (1)");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    @Test
    void guardarOActualizarValor_conFechaExplicita_valorDentroDelRango_seAceptaNormalmente() {
        Variable variable = variableConEscala(new BigDecimal("1"), new BigDecimal("5"));
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("3"), null, null, null, fecha);

        assertThat(resultado.getValorNum()).isEqualByComparingTo("3");
    }

    @Test
    void guardarOActualizarValor_conFechaExplicita_variableSinEscalaDefinida_noRestringeElValor() {
        Variable variable = variableGrupal(); // escalaMin/Max quedan null
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("999"), null, null, null, fecha);

        assertThat(resultado.getValorNum()).isEqualByComparingTo("999");
    }

    // El camino de 7 argumentos (sin fecha explícita) también valida el
    // rango — defensa en profundidad, aunque el frontend actual solo use el
    // camino de 8 argumentos.
    @Test
    void guardarOActualizarValor_sinFecha_valorFueraDeRango_tambienSeRechaza() {
        Variable variable = variableConEscala(new BigDecimal("1"), new BigDecimal("5"));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("10"), null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mayor al máximo permitido (5)");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Corrección del manejo de escalas — Ejecución debe ser la autoridad final
    // sobre tipo (entero/decimal), paso y "sin límite", no solo min/max.
    // La escala llega a Variable ya copiada por ParametrizacionService /
    // VariableDinamicaService (nunca inventada por Ejecución) — este bloque
    // solo prueba que, una vez ahí, se hace cumplir de verdad.
    // ══════════════════════════════════════════════════════════════════════

    private Variable variableConEscalaCompleta(String tipo, BigDecimal min, BigDecimal max,
                                                BigDecimal paso, Boolean sinLimite) {
        Variable v = variableGrupal();
        v.setEscalaTipo(tipo);
        v.setEscalaMin(min);
        v.setEscalaMax(max);
        v.setEscalaPaso(paso);
        v.setEscalaSinLimite(sinLimite);
        return v;
    }

    private RegistroValor aceptar(Variable variable, BigDecimal valor) {
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));
        return service.guardarOActualizarValor(variable, sprintId, "user-a", valor, null, null, null, fecha);
    }

    private org.assertj.core.api.ThrowableAssert.ThrowingCallable rechazar(Variable variable, BigDecimal valor) {
        Instant fecha = Instant.parse("2026-08-21T00:00:00Z");
        return () -> service.guardarOActualizarValor(variable, sprintId, "user-a", valor, null, null, null, fecha);
    }

    // 1. Escala 0-10 entero: 0, 5 y 10 se aceptan.
    @Test
    void escala0a10Entera_aceptaLimitesYValorIntermedio() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false);
        assertThat(aceptar(v, BigDecimal.ZERO).getValorNum()).isEqualByComparingTo("0");
        assertThat(aceptar(v, new BigDecimal("5")).getValorNum()).isEqualByComparingTo("5");
        assertThat(aceptar(v, BigDecimal.TEN).getValorNum()).isEqualByComparingTo("10");
    }

    // 2. Escala 0-100 entero.
    @Test
    void escala0a100Entera_aceptaValorDentroDelRango() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ONE, false);
        assertThat(aceptar(v, new BigDecimal("57")).getValorNum()).isEqualByComparingTo("57");
    }

    // 3. Escala 0-sin límite entero: acepta valores grandes.
    @Test
    void escala0aSinLimiteEntera_aceptaValoresGrandes() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true);
        assertThat(aceptar(v, new BigDecimal("100")).getValorNum()).isEqualByComparingTo("100");
        assertThat(aceptar(v, new BigDecimal("100000")).getValorNum()).isEqualByComparingTo("100000");
    }

    // 4. Escala decimal: 0-100 con paso 0.01 acepta un valor decimal válido.
    @Test
    void escalaDecimal_aceptaValorConDecimalesDentroDelPaso() {
        Variable v = variableConEscalaCompleta("NUMERICA_DECIMAL", BigDecimal.ZERO, new BigDecimal("100"),
            new BigDecimal("0.01"), false);
        assertThat(aceptar(v, new BigDecimal("57.34")).getValorNum()).isEqualByComparingTo("57.34");
    }

    // 5. Paso > 1: solo múltiplos del paso a partir del mínimo son válidos.
    @Test
    void pasoMayorQueUno_aceptaMultiploYRechazaNoMultiplo() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, new BigDecimal("20"),
            new BigDecimal("5"), false);
        assertThat(aceptar(v, new BigDecimal("15")).getValorNum()).isEqualByComparingTo("15");
        assertThatThrownBy(rechazar(v, new BigDecimal("12")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no respeta el paso permitido");
    }

    // 6. escalaMax menor que escalaMin -> ParametrizacionService.validarEscalaEstructurada() rechaza (ver ParametrizacionServiceEscalaTest).

    // 7. escalaPaso <= 0 -> ver ParametrizacionServiceEscalaTest (rechazado antes de llegar a Variable).

    // 8. Decimal en escala entera -> rechazo.
    @Test
    void escalaEntera_rechazaValorConDecimales() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false);
        assertThatThrownBy(rechazar(v, new BigDecimal("7.5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debe ser un número entero");
    }

    // 9. Valor menor al mínimo -> rechazo (ya cubierto arriba, se repite aquí con escala completa).
    @Test
    void escalaCompleta_valorMenorAlMinimo_rechaza() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false);
        assertThatThrownBy(rechazar(v, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("menor al mínimo permitido (0)");
    }

    // 10. Valor mayor al máximo -> rechazo.
    @Test
    void escalaCompleta_valorMayorAlMaximo_rechaza() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false);
        assertThatThrownBy(rechazar(v, new BigDecimal("11")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mayor al máximo permitido (10)");
    }

    // 11. Valor fuera del paso -> rechazo (paso=1 en escala 0-100 con decimales).
    @Test
    void escalaEntera_valorFueraDelPaso_rechaza() {
        // Paso 2 a partir de 0: 0,2,4... — 3 no es múltiplo.
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, new BigDecimal("10"),
            new BigDecimal("2"), false);
        assertThatThrownBy(rechazar(v, new BigDecimal("3")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no respeta el paso permitido");
    }

    // 12. Valor válido respetando paso -> aceptación.
    @Test
    void escalaEntera_valorQueRespetaElPaso_seAcepta() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, new BigDecimal("10"),
            new BigDecimal("2"), false);
        assertThat(aceptar(v, new BigDecimal("4")).getValorNum()).isEqualByComparingTo("4");
    }

    // 13. Sin límite superior -> acepta valores mayores que cualquier máximo "razonable".
    @Test
    void sinLimiteSuperior_aceptaValorMuyGrande() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true);
        assertThat(aceptar(v, new BigDecimal("999999")).getValorNum()).isEqualByComparingTo("999999");
    }

    // 14. Negativo en escala mínima 0 (incluso sin límite superior) -> rechazo.
    @Test
    void escalaMinimaCero_rechazaNegativoAunSinLimiteSuperior() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true);
        assertThatThrownBy(rechazar(v, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("menor al mínimo permitido (0)");
    }

    // Caso adicional real (E2E sección 14): conteo de defectos, min=0, sin límite,
    // paso=1 — acepta 0, 1 y 100; rechaza negativo y decimal.
    @Test
    void metricaDeConteo_aceptaEnterosNoNegativos_rechazaNegativoYDecimal() {
        Variable v = variableConEscalaCompleta("NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true);
        assertThat(aceptar(v, BigDecimal.ZERO).getValorNum()).isEqualByComparingTo("0");
        assertThat(aceptar(v, BigDecimal.ONE).getValorNum()).isEqualByComparingTo("1");
        assertThat(aceptar(v, new BigDecimal("100")).getValorNum()).isEqualByComparingTo("100");
        assertThatThrownBy(rechazar(v, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(rechazar(v, new BigDecimal("1.5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debe ser un número entero");
    }

    // Variable sin escala estructurada en absoluto (histórica, ver migración V32):
    // Ejecución no restringe nada — ni tipo ni paso — solo min/max si existieran.
    @Test
    void variableSinEscalaEstructurada_noRestringeTipoNiPaso() {
        Variable v = variableGrupal(); // escalaTipo/Paso/SinLimite quedan null
        assertThat(aceptar(v, new BigDecimal("3.14159")).getValorNum()).isEqualByComparingTo("3.14159");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Revisión de Ejecución — bug real reportado: editar una captura
    // 'por_sprint' cambiando su fecha era rechazado con "Ya existe un valor
    // para... en este sprint", porque el backend solo reconocía una edición
    // cuando la fecha nueva coincidía EXACTO con una ya persistida. El
    // overload de 9 argumentos (con registroId) resuelve esto localizando y
    // actualizando la fila por ID, excluyéndola del chequeo de duplicados.
    //
    // Casos a) a f) del pedido de revisión, uno a uno:
    //   a) crear primera captura -> OK
    //   b) crear segunda captura en el mismo sprint -> rechazado
    //   c) editar la primera captura (sin cambiar fecha) -> OK
    //   d) editarla cambiando su fecha dentro del mismo sprint -> OK
    //   e) editarla intentando moverla fuera del sprint -> rechazado
    //   f) tras editar sigue existiendo UNA sola fila (no dos)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void guardarOActualizarValor_registroId_a_crearPrimeraCaptura_ok() {
        Variable variable = variableGrupal(); // frecuenciaCaptura por defecto = "por_sprint"
        Instant fecha = Instant.parse("2026-08-23T00:00:00Z");

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of());
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndRegistradoAt(sprintId, variableId, fecha))
            .thenReturn(Optional.empty());
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("5"), null, null, null, fecha, null);

        assertThat(resultado.getId()).isNull(); // fila nueva
        assertThat(resultado.getValorNum()).isEqualByComparingTo("5");
    }

    @Test
    void guardarOActualizarValor_registroId_b_crearSegundaCapturaEnElMismoSprint_rechazado() {
        Variable variable = variableGrupal(); // "por_sprint"
        Instant fechaExistente = Instant.parse("2026-08-23T00:00:00Z");
        Instant fechaNueva = Instant.parse("2026-08-24T00:00:00Z");

        RegistroValor primera = new RegistroValor();
        primera.setId(UUID.randomUUID());
        primera.setVariable(variable);
        primera.setSprintId(sprintId);
        primera.setValorNum(new BigDecimal("5"));
        primera.setRegistradoAt(fechaExistente);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of(primera));

        // Sin registroId: es una creación nueva, no una edición de "primera".
        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("6"), null, null, null, fechaNueva, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Editá ese valor en vez de crear una captura nueva");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    @Test
    void guardarOActualizarValor_registroId_c_editarPrimeraCaptura_sinCambiarFecha_ok() {
        Variable variable = variableGrupal();
        UUID idExistente = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-08-23T00:00:00Z");

        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("5"));
        existente.setRegistradoAt(fecha);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findById(idExistente)).thenReturn(Optional.of(existente));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of(existente));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("8"), null, null, null, fecha, idExistente);

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(idExistente); // MISMA fila
        assertThat(captor.getValue().getValorNum()).isEqualByComparingTo("8");
    }

    @Test
    void guardarOActualizarValor_registroId_d_editarCambiandoFechaDentroDelSprint_ok() {
        Variable variable = variableGrupal(); // "por_sprint" — el bug real reportado
        UUID idExistente = UUID.randomUUID();
        Instant fechaVieja = Instant.parse("2026-08-23T00:00:00Z");
        Instant fechaNueva = Instant.parse("2026-08-20T00:00:00Z"); // dentro del sprint (1-31 ago)

        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("5"));
        existente.setRegistradoAt(fechaVieja);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findById(idExistente)).thenReturn(Optional.of(existente));
        // La única fila existente es la que se está editando — al excluirla
        // por registroId, la lista de conflicto queda vacía.
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId)).thenReturn(List.of(existente));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RegistroValor> captor = ArgumentCaptor.forClass(RegistroValor.class);

        RegistroValor resultado = service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("9"), null, null, null, fechaNueva, idExistente);

        verify(registroRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(idExistente); // sigue siendo la MISMA fila
        assertThat(captor.getValue().getRegistradoAt()).isEqualTo(fechaNueva); // fecha actualizada
        assertThat(resultado.getValorNum()).isEqualByComparingTo("9");
        // Nunca se buscó "el registro por fecha" — el camino de edición localiza siempre por ID.
        verify(registroRepo, never())
            .findFirstBySprintIdAndVariable_IdAndRegistradoAt(any(), any(), any());
    }

    @Test
    void guardarOActualizarValor_registroId_e_editarMoviendoFechaFueraDelSprint_rechazado() {
        Variable variable = variableGrupal();
        UUID idExistente = UUID.randomUUID();
        Instant fechaVieja = Instant.parse("2026-08-23T00:00:00Z");
        Instant fechaFueraDeRango = Instant.parse("2026-09-05T00:00:00Z"); // sprint termina el 31/08

        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("5"));
        existente.setRegistradoAt(fechaVieja);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findById(idExistente)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("9"), null, null, null,
                fechaFueraDeRango, idExistente))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("posterior al fin del sprint");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    @Test
    void guardarOActualizarValor_registroId_f_trasEditar_sigueExistiendoUnaSolaFila() {
        // Simula el repositorio real: una lista mutable que representa la
        // tabla — save() sobre una fila con ID existente actualiza en el
        // lugar, nunca agrega una segunda entrada.
        Variable variable = variableGrupal();
        UUID idExistente = UUID.randomUUID();
        Instant fechaVieja = Instant.parse("2026-08-23T00:00:00Z");
        Instant fechaNueva = Instant.parse("2026-08-21T00:00:00Z");

        RegistroValor existente = new RegistroValor();
        existente.setId(idExistente);
        existente.setVariable(variable);
        existente.setSprintId(sprintId);
        existente.setValorNum(new BigDecimal("5"));
        existente.setRegistradoAt(fechaVieja);

        List<RegistroValor> tabla = new java.util.ArrayList<>(List.of(existente));

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findById(idExistente)).thenReturn(Optional.of(existente));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenAnswer(inv -> List.copyOf(tabla));
        when(registroRepo.save(any(RegistroValor.class))).thenAnswer(inv -> inv.getArgument(0)); // misma referencia, no agrega fila

        service.guardarOActualizarValor(
            variable, sprintId, "user-a", new BigDecimal("7"), null, null, null, fechaNueva, idExistente);

        assertThat(tabla).hasSize(1); // sigue habiendo UNA sola fila
        assertThat(tabla.get(0).getId()).isEqualTo(idExistente);
        assertThat(tabla.get(0).getRegistradoAt()).isEqualTo(fechaNueva);
    }

    // Caso adicional (no listado a-f pero corolario directo de "excluir el
    // propio registro del chequeo de duplicados"): si al editar la nueva
    // fecha choca con OTRA captura real y distinta, sigue rechazándose — solo
    // se excluye la fila que se está editando, nunca las demás.
    @Test
    void guardarOActualizarValor_registroId_editarChocandoConOtroRegistroReal_rechazado() {
        Variable variable = variableGrupal();
        variable.setFrecuenciaCaptura("diaria");
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Instant fechaA = Instant.parse("2026-08-10T00:00:00Z");
        Instant fechaB = Instant.parse("2026-08-15T00:00:00Z");

        RegistroValor registroA = new RegistroValor();
        registroA.setId(idA);
        registroA.setVariable(variable);
        registroA.setSprintId(sprintId);
        registroA.setValorNum(new BigDecimal("1"));
        registroA.setRegistradoAt(fechaA);

        RegistroValor registroB = new RegistroValor();
        registroB.setId(idB);
        registroB.setVariable(variable);
        registroB.setSprintId(sprintId);
        registroB.setValorNum(new BigDecimal("2"));
        registroB.setRegistradoAt(fechaB);

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintQueAbarcaAgosto2026()));
        when(registroRepo.findById(idA)).thenReturn(Optional.of(registroA));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variableId))
            .thenReturn(List.of(registroA, registroB));

        // Editar A intentando moverla al mismo día que B: B sigue siendo un
        // conflicto real (no es la fila que se edita) -> rechazado.
        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("9"), null, null, null, fechaB, idA))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Editá ese valor en vez de crear una captura nueva");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }

    // Defensa en profundidad: un registroId que no pertenece a esta
    // variable/sprint (ej. manipulado por el cliente) se rechaza en vez de
    // editarse a ciegas.
    @Test
    void guardarOActualizarValor_registroId_deOtroSprint_rechazado() {
        Variable variable = variableGrupal();
        UUID idDeOtroSprint = UUID.randomUUID();
        UUID otroSprintId = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-08-23T00:00:00Z");

        RegistroValor deOtroSprint = new RegistroValor();
        deOtroSprint.setId(idDeOtroSprint);
        deOtroSprint.setVariable(variable);
        deOtroSprint.setSprintId(otroSprintId);
        deOtroSprint.setValorNum(new BigDecimal("5"));
        deOtroSprint.setRegistradoAt(fecha);

        when(registroRepo.findById(idDeOtroSprint)).thenReturn(Optional.of(deOtroSprint));

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("9"), null, null, null, fecha, idDeOtroSprint))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no corresponde a esta variable/sprint");

        verify(registroRepo, never()).save(any(RegistroValor.class));
        // El chequeo de pertenencia ocurre antes de tocar sprintRepo (no llega a validar fechas).
        verify(sprintRepo, never()).findById(any());
    }

    @Test
    void guardarOActualizarValor_registroId_inexistente_rechazado() {
        Variable variable = variableGrupal();
        UUID idInexistente = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-08-23T00:00:00Z");

        when(registroRepo.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guardarOActualizarValor(
                variable, sprintId, "user-a", new BigDecimal("9"), null, null, null, fecha, idInexistente))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ya no existe");

        verify(registroRepo, never()).save(any(RegistroValor.class));
    }
}
