// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CalcularMetricaRequest;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.Proyecto;
import com.prodox.entity.RegistroValor;
import com.prodox.entity.ResultadoMetrica;
import com.prodox.entity.Sprint;
import com.prodox.entity.Variable;
import com.prodox.formula.FormulaEvaluator;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.RegistroValorRepository;
import com.prodox.repository.ResultadoMetricaRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Auditoría transversal: calcularMetrica() recibía un parámetro userId que nunca se
 * usaba — cualquier usuario autenticado podía calcular una métrica de cualquier
 * proyecto conociendo su UUID, pese a que el sprint↔proyecto ya se validaba
 * correctamente entre sí. Estas pruebas cubren solo el nuevo chequeo de membresía;
 * el motor de cálculo en sí ya tiene cobertura propia y no se modifica aquí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalculoMetricaService — autorización")
class CalculoMetricaServiceTest {

    @Mock private MetricaRepository metricaRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private SprintRepository sprintRepo;
    @Mock private MetricParametrizacionRepository parametrizacionRepo;
    @Mock private VariableRepository variableRepo;
    @Mock private RegistroValorRepository registroRepo;
    @Mock private ResultadoMetricaRepository resultadoRepo;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private CalculoMetricaService service;

    private UUID metricaId;
    private UUID proyectoId;
    private UUID sprintId;

    @BeforeEach
    void setUp() {
        service = new CalculoMetricaService(
                metricaRepo, proyectoRepo, sprintRepo, parametrizacionRepo,
                variableRepo, registroRepo, resultadoRepo, projectMemberRepo,
                new FormulaEvaluator(), new ObjectMapper());

        metricaId  = UUID.randomUUID();
        proyectoId = UUID.randomUUID();
        sprintId   = UUID.randomUUID();

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(new com.prodox.entity.Metrica()));
    }

    @Test
    @DisplayName("calcularMetrica: usuario externo al proyecto lanza SecurityException")
    void calcularMetrica_usuarioExterno_lanzaSecurityException() {
        String externoId = "externo-1";
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, externoId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        // Nunca llega a buscar la parametrización — la autorización corta el flujo antes.
        org.mockito.Mockito.verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    @DisplayName("calcularMetrica: miembro del proyecto pasa la validación de autorización")
    void calcularMetrica_miembroDelProyecto_pasaValidacion() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        // Pasa la autorización y llega a la siguiente validación de negocio
        // (parametrización no encontrada), sin lanzar SecurityException.
        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "user-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe parametrización aprobada");
    }

    @Test
    @DisplayName("calcularMetrica: sprint de otro proyecto se rechaza antes de validar membresía")
    void calcularMetrica_sprintDeOtroProyecto_seRechazaAntesDeMembresía() {
        UUID otroProyecto = UUID.randomUUID();
        Sprint sprintDeOtroProyecto = new Sprint();
        sprintDeOtroProyecto.setId(sprintId);
        sprintDeOtroProyecto.setProyectoId(otroProyecto);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDeOtroProyecto));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "cualquiera"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El sprint no pertenece al proyecto");

        org.mockito.Mockito.verifyNoInteractions(projectMemberRepo);
    }

    // ════════════════════════════════════════════════════════════════════
    // Revisión de captura individual — agregación multi-miembro por
    // tipoOperacion. Cubre SUMA/PROMEDIO (ya agregaban todo, sin cambios de
    // comportamiento) y DIRECTO/FORMULA (antes tomaban solo el más reciente,
    // ahora exigen y usan Variable.agregacionMiembros cuando hay 2+ registros
    // individuales).
    // ════════════════════════════════════════════════════════════════════

    /**
     * Construye una parametrización aprobada con forma REALISTA: tipoOperacion en
     * la columna propia (fuente de verdad que lee CalculoMetricaService tras la
     * corrección de lectura), y un configuracionAprobadaJson no vacío que, a
     * propósito, NO incluye ninguna clave "tipo"/"variable_id"/"expresion" — igual
     * que el snapshot real de MetricRankingService.guardarSnapshotConNombreVariable(),
     * para probar que el motor ya no depende de esas claves inexistentes.
     */
    private MetricParametrizacion parametrizacionConTipoOperacion(String tipoOperacion) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setVersion(1);
        p.setTipoOperacion(tipoOperacion);
        p.setConfiguracionAprobadaJson(
                "{\"indicadorVariable\":\"x\",\"procedimiento\":\"y\",\"nombreVariable\":\"z\"}");
        return p;
    }

    private MetricParametrizacion parametrizacionFormula(String formulaAcademica) {
        MetricParametrizacion p = parametrizacionConTipoOperacion("FORMULA");
        p.setFormulaAcademica(formulaAcademica);
        return p;
    }

    /** Parametrización aprobada sin tipoOperacion (posible vía MetricRankingService.verificar()). */
    private MetricParametrizacion parametrizacionSinTipoOperacion() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setVersion(1);
        p.setConfiguracionAprobadaJson("{\"indicadorVariable\":\"x\"}");
        return p;
    }

    private Variable variable(UUID id, String tipoAlcance, String agregacionMiembros) {
        return variable(id, tipoAlcance, agregacionMiembros, "variable-" + id);
    }

    private Variable variable(UUID id, String tipoAlcance, String agregacionMiembros, String nombre) {
        Variable v = new Variable();
        v.setId(id);
        v.setNombre(nombre);
        v.setTipoAlcance(tipoAlcance);
        v.setAgregacionMiembros(agregacionMiembros);
        return v;
    }

    private RegistroValor registro(Variable variable, BigDecimal valor, String userId, Instant fecha) {
        RegistroValor r = new RegistroValor();
        r.setVariable(variable);
        r.setSprintId(sprintId);
        r.setUserId(userId);
        r.setValorNum(valor);
        r.setRegistradoAt(fecha);
        return r;
    }

    private void stubMiembroDelProyecto(String userId) {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
    }

    @Test
    @DisplayName("SUMA: tres miembros registran 2, 3 y 4 -> resultado 9 (ya agregaba todo, sin cambios)")
    void calcularMetrica_suma_tresMiembros_sumaTodosLosRegistros() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null); // sin agregacionMiembros: SUMA no la necesita
        MetricParametrizacion param = parametrizacionConTipoOperacion("SUMA");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("4"), "pedro", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("3"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("2"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("9.0000");
    }

    @Test
    @DisplayName("PROMEDIO: tres miembros registran 60, 80 y 100 -> resultado 80")
    void calcularMetrica_promedio_tresMiembros_promediaTodosLosRegistros() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null);
        MetricParametrizacion param = parametrizacionConTipoOperacion("PROMEDIO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("100"), "maria", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("60"), "pedro", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("80"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("80.0000");
    }

    // Requisito explícito de la revisión de captura por parametrización:
    // "responsable de captura" (tipoAlcance) y "fórmula/operación" son
    // configuraciones independientes — la MISMA operación (SUMA) sobre los
    // MISMOS registros produce el MISMO resultado sin importar si la
    // variable es 'individual' (alcance EQUIPO) o 'grupal' (alcance SCRUM
    // MASTER). Mismos datos que calcularMetrica_suma_tresMiembros_sumaTodosLosRegistros
    // (2+3+4=9), pero con tipoAlcance='grupal'.
    @Test
    @DisplayName("SUMA + variable grupal (alcance SCRUM MASTER): mismo resultado que con variable individual (alcance EQUIPO) — el responsable no cambia el cálculo")
    void calcularMetrica_suma_variableGrupal_mismoResultadoQueVariableIndividual() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", null);
        MetricParametrizacion param = parametrizacionConTipoOperacion("SUMA");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("4"), "pedro", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("3"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("2"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("9.0000"); // idéntico al caso 'individual'
    }

    @Test
    @DisplayName("DIRECTO + individual + 2 registros + agregacionMiembros=SUMA: reduce antes de usar el valor directo")
    void calcularMetrica_directo_individualConAgregacionSuma_reduceAntesDeUsarValorDirecto() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", "SUMA");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "pedro", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("3"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("8.0000"); // 5+3, no solo el más reciente (5)
    }

    @Test
    @DisplayName("DIRECTO + individual + 1 solo registro: se usa directamente, sin exigir agregacionMiembros")
    void calcularMetrica_directo_individualConUnSoloRegistro_noExigeAgregacion() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null); // sin configurar — no hace falta con 1 solo registro
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("7"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("7.0000");
    }

    @Test
    @DisplayName("DIRECTO + individual + 2 registros SIN agregacionMiembros configurada: error explícito, no toma el más reciente en silencio")
    void calcularMetrica_directo_individualSinAgregacionConfigurada_lanzaErrorExplicito() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null); // SIN configurar
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "pedro", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("3"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "juan"))
                .isInstanceOf(AgregacionMiembrosNoConfiguradaException.class)
                .hasMessageContaining("no tiene configurada Variable.agregacionMiembros");

        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("FORMULA (A+B)/C: A=SUMA de 3 registros, B=PROMEDIO de 2 registros, C=grupal directo -> (12+20)/4=8")
    void calcularMetrica_formula_variablesIndividualesConDistintaAgregacionYVariableGrupal() {
        UUID varA = UUID.randomUUID();
        UUID varB = UUID.randomUUID();
        UUID varC = UUID.randomUUID();
        Variable a = variable(varA, "individual", "SUMA", "var_a");
        Variable b = variable(varB, "individual", "PROMEDIO", "var_b");
        Variable c = variable(varC, "grupal", null, "var_c");

        // Fórmula académica en texto humano (nombres de variable, no ${uuid}) — el
        // motor la traduce internamente, igual que MetricaAcademicaService.
        MetricParametrizacion param = parametrizacionFormula("(var_a + var_b) / var_c");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1))
                .thenReturn(List.of(a, b, c));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varA)).thenReturn(List.of(
                registro(a, new BigDecimal("2"), "juan", Instant.parse("2026-01-03T00:00:00Z")),
                registro(a, new BigDecimal("4"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(a, new BigDecimal("6"), "pedro", Instant.parse("2026-01-01T00:00:00Z"))
        )); // SUMA = 12
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varB)).thenReturn(List.of(
                registro(b, new BigDecimal("10"), "juan", Instant.parse("2026-01-02T00:00:00Z")),
                registro(b, new BigDecimal("30"), "maria", Instant.parse("2026-01-01T00:00:00Z"))
        )); // PROMEDIO = 20
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varC)).thenReturn(List.of(
                registro(c, new BigDecimal("4"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        )); // grupal, valor directo = 4
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("CONTEO: tres registros individuales -> resultado 3")
    void calcularMetrica_directo_individualConAgregacionConteo() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", "CONTEO");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("1"), "juan", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("1"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("1"), "pedro", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("MIN: tres registros individuales -> resultado el mínimo")
    void calcularMetrica_directo_individualConAgregacionMin() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", "MIN");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("9"), "juan", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("2"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("5"), "pedro", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("MAX: tres registros individuales -> resultado el máximo")
    void calcularMetrica_directo_individualConAgregacionMax() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", "MAX");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("9"), "juan", Instant.parse("2026-01-03T00:00:00Z")),
                registro(v, new BigDecimal("2"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("5"), "pedro", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dto.resultado()).isEqualByComparingTo("9.0000");
    }

    @Test
    @DisplayName("Grupal + DIRECTO + 1 solo registro: se usa directamente, sin exigir agregacionMiembros")
    void calcularMetrica_directo_grupal_unSoloRegistro_noExigeAgregacion() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", null);
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("sm");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("11"), "sm", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "sm");

        assertThat(dto.resultado()).isEqualByComparingTo("11.0000");
    }

    // Revisión de captura universal: ANTES, una variable 'grupal' con 2+
    // registros tomaba silenciosamente "el más reciente" (solo el Scrum
    // Master podía capturarla, así que nunca había ambigüedad real). Ahora
    // que cualquier miembro puede registrar su propio valor en variables
    // 'grupal' también, 2+ registros son tan ambiguos como en 'individual' y
    // exigen la misma configuración explícita de Variable.agregacionMiembros
    // — nunca se descarta en silencio el dato de un miembro.
    @Test
    @DisplayName("Grupal + DIRECTO + 2 registros SIN agregacionMiembros: error explícito, ya no toma el más reciente en silencio")
    void calcularMetrica_directo_grupalConDosRegistrosSinAgregacion_lanzaErrorExplicito() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", null); // SIN configurar
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("sm");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("11"), "sm", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("7"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "sm"))
                .isInstanceOf(AgregacionMiembrosNoConfiguradaException.class)
                .hasMessageContaining("no tiene configurada Variable.agregacionMiembros");

        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Grupal + DIRECTO + 2 registros de distintos miembros + agregacionMiembros=SUMA: reduce antes de usar el valor directo")
    void calcularMetrica_directo_grupalConAgregacionSuma_reduceDosMiembros() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", "SUMA");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("sm");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("11"), "sm", Instant.parse("2026-01-02T00:00:00Z")),
                registro(v, new BigDecimal("7"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "sm");

        assertThat(dto.resultado()).isEqualByComparingTo("18.0000"); // 11 + 7, no solo el más reciente (11)
    }

    // Requisito explícito de la revisión de captura universal: los registros
    // de OTRO sprint nunca deben contaminar el cálculo de este sprint. La
    // consulta real (findBySprintIdAndVariable_IdOrderByRegistradoAtDesc) ya
    // scopea por sprintId a nivel de query derivada de Spring Data — este
    // test confirma que el servicio solo consulta y usa el sprint solicitado.
    @Test
    @DisplayName("Los registros de otro sprint no contaminan el cálculo de este sprint")
    void calcularMetrica_registrosDeOtroSprint_noContaminanElCalculo() {
        UUID varId = UUID.randomUUID();
        UUID otroSprintId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", "SUMA");
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        stubMiembroDelProyecto("sm");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        // Solo se registran datos para ESTE sprint; los de "otroSprintId" nunca se stubean con datos,
        // por lo que si el servicio los consultara erróneamente, el mock (Optional/lista vacía por
        // defecto de Mockito) los haría desaparecer del resultado en vez de sumarse por error.
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "sm", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "sm");

        assertThat(dto.resultado()).isEqualByComparingTo("5.0000");
        org.mockito.Mockito.verify(registroRepo, org.mockito.Mockito.never())
                .findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(otroSprintId, varId);
    }

    // ════════════════════════════════════════════════════════════════════
    // Revisión de ResultadoMetrica: vigente vs. histórico (V37). Recalcular
    // nunca borra la fila anterior — la marca vigente=false y crea una nueva
    // vigente=true.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Recalcular la misma métrica/sprint/versión: el resultado anterior pasa a vigente=false")
    void calcularMetrica_recalcular_marcaResultadoAnteriorComoHistorico() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "grupal", null);
        MetricParametrizacion param = parametrizacionConTipoOperacion("DIRECTO");

        ResultadoMetrica anterior = new ResultadoMetrica();
        anterior.setId(UUID.randomUUID());
        anterior.setVigente(true);

        stubMiembroDelProyecto("sm");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "sm", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)).thenReturn(Optional.of(anterior));
        when(resultadoRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.calcularMetrica(metricaId, new CalcularMetricaRequest(proyectoId, sprintId), "sm");

        assertThat(anterior.getVigente()).isFalse();
        // Corrección: el anterior se marca histórico con saveAndFlush (no save), para que
        // el UPDATE llegue a la base de datos antes del INSERT del resultado nuevo — evita
        // que el orden de flush de Hibernate (INSERTs antes que UPDATEs) viole por un
        // instante el índice único parcial idx_resultado_vigente_unico (V37).
        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.times(1)).saveAndFlush(anterior);
        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.times(1)).save(any());
    }

    // ════════════════════════════════════════════════════════════════════
    // Corrección de lectura de la configuración aprobada: MetricParametrizacion.
    // tipoOperacion (columna propia) es el campo canónico que el motor debe leer
    // — nunca una clave del snapshot JSON, que ninguno de los dos flujos de
    // aprobación reales escribe de forma consistente (ver ParametrizacionService.
    // aprobarParametrizacion() vs. MetricRankingService.guardarSnapshotConNombreVariable()).
    // ════════════════════════════════════════════════════════════════════

    // Requisito explícito de la corrección: dos métricas distintas, cada una con
    // su propia operación aprobada, calculadas en el mismo sprint, no deben
    // interferir entre sí ni "contagiarse" la operación de la otra.
    @Test
    @DisplayName("Dos métricas con distinta operación aprobada en el mismo sprint: cada una usa SU PROPIA operación")
    void calcularMetrica_dosMetricasConDistintaOperacion_cadaUnaUsaSuPropiaOperacion() {
        // metricaId (con su stub de metricaRepo ya montado en setUp()) se reutiliza
        // como una de las dos métricas, para no dejar ese stub sin usar en este test.
        UUID metricaEstadoAnimo = metricaId;
        UUID metricaErrores = UUID.randomUUID();

        UUID varAnimo = UUID.randomUUID();
        Variable vAnimo = variable(varAnimo, "individual", null);
        MetricParametrizacion paramAnimo = parametrizacionConTipoOperacion("PROMEDIO");

        UUID varErrores = UUID.randomUUID();
        Variable vErrores = variable(varErrores, "individual", null);
        MetricParametrizacion paramErrores = parametrizacionConTipoOperacion("SUMA");

        stubMiembroDelProyecto("juan");
        when(metricaRepo.findById(metricaErrores)).thenReturn(Optional.of(new com.prodox.entity.Metrica()));

        when(parametrizacionRepo.findUltimaVersionAprobada(metricaEstadoAnimo, proyectoId))
                .thenReturn(Optional.of(paramAnimo));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(paramAnimo.getId(), 1))
                .thenReturn(List.of(vAnimo));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varAnimo)).thenReturn(List.of(
                registro(vAnimo, new BigDecimal("100"), "maria", Instant.parse("2026-01-03T00:00:00Z")),
                registro(vAnimo, new BigDecimal("60"), "pedro", Instant.parse("2026-01-02T00:00:00Z")),
                registro(vAnimo, new BigDecimal("80"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));

        when(parametrizacionRepo.findUltimaVersionAprobada(metricaErrores, proyectoId))
                .thenReturn(Optional.of(paramErrores));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(paramErrores.getId(), 1))
                .thenReturn(List.of(vErrores));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varErrores)).thenReturn(List.of(
                registro(vErrores, new BigDecimal("2"), "juan", Instant.parse("2026-01-01T00:00:00Z")),
                registro(vErrores, new BigDecimal("1"), "maria", Instant.parse("2026-01-02T00:00:00Z")),
                registro(vErrores, new BigDecimal("3"), "pedro", Instant.parse("2026-01-03T00:00:00Z"))
        ));

        when(resultadoRepo.findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(resultadoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dtoAnimo = service.calcularMetrica(
                metricaEstadoAnimo, new CalcularMetricaRequest(proyectoId, sprintId), "juan");
        var dtoErrores = service.calcularMetrica(
                metricaErrores, new CalcularMetricaRequest(proyectoId, sprintId), "juan");

        assertThat(dtoAnimo.resultado()).isEqualByComparingTo("80.0000"); // PROMEDIO(80,60,100)
        assertThat(dtoErrores.resultado()).isEqualByComparingTo("6.0000"); // SUMA(2,1,3)
    }

    // Requisito explícito de la corrección (Parte C): una parametrización aprobada
    // SIN tipoOperacion definido (posible vía MetricRankingService.verificar(), que
    // no exige este campo al aprobar) debe producir un error de negocio claro,
    // NUNCA un NullPointerException.
    @Test
    @DisplayName("Configuración aprobada sin tipoOperacion definido: error de negocio claro, nunca NullPointerException")
    void calcularMetrica_configuracionAprobadaSinTipoOperacion_lanzaErrorDeNegocioClaro() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null);
        MetricParametrizacion param = parametrizacionSinTipoOperacion();

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "juan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene una operación de cálculo válida");

        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.never()).save(any());
    }

    // Complementa el caso anterior: tipoOperacion informado pero con un valor que
    // el motor no reconoce (dato histórico corrupto) — mismo requisito de error
    // de negocio claro en vez de un comportamiento indefinido.
    @Test
    @DisplayName("tipoOperacion con valor no soportado: error de negocio claro")
    void calcularMetrica_tipoOperacionNoSoportado_lanzaErrorDeNegocioClaro() {
        UUID varId = UUID.randomUUID();
        Variable v = variable(varId, "individual", null);
        MetricParametrizacion param = parametrizacionConTipoOperacion("PROMEDIO_PONDERADO");

        stubMiembroDelProyecto("juan");
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.of(param));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 1)).thenReturn(List.of(v));
        when(registroRepo.findBySprintIdAndVariable_IdOrderByRegistradoAtDesc(sprintId, varId)).thenReturn(List.of(
                registro(v, new BigDecimal("5"), "juan", Instant.parse("2026-01-01T00:00:00Z"))
        ));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "juan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no soportado");

        org.mockito.Mockito.verify(resultadoRepo, org.mockito.Mockito.never()).save(any());
    }
}
