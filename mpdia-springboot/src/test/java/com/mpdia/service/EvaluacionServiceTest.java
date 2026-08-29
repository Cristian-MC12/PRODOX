// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.MetricaEvaluacionDetalleDto;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricaCategoria;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * FASE 8C — cubre únicamente evaluarDetalle() y el transporte de
 * Variable.descripcion hacia MetricaEvaluacionDetalleDto.variableDescripcion.
 * No repite cobertura de evaluar()/evaluarSprint() (EvaluacionSprintDto no
 * fue tocado en esta fase, ver informe de FASE 8C).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluacionService — pruebas unitarias (FASE 8C)")
class EvaluacionServiceTest {

    @Mock SprintRepository sprintRepo;
    @Mock VariableRepository variableRepo;
    @Mock RegistroValorRepository registroRepo;

    @InjectMocks EvaluacionService service;

    private UUID proyectoId;
    private UUID variableId;
    private UUID sprintId;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        variableId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
    }

    private Variable crearVariable(String nombre, String descripcion) {
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setId((short) 1);
        categoria.setNombre("Significado");

        Metrica metrica = new Metrica();
        metrica.setId(UUID.randomUUID());
        metrica.setCategoria(categoria);
        metrica.setCodigo("MET-1");
        metrica.setNombre("Métrica de prueba");

        Variable v = new Variable();
        v.setId(variableId);
        v.setProyectoId(proyectoId);
        v.setMetrica(metrica);
        v.setNombre(nombre);
        v.setDescripcion(descripcion);
        v.setTipoAlcance("grupal");
        v.setFrecuenciaCaptura("por_sprint");
        v.setFormulaTexto("suma simple");
        v.setActiva(true);
        return v;
    }

    private Sprint crearSprint(int numero) {
        return crearSprint(sprintId, numero);
    }

    private Sprint crearSprint(UUID id, int numero) {
        Sprint s = new Sprint();
        s.setId(id);
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setEstado("finalizado");
        s.setFechaInicio(LocalDate.now().minusWeeks(2));
        s.setFechaFin(LocalDate.now().minusWeeks(1));
        return s;
    }

    private RegistroValor crearRegistro(Variable variable, BigDecimal valor, Instant registradoAt) {
        return crearRegistro(variable, valor, registradoAt, sprintId);
    }

    private RegistroValor crearRegistro(Variable variable, BigDecimal valor, Instant registradoAt, UUID enSprintId) {
        RegistroValor r = new RegistroValor();
        r.setId(UUID.randomUUID());
        r.setVariable(variable);
        r.setSprintId(enSprintId);
        r.setUserId("sm@test.com");
        r.setValorNum(valor);
        r.setRegistradoAt(registradoAt);
        return r;
    }

    @Test
    @DisplayName("evaluarDetalle: cuando Variable.descripcion existe, el DTO la incluye en variableDescripcion")
    void evaluarDetalle_variableConDescripcion_incluyeVariableDescripcionEnDto() {
        Variable variable = crearVariable("tareas_retrabajadas", "Tareas retrabajadas por sprint");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("5"), Instant.now().minusSeconds(60));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("7"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        List<MetricaEvaluacionDetalleDto> resultado = service.evaluarDetalle(proyectoId);

        assertThat(resultado).hasSize(1);
        MetricaEvaluacionDetalleDto dto = resultado.get(0);
        assertThat(dto.variableNombre()).isEqualTo("tareas_retrabajadas");
        assertThat(dto.variableDescripcion()).isEqualTo("Tareas retrabajadas por sprint");
    }

    @Test
    @DisplayName("evaluarDetalle: cuando Variable.descripcion es null, el DTO conserva variableNombre y variableDescripcion queda null (sin inventar texto)")
    void evaluarDetalle_variableSinDescripcion_conservaVariableNombreYNoInventaTexto() {
        Variable variable = crearVariable("tareas_retrabajadas", null);
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("5"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1));

        List<MetricaEvaluacionDetalleDto> resultado = service.evaluarDetalle(proyectoId);

        assertThat(resultado).hasSize(1);
        MetricaEvaluacionDetalleDto dto = resultado.get(0);
        assertThat(dto.variableNombre()).isEqualTo("tareas_retrabajadas"); // se conserva intacto
        assertThat(dto.variableDescripcion()).isNull(); // no se inventa ni se genera uno artificial
    }

    @Test
    @DisplayName("evaluarDetalle: el resto de los campos del DTO (categoría, alcance, fórmula, frecuencia) no cambian")
    void evaluarDetalle_otrosCamposDelDto_noCambian() {
        Variable variable = crearVariable("capacidad_de_trabajo", "Capacidad de trabajo del equipo");
        Sprint sprint = crearSprint(2);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("10"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1));

        MetricaEvaluacionDetalleDto dto = service.evaluarDetalle(proyectoId).get(0);

        assertThat(dto.variableId()).isEqualTo(variable.getId());
        assertThat(dto.categoria()).isEqualTo("Significado");
        assertThat(dto.tipoAlcance()).isEqualTo("grupal");
        assertThat(dto.frecuenciaCaptura()).isEqualTo("por_sprint");
        assertThat(dto.formulaTexto()).isEqualTo("suma simple");
        assertThat(dto.registros()).hasSize(1);
        assertThat(dto.porSprint()).hasSize(1);
    }

    @Test
    @DisplayName("evaluarDetalle: los cálculos estadísticos existentes (promedio, min, max) no se alteran por el nuevo campo")
    void evaluarDetalle_calculosEstadisticos_noCambian() {
        // Corrección Ejecución/Tendencias: 3 registros de UNA variable "por_sprint" deben
        // pertenecer a 3 sprints DISTINTOS — 3 registros en el mismo sprint no es un caso
        // real para esta frecuencia (EjecucionService.validarFrecuencia ya lo impide al
        // capturar) y con el agrupamiento por período nuevo se contarían como 1 solo período.
        Variable variable = crearVariable("calidad_twq", "Calidad del código (TWQ)");
        UUID sprint2Id = UUID.randomUUID();
        UUID sprint3Id = UUID.randomUUID();
        Sprint sprint1 = crearSprint(sprintId, 1);
        Sprint sprint2 = crearSprint(sprint2Id, 2);
        Sprint sprint3 = crearSprint(sprint3Id, 3);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("4"), Instant.now().minusSeconds(120), sprintId);
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("6"), Instant.now().minusSeconds(60), sprint2Id);
        RegistroValor r3 = crearRegistro(variable, new BigDecimal("8"), Instant.now(), sprint3Id);

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint1, sprint2, sprint3));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2, r3));

        MetricaEvaluacionDetalleDto dto = service.evaluarDetalle(proyectoId).get(0);

        assertThat(dto.estadisticas().totalRegistros()).isEqualTo(3);
        assertThat(dto.estadisticas().promedio()).isEqualByComparingTo("6.00");
        assertThat(dto.estadisticas().minimo()).isEqualByComparingTo("4");
        assertThat(dto.estadisticas().maximo()).isEqualByComparingTo("8");
        assertThat(dto.estadisticas().primerValor()).isEqualByComparingTo("4");
        assertThat(dto.estadisticas().ultimoValor()).isEqualByComparingTo("8");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Corrección Ejecución/Tendencias: la tendencia/variabilidad debe respetar
    // la frecuencia de captura de la variable — 2 registros del MISMO período
    // (mismo sprint/semana/día, según la frecuencia) NO son 2 períodos
    // comparables. Casos A-G pedidos explícitamente.
    // ══════════════════════════════════════════════════════════════════════

    // A) Por sprint, 1 registro: no calcula tendencia, indica insuficiencia.
    @Test
    @DisplayName("A) por_sprint con 1 registro: tendencia null, variabilidad null, totalRegistros=1")
    void porSprint_unRegistro_noCalculaTendencia() {
        Variable variable = crearVariable("valor_entregas", "Valor de entregas aceptadas");
        Sprint sprint = crearSprint(5);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("7"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1);
        assertThat(estadisticas.promedio()).isEqualByComparingTo("7");
        assertThat(estadisticas.minimo()).isEqualByComparingTo("7");
        assertThat(estadisticas.maximo()).isEqualByComparingTo("7");
        assertThat(estadisticas.tendencia()).isNull();
        assertThat(estadisticas.variabilidad()).isNull();
    }

    // B) Por sprint, 2 registros (2 sprints distintos): sí permite tendencia.
    @Test
    @DisplayName("B) por_sprint con 2 registros en 2 sprints: sí calcula tendencia")
    void porSprint_dosRegistrosDosSprints_calculaTendencia() {
        Variable variable = crearVariable("valor_entregas", "Valor de entregas aceptadas");
        UUID sprint2Id = UUID.randomUUID();
        Sprint sprint1 = crearSprint(sprintId, 1);
        Sprint sprint2 = crearSprint(sprint2Id, 2);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("5"), Instant.now().minusSeconds(60), sprintId);
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("9"), Instant.now(), sprint2Id);

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint1, sprint2));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(2);
        assertThat(estadisticas.tendencia()).isEqualTo("ascendente");
    }

    // Caso explícito del defecto: 2 registros pero del MISMO sprint (drift histórico o
    // reenvío) no deben contarse como 2 períodos comparables para una variable por_sprint.
    @Test
    @DisplayName("por_sprint con 2 registros del MISMO sprint: se agrupan en 1 solo período, sin tendencia")
    void porSprint_dosRegistrosMismoSprint_seAgrupanEnUnSoloPeriodo() {
        Variable variable = crearVariable("valor_entregas", "Valor de entregas aceptadas");
        Sprint sprint = crearSprint(5);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("5"), Instant.now().minusSeconds(60));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("9"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1); // mismo sprint = 1 período
        assertThat(estadisticas.promedio()).isEqualByComparingTo("9"); // se queda con el más reciente
        assertThat(estadisticas.tendencia()).isNull();
    }

    // C) Semanal, 1 registro: no calcula tendencia.
    @Test
    @DisplayName("C) semanal con 1 registro: tendencia null")
    void semanal_unRegistro_noCalculaTendencia() {
        Variable variable = crearVariable("impedimentos", "Impedimentos reportados");
        variable.setFrecuenciaCaptura("semanal");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("3"), Instant.parse("2026-08-03T00:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1);
        assertThat(estadisticas.tendencia()).isNull();
    }

    // D) Semanal, 2 registros en 2 semanas ISO distintas: sí permite tendencia.
    @Test
    @DisplayName("D) semanal con 2 registros en 2 semanas ISO distintas: sí calcula tendencia")
    void semanal_dosRegistrosDosSemanas_calculaTendencia() {
        Variable variable = crearVariable("impedimentos", "Impedimentos reportados");
        variable.setFrecuenciaCaptura("semanal");
        Sprint sprint = crearSprint(1);
        // 2026-08-03 (lunes, semana ISO 32) y 2026-08-11 (martes, semana ISO 33).
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("3"), Instant.parse("2026-08-03T00:00:00Z"));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("6"), Instant.parse("2026-08-11T00:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(2);
        assertThat(estadisticas.tendencia()).isEqualTo("ascendente");
    }

    // Dos capturas en la MISMA semana ISO (aunque días distintos) cuentan como 1 período.
    @Test
    @DisplayName("semanal con 2 registros en la MISMA semana ISO: se agrupan en 1 solo período")
    void semanal_dosRegistrosMismaSemana_seAgrupanEnUnSoloPeriodo() {
        Variable variable = crearVariable("impedimentos", "Impedimentos reportados");
        variable.setFrecuenciaCaptura("semanal");
        Sprint sprint = crearSprint(1);
        // 2026-08-03 (lunes) y 2026-08-05 (miércoles): misma semana ISO 32.
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("3"), Instant.parse("2026-08-03T00:00:00Z"));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("6"), Instant.parse("2026-08-05T00:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1);
        assertThat(estadisticas.tendencia()).isNull();
    }

    // E) Diaria, 1 registro: no calcula tendencia.
    @Test
    @DisplayName("E) diaria con 1 registro: tendencia null")
    void diaria_unRegistro_noCalculaTendencia() {
        Variable variable = crearVariable("bugs_reportados", "Bugs reportados");
        variable.setFrecuenciaCaptura("diaria");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("2"), Instant.parse("2026-08-03T00:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1);
        assertThat(estadisticas.tendencia()).isNull();
    }

    // F) Diaria, 2 registros en 2 días distintos: sí permite tendencia.
    @Test
    @DisplayName("F) diaria con 2 registros en 2 días distintos: sí calcula tendencia")
    void diaria_dosRegistrosDosDias_calculaTendencia() {
        Variable variable = crearVariable("bugs_reportados", "Bugs reportados");
        variable.setFrecuenciaCaptura("diaria");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("2"), Instant.parse("2026-08-03T00:00:00Z"));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("5"), Instant.parse("2026-08-04T00:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(2);
        assertThat(estadisticas.tendencia()).isEqualTo("ascendente");
    }

    // Dos capturas el MISMO día cuentan como 1 período.
    @Test
    @DisplayName("diaria con 2 registros el MISMO día: se agrupan en 1 solo período")
    void diaria_dosRegistrosMismoDia_seAgrupanEnUnSoloPeriodo() {
        Variable variable = crearVariable("bugs_reportados", "Bugs reportados");
        variable.setFrecuenciaCaptura("diaria");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("2"), Instant.parse("2026-08-03T08:00:00Z"));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("5"), Instant.parse("2026-08-03T18:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(1);
        assertThat(estadisticas.tendencia()).isNull();
    }

    // Frecuencia "ilimitada": nunca se agrupa (sin período definido) — comportamiento
    // preexistente, sin cambios.
    @Test
    @DisplayName("ilimitada: NO se agrupa, cada registro cuenta como su propio punto")
    void ilimitada_noAgrupaAunqueSeanDelMismoSprintYDia() {
        Variable variable = crearVariable("satisfaccion", "Satisfacción reportada");
        variable.setFrecuenciaCaptura("ilimitada");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("2"), Instant.parse("2026-08-03T08:00:00Z"));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("5"), Instant.parse("2026-08-03T18:00:00Z"));

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2));

        var estadisticas = service.evaluarDetalle(proyectoId).get(0).estadisticas();

        assertThat(estadisticas.totalRegistros()).isEqualTo(2);
        assertThat(estadisticas.tendencia()).isEqualTo("ascendente");
    }
}
