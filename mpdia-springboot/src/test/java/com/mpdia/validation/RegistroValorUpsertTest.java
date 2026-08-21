// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.entity.Metrica;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import com.mpdia.service.EjecucionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 16.11 — Reproduce contra Postgres real el patrón de duplicado
 * encontrado en producción para la variable "Cambios de alcance por sprint"
 * (dos filas en registro_valores para la misma variable+sprint), usando
 * datos sintéticos propios que se revierten al final de cada test
 * (@Transactional). NO toca ni modifica los datos reales de producción ni
 * las filas duplicadas ya existentes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistroValorUpsertTest {

    // SIG-VEL-01 (Velocidad), reutilizada de otros tests de esta suite —
    // distinta de SIG-SC-02/SIG-VEL-02 para no rozar los datos del piloto.
    private static final UUID METRICA_VEL = UUID.fromString("d0006325-a144-489f-b09c-e51b3e87dfa1");
    private static final UUID PROYECTO_1 = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");

    @Autowired private MetricaRepository metricaRepo;
    @Autowired private VariableRepository variableRepo;
    @Autowired private RegistroValorRepository registroRepo;
    @Autowired private SprintRepository sprintRepo;
    @Autowired private EjecucionService ejecucionService;

    // registro_valores.sprint_id tiene FK real hacia sprints — se necesita
    // un Sprint real (sintético, revertido por @Transactional), no basta un
    // UUID aleatorio. Números altos y distintos para no chocar con los
    // sprints reales 1-6 de PROYECTO_1 (no hay UNIQUE(proyecto_id,numero)
    // en el esquema, pero se evita igual por prolijidad).
    private final AtomicInteger numeroSprint = new AtomicInteger(9001);

    private Variable nuevaVariableGrupal(Metrica metrica, String nombre) {
        Variable variable = new Variable();
        variable.setProyectoId(PROYECTO_1);
        variable.setMetrica(metrica);
        variable.setNombre(nombre);
        variable.setTipoAlcance("grupal");
        variable.setTipoDato("numerico");
        variable.setActiva(true);
        return variableRepo.saveAndFlush(variable);
    }

    private UUID nuevoSprint() {
        Sprint sprint = new Sprint();
        sprint.setProyectoId(PROYECTO_1);
        sprint.setNumero(numeroSprint.getAndIncrement());
        sprint.setSprintGoal("Sprint sintético de test — RegistroValorUpsertTest");
        sprint.setEstado("en_ejecucion");
        sprint.setFechaInicio(LocalDate.now());
        return sprintRepo.saveAndFlush(sprint).getId();
    }

    @Test
    void guardarOActualizarValor_reproduceCasoDeDuplicadoReal_yaNoDuplica() {
        Metrica metrica = metricaRepo.findById(METRICA_VEL).orElseThrow();
        Variable variable = nuevaVariableGrupal(metrica, "variable_upsert_test_" + UUID.randomUUID());
        UUID sprintId = nuevoSprint();

        // Primera captura del sprint (equivalente al primer valor real "0").
        ejecucionService.guardarOActualizarValor(
            variable, sprintId, "user-test", new BigDecimal("0"), null, null, null);

        // Antes de esta corrección, un segundo registro para la misma
        // variable+sprint insertaba una fila nueva (el duplicado real
        // encontrado: valor 0 y, 12 días después, valor 6, misma variable,
        // mismo sprint). Ahora debe actualizar la misma fila.
        ejecucionService.guardarOActualizarValor(
            variable, sprintId, "user-test", new BigDecimal("6"), null, null, null);

        List<RegistroValor> registros =
            registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId());

        assertThat(registros).hasSize(1); // NO hay duplicado
        assertThat(registros.get(0).getValorNum()).isEqualByComparingTo("6"); // último valor vigente
    }

    @Test
    void guardarOActualizarValor_noModificaRegistrosDeOtroSprint() {
        Metrica metrica = metricaRepo.findById(METRICA_VEL).orElseThrow();
        Variable variable = nuevaVariableGrupal(metrica, "variable_upsert_test2_" + UUID.randomUUID());
        UUID sprintA = nuevoSprint();
        UUID sprintB = nuevoSprint();

        ejecucionService.guardarOActualizarValor(variable, sprintA, "user-test", new BigDecimal("1"), null, null, null);
        ejecucionService.guardarOActualizarValor(variable, sprintB, "user-test", new BigDecimal("2"), null, null, null);

        assertThat(registroRepo.findBySprintIdAndVariable_Id(sprintA, variable.getId())).hasSize(1);
        assertThat(registroRepo.findBySprintIdAndVariable_Id(sprintB, variable.getId())).hasSize(1);
    }

    @Test
    void guardarOActualizarValor_variableIndividual_noColisionaEntreUsuarios() {
        Metrica metrica = metricaRepo.findById(METRICA_VEL).orElseThrow();
        Variable variable = new Variable();
        variable.setProyectoId(PROYECTO_1);
        variable.setMetrica(metrica);
        variable.setNombre("variable_upsert_individual_" + UUID.randomUUID());
        variable.setTipoAlcance("individual");
        variable.setTipoDato("numerico");
        variable.setActiva(true);
        variable = variableRepo.saveAndFlush(variable);

        UUID sprintId = nuevoSprint();

        ejecucionService.guardarOActualizarValor(variable, sprintId, "user-a", new BigDecimal("1"), null, null, null);
        ejecucionService.guardarOActualizarValor(variable, sprintId, "user-b", new BigDecimal("2"), null, null, null);
        // user-a vuelve a registrar en el mismo sprint: debe actualizar SU propia fila, no crear una tercera.
        ejecucionService.guardarOActualizarValor(variable, sprintId, "user-a", new BigDecimal("9"), null, null, null);

        List<RegistroValor> registros =
            registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId());

        assertThat(registros).hasSize(2); // una por usuario, no tres
        assertThat(registros).anySatisfy(r -> {
            assertThat(r.getUserId()).isEqualTo("user-a");
            assertThat(r.getValorNum()).isEqualByComparingTo("9");
        });
        assertThat(registros).anySatisfy(r -> {
            assertThat(r.getUserId()).isEqualTo("user-b");
            assertThat(r.getValorNum()).isEqualByComparingTo("2");
        });
    }
}
