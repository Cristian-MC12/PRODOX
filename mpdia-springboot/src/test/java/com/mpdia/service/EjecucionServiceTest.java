// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.RegistrarValorRequest;
import com.mpdia.dto.RegistroValorDto;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    private EjecucionService service;

    private UUID sprintId;
    private UUID variableId;

    @BeforeEach
    void setUp() {
        service = new EjecucionService(registroRepo, variableRepo);
        sprintId = UUID.randomUUID();
        variableId = UUID.randomUUID();
    }

    private Variable variableGrupal() {
        Variable v = new Variable();
        v.setId(variableId);
        v.setNombre("variable_grupal_test");
        v.setTipoAlcance("grupal");
        v.setActiva(true);
        return v;
    }

    private Variable variableIndividual() {
        Variable v = new Variable();
        v.setId(variableId);
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
    void registrar_variableInactiva_lanzaExcepcion() {
        Variable variable = variableGrupal();
        variable.setActiva(false);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
        RegistrarValorRequest req = new RegistrarValorRequest(variableId, sprintId, new BigDecimal("1"), null, null, null);

        assertThatThrownBy(() -> service.registrar("user-a", req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactiva");
    }

    @Test
    void registrar_primeraCaptura_creaRegistroYRetornaDto() {
        Variable variable = variableGrupal();
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(variable));
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
}
