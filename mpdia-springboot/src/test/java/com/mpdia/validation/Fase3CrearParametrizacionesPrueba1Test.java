// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.dto.AprobarParametrizacionRequest;
import com.mpdia.dto.GuardarPropuestaRequest;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.Variable;
import com.mpdia.repository.VariableRepository;
import com.mpdia.service.ParametrizacionService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 3 (Defectos / Aprendizaje organizacional (FAT) / Deuda técnica
 * gestionada): creación real, mediante el flujo de producción
 * ParametrizacionService.guardarPropuesta()/aprobarParametrizacion() (el
 * mismo que usa ParametrizacionController), de las parametrizaciones v1
 * aprobadas para estas 3 métricas oficiales pendientes en el proyecto
 * Prueba 1.
 *
 * NO toca Trabajo 1. Idempotente: si al ejecutar ya existe una
 * parametrización para (proyecto, métrica), se omite su creación en vez de
 * generar una versión adicional — permite re-ejecutar este test sin efectos
 * destructivos ni colisiones de versión.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Fase3CrearParametrizacionesPrueba1Test {

    private static final UUID PRUEBA1 = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");
    private static final UUID TRABAJO1 = UUID.fromString("fce0340c-74f2-4219-a727-5bae4d842496");

    private static final UUID METRICA_DEFECTOS = UUID.fromString("ec0d74fe-0bf4-4970-af89-dcaa0736c8ed");
    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");
    private static final UUID METRICA_DEUDA = UUID.fromString("40beffdf-13f4-4772-8820-4df93fae525c");

    // Único miembro real de Prueba 1 (project_members.user_id) — necesario
    // para pasar validarMiembroProyecto() del flujo real de producción.
    private static final String USER_ID_PRUEBA1 = "8227f530-b7d8-4af5-b429-f464cabe0594";

    @Autowired
    private ParametrizacionService parametrizacionService;

    @Autowired
    private VariableRepository variableRepository;

    private void autenticarComoMiembroPrueba1() {
        Authentication auth = new UsernamePasswordAuthenticationToken(USER_ID_PRUEBA1, null, List.of());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private MetricParametrizacion crearYAprobar(GuardarPropuestaRequest propuesta) {
        MetricParametrizacion guardada = parametrizacionService.guardarPropuesta(propuesta);
        assertEquals("propuesta", guardada.getStatus());
        assertEquals(1, guardada.getVersion());

        AprobarParametrizacionRequest aprobar = new AprobarParametrizacionRequest(
            propuesta.objetivo(), propuesta.procedimiento(), propuesta.indicadorVariable(),
            propuesta.escala(), propuesta.frecuenciaCaptura(), propuesta.fuenteAcademica(),
            propuesta.formulaAcademica(), propuesta.tipoOperacion(), propuesta.unidadResultado(),
            propuesta.nombreVariable()
        , null, null, null, null, null, null);

        return parametrizacionService.aprobarParametrizacion(guardada.getId(), aprobar);
    }

    @Test
    @Order(1)
    void defectos_v1_seCreaYApruebaConUnaVariable() {
        autenticarComoMiembroPrueba1();

        List<MetricParametrizacion> historial =
            parametrizacionService.obtenerHistorialVersiones(METRICA_DEFECTOS, PRUEBA1);
        if (!historial.isEmpty()) {
            System.out.println("Defectos ya tiene parametrización en Prueba 1 (v"
                + historial.get(0).getVersion() + "), se omite creación.");
            return;
        }

        GuardarPropuestaRequest propuesta = new GuardarPropuestaRequest(
            METRICA_DEFECTOS, PRUEBA1,
            "Medir el total de defectos detectados durante el sprint",
            "Al cierre del sprint, sumar todos los defectos_totales registrados",
            "defectos_totales (conteo de defectos detectados en el sprint)",
            "Numérica entera >= 0",
            "por_sprint",
            "Decisión de proyecto (FASE 3) — SUMA(defectos_totales), consistente con "
                + "SIG-CE-02 (docs/FASE16_8_7_ESPECIFICACION_METODOLOGICA.md). "
                + "NO es una fórmula citada textualmente de Hernández.",
            "SUMA(defectos_totales)",
            "SUMA",
            "defectos",
            null,
            "defectos_totales"
        , null, null, null, null, null, null);

        MetricParametrizacion aprobada = crearYAprobar(propuesta);

        assertEquals("aprobada", aprobada.getStatus());
        assertEquals(1, aprobada.getVersion());
        assertEquals(METRICA_DEFECTOS, aprobada.getMetricaId());
        assertEquals(PRUEBA1, aprobada.getProyectoId());
        assertEquals("SUMA", aprobada.getTipoOperacion());
        assertEquals("SUMA(defectos_totales)", aprobada.getFormulaAcademica());
        assertEquals("defectos", aprobada.getUnidadResultado());

        List<Variable> vars = variableRepository
            .findByParametrizacionIdAndParametrizacionVersion(aprobada.getId(), 1);
        assertEquals(1, vars.size());
        assertEquals("defectos_totales", vars.get(0).getNombre());
        assertEquals(PRUEBA1, vars.get(0).getProyectoId());
    }

    @Test
    @Order(2)
    void fat_v1_seCreaYApruebaConDosVariables() {
        autenticarComoMiembroPrueba1();

        List<MetricParametrizacion> historial =
            parametrizacionService.obtenerHistorialVersiones(METRICA_FAT, PRUEBA1);
        if (!historial.isEmpty()) {
            System.out.println("FAT ya tiene parametrización en Prueba 1 (v"
                + historial.get(0).getVersion() + "), se omite creación.");
            return;
        }

        GuardarPropuestaRequest propuesta = new GuardarPropuestaRequest(
            METRICA_FAT, PRUEBA1,
            "Medir el porcentaje de actividades de capacitación aplicadas al trabajo "
                + "respecto a las realizadas durante el sprint",
            "Al cierre del sprint, capturar acat (actividades de capacitación aplicadas "
                + "al trabajo) y acr (actividades de capacitación realizadas); el motor "
                + "calcula (acat / acr) × 100",
            "ACAT = Actividades de capacitación aplicadas al trabajo; "
                + "ACR = Actividades de capacitación realizadas "
                + "(capturadas técnicamente como acat y acr)",
            "Numérica entera >= 0 para acat y acr",
            "por_sprint",
            "Hernández (2024), p.14, Tabla 10, ecuación (9) — reclasificada de "
                + "NO CALCULABLE a FORMULA por decisión de proyecto (FASE 3, "
                + "docs/FASE16_8_7_ESPECIFICACION_METODOLOGICA.md, FLX-FAT-01). "
                + "Nombres técnicos (acat, acr) fijados por decisión de proyecto.",
            "(ACAT / ACR) × 100",
            "FORMULA",
            "%",
            null,
            "acat,acr"
        , null, null, null, null, null, null);

        MetricParametrizacion aprobada = crearYAprobar(propuesta);

        assertEquals("aprobada", aprobada.getStatus());
        assertEquals(1, aprobada.getVersion());
        assertEquals(METRICA_FAT, aprobada.getMetricaId());
        assertEquals(PRUEBA1, aprobada.getProyectoId());
        assertEquals("FORMULA", aprobada.getTipoOperacion());
        assertEquals("(ACAT / ACR) × 100", aprobada.getFormulaAcademica());
        assertEquals("%", aprobada.getUnidadResultado());

        List<Variable> vars = variableRepository
            .findByParametrizacionIdAndParametrizacionVersion(aprobada.getId(), 1);
        assertEquals(2, vars.size());
        assertEquals(
            List.of("acat", "acr"),
            vars.stream().map(Variable::getNombre).sorted().toList()
        );
        assertTrue(vars.stream().allMatch(v -> PRUEBA1.equals(v.getProyectoId())));
    }

    @Test
    @Order(3)
    void deudaTecnica_v1_seCreaYApruebaConDosVariables() {
        autenticarComoMiembroPrueba1();

        List<MetricParametrizacion> historial =
            parametrizacionService.obtenerHistorialVersiones(METRICA_DEUDA, PRUEBA1);
        if (!historial.isEmpty()) {
            System.out.println("Deuda técnica ya tiene parametrización en Prueba 1 (v"
                + historial.get(0).getVersion() + "), se omite creación.");
            return;
        }

        GuardarPropuestaRequest propuesta = new GuardarPropuestaRequest(
            METRICA_DEUDA, PRUEBA1,
            "Medir el porcentaje de deuda técnica gestionada respecto a la identificada "
                + "durante el sprint",
            "Al cierre del sprint, capturar deuda_gestionada y deuda_identificada; el "
                + "motor calcula (deuda_gestionada / deuda_identificada) × 100",
            "deuda_gestionada = elementos de deuda técnica gestionados en el sprint; "
                + "deuda_identificada = elementos de deuda técnica identificados en el sprint",
            "Numérica entera >= 0 para ambas variables",
            "por_sprint",
            "Adaptación MPDIA — NO respaldada por el artículo de Hernández "
                + "(docs/FASE16_8_7_ESPECIFICACION_METODOLOGICA.md, FLX-GAE-02: "
                + "'NO CONFIRMADA en artículo'). Fórmula y unidad adoptadas como "
                + "decisión de proyecto (FASE 3).",
            "(deuda_gestionada / deuda_identificada) × 100",
            "FORMULA",
            "%",
            null,
            "deuda_gestionada,deuda_identificada"
        , null, null, null, null, null, null);

        MetricParametrizacion aprobada = crearYAprobar(propuesta);

        assertEquals("aprobada", aprobada.getStatus());
        assertEquals(1, aprobada.getVersion());
        assertEquals(METRICA_DEUDA, aprobada.getMetricaId());
        assertEquals(PRUEBA1, aprobada.getProyectoId());
        assertEquals("FORMULA", aprobada.getTipoOperacion());
        assertEquals("(deuda_gestionada / deuda_identificada) × 100", aprobada.getFormulaAcademica());
        assertEquals("%", aprobada.getUnidadResultado());

        List<Variable> vars = variableRepository
            .findByParametrizacionIdAndParametrizacionVersion(aprobada.getId(), 1);
        assertEquals(2, vars.size());
        assertEquals(
            List.of("deuda_gestionada", "deuda_identificada"),
            vars.stream().map(Variable::getNombre).sorted().toList()
        );
        assertTrue(vars.stream().allMatch(v -> PRUEBA1.equals(v.getProyectoId())));
    }

    @Test
    @Order(4)
    void trabajo1_noFueModificado() {
        // Snapshot confirmado por SQL antes de esta fase: v1 aprobada para las
        // 3 métricas, sin tipo_operacion/formula/unidad (esquema legado). Debe
        // seguir exactamente igual después de crear las parametrizaciones de
        // Prueba 1 — ambos proyectos son completamente independientes.
        for (UUID metricaId : List.of(METRICA_DEFECTOS, METRICA_FAT, METRICA_DEUDA)) {
            List<MetricParametrizacion> historial =
                parametrizacionService.obtenerHistorialVersiones(metricaId, TRABAJO1);
            assertEquals(1, historial.size(), "Trabajo 1 debe seguir con exactamente 1 versión");
            assertEquals(1, historial.get(0).getVersion());
            assertEquals("aprobada", historial.get(0).getStatus());
            assertNull(historial.get(0).getTipoOperacion(),
                "tipoOperacion de Trabajo 1 debe seguir null (esquema legado, no tocado)");
        }
    }
}
