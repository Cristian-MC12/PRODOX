package com.mpdia.validation;

import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.Variable;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.VariableRepository;
import com.mpdia.service.PlaneacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FASE 16.10 — Validación de V26 (ux_variables_proyecto_metrica_planeacion /
 * ux_variables_parametrizacion_version_nombre).
 *
 * Usa datos reales existentes (SIG-VEL-01, Prueba 1, Trabajo 1) para no
 * necesitar fixtures nuevas, pero NUNCA toca las variables/parametrizaciones
 * reales del piloto SIG-SC-02. Toda la clase corre dentro de una transacción
 * que se revierte automáticamente al final de cada test (@Transactional de
 * Spring Test), así que no persiste ningún dato.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VariablesUnicidadV26Test {

    // Prueba 1
    private static final UUID PROYECTO_1 = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");
    // Trabajo 1
    private static final UUID PROYECTO_2 = UUID.fromString("fce0340c-74f2-4219-a727-5bae4d842496");
    // SIG-VEL-01 (Velocidad) — distinta de SIG-SC-02, para no tocar los datos del piloto
    private static final UUID METRICA_VEL = UUID.fromString("d0006325-a144-489f-b09c-e51b3e87dfa1");

    @Autowired
    private VariableRepository variableRepo;

    @Autowired
    private MetricParametrizacionRepository parametrizacionRepo;

    @Autowired
    private MetricaRepository metricaRepo;

    @Autowired
    private PlaneacionService planeacionService;

    private Metrica metricaVel() {
        return metricaRepo.findById(METRICA_VEL).orElseThrow();
    }

    private Variable nuevaVariableSistemaA(UUID proyectoId, String nombre) {
        Variable v = new Variable();
        v.setProyectoId(proyectoId);
        v.setMetrica(metricaVel());
        v.setNombre(nombre);
        v.setActiva(true);
        return v;
    }

    private MetricParametrizacion nuevaParametrizacion(UUID proyectoId, Integer version, String userId) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setProyectoId(proyectoId);
        p.setMetricaId(METRICA_VEL);
        p.setVersion(version);
        p.setUserId(userId);
        p.setUserEmail(userId + "@test.mpdia.com");
        p.setObjetivo("Objetivo de prueba V26");
        p.setProcedimiento("Procedimiento de prueba V26");
        p.setIndicadorVariable("indicador_prueba_v26");
        p.setEscala("Numérica");
        p.setStatus("propuesta");
        p.setCreatedAt(Instant.now());
        return parametrizacionRepo.saveAndFlush(p);
    }

    private Variable nuevaVariableSistemaB(UUID proyectoId, UUID parametrizacionId, Integer version, String nombre) {
        Variable v = new Variable();
        v.setProyectoId(proyectoId);
        v.setMetrica(metricaVel());
        v.setNombre(nombre);
        v.setActiva(true);
        v.setParametrizacionId(parametrizacionId);
        v.setParametrizacionVersion(version);
        return v;
    }

    @Test
    void A_sistemaA_mismoProyectoMetrica_rechazaDuplicado() {
        variableRepo.saveAndFlush(nuevaVariableSistemaA(PROYECTO_1, "variable-a1-" + UUID.randomUUID()));

        assertThatThrownBy(() ->
            variableRepo.saveAndFlush(nuevaVariableSistemaA(PROYECTO_1, "variable-a2-" + UUID.randomUUID()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void B_sistemaB_mismaParametrizacionVersionNombre_rechazaDuplicado() {
        MetricParametrizacion param = nuevaParametrizacion(PROYECTO_1, 900, "user-b-" + UUID.randomUUID());
        String nombreCompartido = "problema_reportado_validado";

        variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, param.getId(), 900, nombreCompartido));

        assertThatThrownBy(() ->
            variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, param.getId(), 900, nombreCompartido))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void C_sistemaB_mismaParametrizacionVersion_nombresDistintos_permiteMultiples() {
        MetricParametrizacion param = nuevaParametrizacion(PROYECTO_1, 901, "user-c-" + UUID.randomUUID());

        Variable v1 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, param.getId(), 901, "variable_uno"));
        Variable v2 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, param.getId(), 901, "variable_dos"));

        assertThat(v1.getId()).isNotEqualTo(v2.getId());
        List<Variable> encontradas = variableRepo.findByParametrizacionIdAndParametrizacionVersion(param.getId(), 901);
        assertThat(encontradas).hasSize(2)
            .extracting(Variable::getNombre)
            .containsExactlyInAnyOrder("variable_uno", "variable_dos");
    }

    @Test
    void D_versionesDistintas_v2yV3_permiteVariablesDistintasParaMismoProyectoMetrica() {
        String userId = "user-d-" + UUID.randomUUID();
        MetricParametrizacion v2 = nuevaParametrizacion(PROYECTO_1, 902, userId);
        MetricParametrizacion v3 = nuevaParametrizacion(PROYECTO_1, 903, userId);
        String mismoNombre = "problema_reportado_individual";

        // Reproduce el caso real que fallaba en producción: misma (proyecto, métrica),
        // mismo nombre de variable, pero de dos versiones sucesivas de parametrización.
        Variable variableV2 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, v2.getId(), 902, mismoNombre));
        Variable variableV3 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, v3.getId(), 903, mismoNombre));

        assertThat(variableV2.getId()).isNotEqualTo(variableV3.getId());
        assertThat(variableRepo.findByParametrizacionIdAndParametrizacionVersion(v2.getId(), 902))
            .extracting(Variable::getId).containsExactly(variableV2.getId());
        assertThat(variableRepo.findByParametrizacionIdAndParametrizacionVersion(v3.getId(), 903))
            .extracting(Variable::getId).containsExactly(variableV3.getId());
    }

    @Test
    void E_proyectosDistintos_mismaMetricaVersion_permiteVariablesIndependientes() {
        MetricParametrizacion enProyecto1 = nuevaParametrizacion(PROYECTO_1, 904, "user-e1-" + UUID.randomUUID());
        MetricParametrizacion enProyecto2 = nuevaParametrizacion(PROYECTO_2, 904, "user-e2-" + UUID.randomUUID());
        String mismoNombre = "variable_compartida_e";

        Variable variableP1 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, enProyecto1.getId(), 904, mismoNombre));
        Variable variableP2 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_2, enProyecto2.getId(), 904, mismoNombre));

        assertThat(variableP1.getId()).isNotEqualTo(variableP2.getId());
        assertThat(variableRepo.findByParametrizacionIdAndParametrizacionVersion(enProyecto1.getId(), 904))
            .extracting(Variable::getId).containsExactly(variableP1.getId());
        assertThat(variableRepo.findByParametrizacionIdAndParametrizacionVersion(enProyecto2.getId(), 904))
            .extracting(Variable::getId).containsExactly(variableP2.getId());
    }

    @Test
    void F_sistemaA_flujoDePlaneacion_siguefuncionandoSinRegresion() {
        String aprobadaPor = "user-f-" + UUID.randomUUID();

        // Camino real de PlaneacionService.aprobar(): crea ProyectoMetrica +
        // genera la variable de catálogo (parametrizacion_id = NULL).
        var dto = planeacionService.aprobar(PROYECTO_1, METRICA_VEL, aprobadaPor);
        assertThat(dto).isNotNull();
        assertThat(variableRepo.existsByProyectoIdAndMetrica_Id(PROYECTO_1, METRICA_VEL)).isTrue();

        // Repetir aprobar() no debe duplicar ni violar el índice nuevo
        // (usa existsByProyectoIdAndMetrica_Id antes de generar).
        var dtoRepetido = planeacionService.aprobar(PROYECTO_1, METRICA_VEL, aprobadaPor);
        assertThat(dtoRepetido.id()).isEqualTo(dto.id());

        // desaprobar() sigue encontrando y desactivando la variable de catálogo.
        planeacionService.desaprobar(PROYECTO_1, METRICA_VEL);
        Variable variable = variableRepo.findByProyectoIdAndMetrica_Id(PROYECTO_1, METRICA_VEL).orElseThrow();
        assertThat(variable.getActiva()).isFalse();
        assertThat(variable.getParametrizacionId()).isNull();
    }

    @Test
    void G_busquedaPorParametrizacionVersion_aislaCorrectamenteLasVariablesDeCadaUna() {
        MetricParametrizacion paramX = nuevaParametrizacion(PROYECTO_1, 905, "user-g1-" + UUID.randomUUID());
        MetricParametrizacion paramY = nuevaParametrizacion(PROYECTO_2, 905, "user-g2-" + UUID.randomUUID());

        // X tiene 2 variables (como usan VariableDinamicaService/MetricaAcademicaService
        // al materializar todas las variables de una parametrización aprobada).
        Variable x1 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, paramX.getId(), 905, "var_x_uno"));
        Variable x2 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_1, paramX.getId(), 905, "var_x_dos"));
        // Y tiene 1 variable, con el MISMO número de versión pero distinta parametrización.
        Variable y1 = variableRepo.saveAndFlush(nuevaVariableSistemaB(PROYECTO_2, paramY.getId(), 905, "var_y_uno"));

        List<Variable> deX = variableRepo.findByParametrizacionIdAndParametrizacionVersion(paramX.getId(), 905);
        assertThat(deX).extracting(Variable::getId)
            .containsExactlyInAnyOrder(x1.getId(), x2.getId());

        List<Variable> deY = variableRepo.findByParametrizacionIdAndParametrizacionVersion(paramY.getId(), 905);
        assertThat(deY).extracting(Variable::getId).containsExactly(y1.getId());
    }
}
