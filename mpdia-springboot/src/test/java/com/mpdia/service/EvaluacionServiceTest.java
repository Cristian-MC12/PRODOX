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
        Sprint s = new Sprint();
        s.setId(sprintId);
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setEstado("finalizado");
        s.setFechaInicio(LocalDate.now().minusWeeks(2));
        s.setFechaFin(LocalDate.now().minusWeeks(1));
        return s;
    }

    private RegistroValor crearRegistro(Variable variable, BigDecimal valor, Instant registradoAt) {
        RegistroValor r = new RegistroValor();
        r.setId(UUID.randomUUID());
        r.setVariable(variable);
        r.setSprintId(sprintId);
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
        Variable variable = crearVariable("calidad_twq", "Calidad del código (TWQ)");
        Sprint sprint = crearSprint(1);
        RegistroValor r1 = crearRegistro(variable, new BigDecimal("4"), Instant.now().minusSeconds(120));
        RegistroValor r2 = crearRegistro(variable, new BigDecimal("6"), Instant.now().minusSeconds(60));
        RegistroValor r3 = crearRegistro(variable, new BigDecimal("8"), Instant.now());

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoId)).thenReturn(List.of(variable));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())).thenReturn(List.of(r1, r2, r3));

        MetricaEvaluacionDetalleDto dto = service.evaluarDetalle(proyectoId).get(0);

        assertThat(dto.estadisticas().totalRegistros()).isEqualTo(3);
        assertThat(dto.estadisticas().promedio()).isEqualByComparingTo("6.00");
        assertThat(dto.estadisticas().minimo()).isEqualByComparingTo("4");
        assertThat(dto.estadisticas().maximo()).isEqualByComparingTo("8");
        assertThat(dto.estadisticas().primerValor()).isEqualByComparingTo("4");
        assertThat(dto.estadisticas().ultimoValor()).isEqualByComparingTo("8");
    }
}
