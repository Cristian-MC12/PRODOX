// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.dto.EjecutarMetricaAcademicaRequest;
import com.mpdia.dto.ResultadoMetricaDto;
import com.mpdia.entity.ResultadoMetrica;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.ResultadoMetricaRepository;
import com.mpdia.service.MetricaAcademicaService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 4: Ejecución E2E real, mediante MetricaAcademicaService.ejecutarMetricaAcademica()
 * (el mismo camino que usaría el controller de ejecución), de las 3 parametrizaciones
 * v1 aprobadas en Prueba 1 creadas en FASE 3: Defectos (SUMA), Aprendizaje
 * organizacional / FAT (FORMULA) y Deuda técnica gestionada (FORMULA).
 *
 * NO toca Impedimentos por sprint ni Problemas reportados por el cliente
 * (ya validadas E2E previamente). NO toca Trabajo 1. NO usa el motor legado
 * (CalculoMetricaService) — se invoca exclusivamente el servicio académico
 * actual, que resuelve FORMULA vía FormulaEvaluator.
 *
 * Idempotente: si ya existe un resultado calculado para (métrica, sprint) con
 * el valor esperado, se omite la re-ejecución en vez de acumular filas nuevas
 * en resultados_metricas en cada corrida de la suite completa.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Fase4EjecucionE2EPrueba1Test {

    private static final UUID PRUEBA1 = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");
    private static final UUID SPRINT1_PRUEBA1 = UUID.fromString("589c1f90-440c-4331-ae34-345c37405b5e");

    private static final UUID METRICA_DEFECTOS = UUID.fromString("ec0d74fe-0bf4-4970-af89-dcaa0736c8ed");
    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");
    private static final UUID METRICA_DEUDA = UUID.fromString("40beffdf-13f4-4772-8820-4df93fae525c");

    // IDs de variable v1 de Prueba 1 confirmados por SELECT previo a la ejecución
    // (ver reporte de FASE 4) — usados solo para verificación directa de
    // registro_valores, nunca para ejecutar (la ejecución real resuelve las
    // variables por nombre técnico dentro de MetricaAcademicaService).
    private static final UUID VARIABLE_DEFECTOS_TOTALES = UUID.fromString("d6cb4885-6018-47d2-b5f2-dcf25f55de9f");

    private static final String USER_ID_PRUEBA1 = "8227f530-b7d8-4af5-b429-f464cabe0594";

    @Autowired
    private MetricaAcademicaService metricaAcademicaService;

    @Autowired
    private RegistroValorRepository registroValorRepository;

    @Autowired
    private ResultadoMetricaRepository resultadoMetricaRepository;

    private void autenticarComoMiembroPrueba1() {
        Authentication auth = new UsernamePasswordAuthenticationToken(USER_ID_PRUEBA1, null, List.of());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    /**
     * Guard de idempotencia. Consulta ResultadoMetricaRepository directamente
     * (método existente, findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc)
     * en vez de MetricaAcademicaService.obtenerHistorico(): esta última
     * construye un DTO accediendo a resultado.getMetrica().getNombre(), una
     * asociación @ManyToOne de carga perezosa que solo se resuelve con éxito
     * dentro de una transacción/petición HTTP activa (Open Session In View).
     * Este test no corre dentro de ninguna de las dos, así que ese acceso
     * lanzaba LazyInitializationException en cuanto ya existía al menos un
     * resultado real que iterar. Aquí NUNCA se toca resultado.getMetrica():
     * solo se leen columnas propias de ResultadoMetrica (sprintId, estado,
     * resultado, parametrizacionVersion), que no requieren sesión activa.
     */
    private boolean yaExisteResultadoEsperado(UUID metricaId, BigDecimal esperado) {
        List<ResultadoMetrica> historico = resultadoMetricaRepository
            .findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(metricaId, PRUEBA1);
        return historico.stream()
            .anyMatch(r -> SPRINT1_PRUEBA1.equals(r.getSprintId())
                && "calculado".equals(r.getEstado())
                && r.getParametrizacionVersion() != null && r.getParametrizacionVersion() == 1
                && r.getResultado() != null && r.getResultado().compareTo(esperado) == 0);
    }

    @Test
    @Order(1)
    void defectos_ejecucionE2E_sumaCorrectamente() {
        autenticarComoMiembroPrueba1();
        if (yaExisteResultadoEsperado(METRICA_DEFECTOS, new BigDecimal("5"))) {
            System.out.println("Defectos ya tiene un resultado calculado=5 para Sprint 1 en Prueba 1, se omite re-ejecución.");
            return;
        }

        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            PRUEBA1, SPRINT1_PRUEBA1, Map.of("defectos_totales", 5)
        );

        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(METRICA_DEFECTOS, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("5")));
        assertEquals("defectos", resultado.unidad());
        assertEquals("suma", resultado.tipoCalculo());
        assertEquals("SUMA(defectos_totales)", resultado.expresion());
        assertEquals(1, resultado.parametrizacionVersion());
        assertEquals("calculado", resultado.estado());

        List<com.mpdia.entity.RegistroValor> registros = registroValorRepository
            .findBySprintIdAndVariable_Id(SPRINT1_PRUEBA1, VARIABLE_DEFECTOS_TOTALES);
        assertEquals(1, registros.size());
        assertEquals(0, registros.get(0).getValorNum().compareTo(new BigDecimal("5")));
    }

    @Test
    @Order(2)
    void fat_ejecucionE2E_formulaCalculaCorrectamenteYPreservaTrazabilidad() {
        autenticarComoMiembroPrueba1();
        if (yaExisteResultadoEsperado(METRICA_FAT, new BigDecimal("80.0000"))) {
            System.out.println("FAT ya tiene un resultado calculado=80.0000 para Sprint 1 en Prueba 1, se omite re-ejecución.");
            return;
        }

        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            PRUEBA1, SPRINT1_PRUEBA1, Map.of("acat", 8, "acr", 10)
        );

        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(METRICA_FAT, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("80.0000")));
        assertEquals("%", resultado.unidad());
        // tipoCalculo="formula" prueba que se usó el case FORMULA (no SUMA/PROMEDIO/DIRECTO)
        // de MetricaAcademicaService.calcularSegunTipo(), es decir, el motor
        // FormulaEvaluator — nunca CalculoMetricaService (motor legado, que
        // etiqueta con tipoCalculo="formula" también pero opera sobre un
        // esquema configuracionAprobadaJson distinto que esta parametrización
        // nunca tuvo poblado: no fue creada por ese camino).
        assertEquals("formula", resultado.tipoCalculo());
        // expresion_utilizada conserva el texto académico original íntegro,
        // con "×" sin normalizar — la normalización a "*" es solo interna.
        assertEquals("(ACAT / ACR) × 100", resultado.expresion());
        assertEquals(1, resultado.parametrizacionVersion());
        assertEquals("calculado", resultado.estado());
    }

    @Test
    @Order(3)
    void deudaTecnica_ejecucionE2E_formulaCalculaCorrectamenteYPreservaTrazabilidad() {
        autenticarComoMiembroPrueba1();
        if (yaExisteResultadoEsperado(METRICA_DEUDA, new BigDecimal("75.0000"))) {
            System.out.println("Deuda técnica ya tiene un resultado calculado=75.0000 para Sprint 1 en Prueba 1, se omite re-ejecución.");
            return;
        }

        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            PRUEBA1, SPRINT1_PRUEBA1, Map.of("deuda_gestionada", 6, "deuda_identificada", 8)
        );

        ResultadoMetricaDto resultado = metricaAcademicaService.ejecutarMetricaAcademica(METRICA_DEUDA, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("75.0000")));
        assertEquals("%", resultado.unidad());
        assertEquals("formula", resultado.tipoCalculo());
        assertEquals("(deuda_gestionada / deuda_identificada) × 100", resultado.expresion());
        assertEquals(1, resultado.parametrizacionVersion());
        assertEquals("calculado", resultado.estado());
    }
}
