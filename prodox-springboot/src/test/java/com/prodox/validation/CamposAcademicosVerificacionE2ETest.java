// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.validation;

import com.prodox.dto.EjecutarMetricaAcademicaRequest;
import com.prodox.dto.GuardarParametrizacionRequest;
import com.prodox.dto.MetricParametrizacionDto;
import com.prodox.dto.ResultadoMetricaDto;
import com.prodox.dto.VerificarParametrizacionRequest;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.repository.VariableRepository;
import com.prodox.service.MetricRankingService;
import com.prodox.service.MetricaAcademicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 11, bloque 1/2 — demuestra que una parametrización enviada al Scrum Master
 * (MetricRankingService.guardar()) y aprobada por Verificación (MetricRankingService.
 * verificar()) conserva tipoOperacion/formulaAcademica/unidadResultado/fuenteAcademica y
 * puede calcularse realmente en Ejecución (MetricaAcademicaService), para las 3 métricas
 * académicas oficiales que lo requieren (Defectos, FAT, Deuda técnica).
 *
 * Corre en un proyecto/sprint efímero creado dentro de la propia transacción (rollback
 * automático al finalizar cada test) — nunca toca Prueba 1 ni Trabajo 1, ni siquiera
 * transitoriamente.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CamposAcademicosVerificacionE2ETest {

    private static final UUID METRICA_DEFECTOS = UUID.fromString("ec0d74fe-0bf4-4970-af89-dcaa0736c8ed");
    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");
    private static final UUID METRICA_DEUDA = UUID.fromString("40beffdf-13f4-4772-8820-4df93fae525c");

    @Autowired private MetricRankingService rankingService;
    @Autowired private MetricaAcademicaService metricaAcademicaService;
    @Autowired private MetricParametrizacionRepository parametrizacionRepo;
    @Autowired private VariableRepository variableRepo;
    @Autowired private ProyectoRepository proyectoRepo;
    @Autowired private SprintRepository sprintRepo;
    @Autowired private ProjectMemberRepository projectMemberRepo;

    private UUID proyectoId;
    private UUID sprintId;
    private String userEmail;

    @BeforeEach
    void crearSandboxEfimero() {
        userEmail = "user-fase11-" + UUID.randomUUID() + "@test.prodox.com";

        Proyecto p = new Proyecto();
        p.setNombre("Sandbox efimero FASE 11 test");
        p.setMetodo("scrum");
        p.setTimeBoxSemanas(1);
        p.setProductGoal("test");
        p.setSprintGoal("test");
        p.setScrumMasterId(userEmail);
        p.setNumeroSprints(1);
        p = proyectoRepo.saveAndFlush(p);
        proyectoId = p.getId();

        Sprint s = new Sprint();
        s.setProyectoId(proyectoId);
        s.setNumero(1);
        s.setSprintGoal("test");
        s = sprintRepo.saveAndFlush(s);
        sprintId = s.getId();

        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(userEmail);
        m.setUserEmail(userEmail);
        m.setRol("scrum_master");
        projectMemberRepo.saveAndFlush(m);

        Authentication auth = new UsernamePasswordAuthenticationToken(userEmail, null, java.util.List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private MetricParametrizacionDto guardarYAprobar(UUID metricaId, String indicadorVariable,
                                                      String tipoOperacion, String formula, String unidad) {
        GuardarParametrizacionRequest req = new GuardarParametrizacionRequest(
                null, "objetivo test", "procedimiento test", indicadorVariable, "escala test",
                null, proyectoId, metricaId,
                tipoOperacion, formula, unidad, "fuente test FASE 11", null, null, null, null, null, null, null);

        MetricParametrizacionDto guardado = rankingService.guardar(req, userEmail, userEmail);
        assertThat(guardado.status()).isEqualTo("pendiente");

        rankingService.verificar(
                new VerificarParametrizacionRequest(guardado.id(), "aprobar", null), userEmail, userEmail);
        return guardado;
    }

    @Test
    void defectos_suma_conservaCamposYCalculaTrasAprobarPorVerificacion() {
        MetricParametrizacionDto guardado = guardarYAprobar(
                METRICA_DEFECTOS, "defectos_totales", "SUMA", "SUMA(defectos_totales)", "defectos");

        // 1) Los campos académicos sobrevivieron request -> backend -> BD.
        MetricParametrizacion enBD = parametrizacionRepo.findById(guardado.id()).orElseThrow();
        assertThat(enBD.getStatus()).isEqualTo("aprobada");
        assertThat(enBD.getTipoOperacion()).isEqualTo("SUMA");
        assertThat(enBD.getFormulaAcademica()).isEqualTo("SUMA(defectos_totales)");
        assertThat(enBD.getUnidadResultado()).isEqualTo("defectos");
        assertThat(enBD.getFuenteAcademica()).isEqualTo("fuente test FASE 11");

        // 2) Ejecución encuentra la parametrización aprobada y calcula.
        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(
                METRICA_DEFECTOS,
                new EjecutarMetricaAcademicaRequest(proyectoId, sprintId, Map.of("defectos_totales", 7)));

        assertThat(resultado.resultado()).isEqualByComparingTo(new BigDecimal("7"));
        assertThat(resultado.unidad()).isEqualTo("defectos");
        assertThat(resultado.estado()).isEqualTo("calculado");
    }

    @Test
    void fat_formula_conservaCamposYCalculaTrasAprobarPorVerificacion() {
        guardarYAprobar(METRICA_FAT, "acat, acr", "FORMULA", "(ACAT / ACR) × 100", "%");

        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(
                METRICA_FAT,
                new EjecutarMetricaAcademicaRequest(proyectoId, sprintId, Map.of("acat", 8, "acr", 10)));

        assertThat(resultado.resultado()).isEqualByComparingTo(new BigDecimal("80.0000"));
        assertThat(resultado.unidad()).isEqualTo("%");
        assertThat(resultado.estado()).isEqualTo("calculado");
    }

    @Test
    void deudaTecnica_formula_conservaCamposYCalculaTrasAprobarPorVerificacion() {
        guardarYAprobar(METRICA_DEUDA, "deuda_gestionada, deuda_identificada", "FORMULA",
                "(deuda_gestionada / deuda_identificada) × 100", "%");

        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(
                METRICA_DEUDA,
                new EjecutarMetricaAcademicaRequest(proyectoId, sprintId,
                        Map.of("deuda_gestionada", 6, "deuda_identificada", 8)));

        assertThat(resultado.resultado()).isEqualByComparingTo(new BigDecimal("75.0000"));
        assertThat(resultado.unidad()).isEqualTo("%");
        assertThat(resultado.estado()).isEqualTo("calculado");
    }

    @Test
    void variables_quedanVinculadasAParametrizacionIdYVersion() {
        MetricParametrizacionDto guardado = guardarYAprobar(
                METRICA_FAT, "acat, acr", "FORMULA", "(ACAT / ACR) × 100", "%");

        var variables = variableRepo.findByParametrizacionIdAndParametrizacionVersion(
                guardado.id(), guardado.version());

        assertThat(variables).hasSize(2);
        assertThat(variables).extracting("nombre").containsExactlyInAnyOrder("acat", "acr");
        assertThat(variables).allMatch(v -> v.getActiva());
    }
}
