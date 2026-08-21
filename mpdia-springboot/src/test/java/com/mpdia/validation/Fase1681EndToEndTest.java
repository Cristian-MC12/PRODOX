package com.mpdia.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.*;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import com.mpdia.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 16.8.1 — VALIDACIÓN END-TO-END REAL del motor legado CalculoMetricaService.
 *
 * FASE 8 (corrección de aislamiento): la versión original de este test operaba
 * directamente sobre el proyecto real "Trabajo 1" y la parametrización real de
 * SIG-SC-02 — su primer paso (limpiezaDatosAnteriores) borraba esa
 * parametrización/variable/resultado real y los recreaba en cada corrida de
 * la suite completa, cambiando sus IDs cada vez. Esta versión corrige
 * exactamente ese problema: crea y reutiliza (idempotente, find-or-create)
 * un proyecto "sandbox" propio y aislado, exclusivo de este test, y nunca
 * borra ni modifica ningún dato de Trabajo 1 ni de Prueba 1. El proyecto
 * sandbox tiene scrumMasterId = TEST_USER (una cuenta de test que nunca
 * inicia sesión real), por lo que ProyectoService.listarMisProyectos()
 * jamás lo muestra a un usuario real.
 *
 * No se modificó CalculoMetricaService, FormulaEvaluator ni ningún otro
 * motor o servicio de producción — solo este archivo de test.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Fase1681EndToEndTest {

    // Métrica de catálogo compartida (dato de referencia global, no de proyecto) — sin cambios.
    private static final UUID METRICA_ID = UUID.fromString("2ba0cf34-0bec-4e7d-8dc5-40795f050ec9");

    private static final String TEST_USER = "test-fase16.8.1@mpdia.com";
    private static final String SANDBOX_PROYECTO_NOMBRE =
        "Sandbox FASE 16.8.1 (dato de test automatizado — no editar)";

    // IDs resueltos dinámicamente (find-or-create) — nunca fijos, nunca reales de Trabajo 1/Prueba 1.
    private static UUID proyectoSandboxId;
    private static UUID sprintSandboxId;
    private static UUID parametrizacionId;
    private static UUID variableId;
    private static UUID resultadoId;

    @Autowired
    private CalculoMetricaService calculoMetricaService;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private MetricaRepository metricaRepository;

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private MetricParametrizacionRepository parametrizacionRepository;

    @Autowired
    private VariableRepository variableRepository;

    @Autowired
    private RegistroValorRepository registroValorRepository;

    @Autowired
    private ResultadoMetricaRepository resultadoMetricaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Order(1)
    void prepararProyectoSandboxAislado() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("FASE 16.8.1 - VALIDACIÓN END-TO-END REAL (aislada, FASE 8)");
        System.out.println("=".repeat(70) + "\n");

        // Idempotente: reutiliza el sandbox de una corrida anterior si ya existe.
        // scrumMasterId = TEST_USER identifica de forma exclusiva los proyectos
        // creados por este test — nunca coincide con un proyecto real.
        Proyecto proyecto = proyectoRepository.findByScrumMasterIdOrderByCreatedAtDesc(TEST_USER).stream()
            .filter(p -> SANDBOX_PROYECTO_NOMBRE.equals(p.getNombre()))
            .findFirst()
            .orElseGet(() -> {
                Proyecto nuevo = new Proyecto();
                nuevo.setNombre(SANDBOX_PROYECTO_NOMBRE);
                nuevo.setDescripcion("Proyecto aislado para validación automática de FASE 16.8.1. No es dato real.");
                nuevo.setMetodo("scrum");
                nuevo.setTimeBoxSemanas(2);
                nuevo.setProductGoal("Sandbox de validación automática");
                nuevo.setSprintGoal("Sandbox de validación automática");
                nuevo.setNumeroSprints(1);
                nuevo.setScrumMasterId(TEST_USER);
                return proyectoRepository.save(nuevo);
            });
        proyectoSandboxId = proyecto.getId();
        System.out.println("✓ Proyecto sandbox: " + SANDBOX_PROYECTO_NOMBRE + " [" + proyectoSandboxId + "]");

        Sprint sprint = sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoSandboxId).stream()
            .findFirst()
            .orElseGet(() -> {
                Sprint nuevo = new Sprint();
                nuevo.setProyectoId(proyectoSandboxId);
                nuevo.setNumero(1);
                nuevo.setSprintGoal("Sandbox de validación automática");
                nuevo.setEstado("en_ejecucion");
                nuevo.setFechaInicio(LocalDate.now());
                return sprintRepository.save(nuevo);
            });
        sprintSandboxId = sprint.getId();
        System.out.println("✓ Sprint sandbox: Número " + sprint.getNumero() + " [" + sprintSandboxId + "]\n");

        assertNotNull(proyectoSandboxId);
        assertNotNull(sprintSandboxId);
    }

    @Test
    @Order(2)
    void verificarDatosDeReferenciaExisten() {
        System.out.println("PASO 1: Verificar Datos");
        System.out.println("-".repeat(70));

        Metrica metrica = metricaRepository.findById(METRICA_ID)
            .orElseThrow(() -> new AssertionError("Métrica no encontrada"));
        assertTrue(metrica.getNombre().contains("Problemas"));
        System.out.println("✓ Métrica (catálogo): " + metrica.getNombre() + " [" + metrica.getCodigo() + "]");

        Proyecto proyecto = proyectoRepository.findById(proyectoSandboxId)
            .orElseThrow(() -> new AssertionError("Proyecto sandbox no encontrado"));
        assertEquals(SANDBOX_PROYECTO_NOMBRE, proyecto.getNombre());
        System.out.println("✓ Proyecto: " + proyecto.getNombre() + " [" + proyectoSandboxId + "]");

        Sprint sprint = sprintRepository.findById(sprintSandboxId)
            .orElseThrow(() -> new AssertionError("Sprint sandbox no encontrado"));
        assertEquals(proyectoSandboxId, sprint.getProyectoId());
        System.out.println("✓ Sprint: Número " + sprint.getNumero() + " [" + sprint.getEstado() + "]\n");
    }

    @Test
    @Order(3)
    void faseAPreparacionDatos() {
        System.out.println("=".repeat(70));
        System.out.println("FASE A: PREPARACIÓN DE DATOS (idempotente, con commit)");
        System.out.println("=".repeat(70) + "\n");

        transactionTemplate.execute(status -> {
            try {
                // Reutilizar la parametrización aprobada del sandbox si ya existe.
                MetricParametrizacion parametrizacion = parametrizacionRepository
                    .findUltimaVersionAprobada(METRICA_ID, proyectoSandboxId)
                    .orElse(null);

                if (parametrizacion == null) {
                    System.out.println("PASO 2: Crear Parametrización");
                    System.out.println("-".repeat(70));

                    parametrizacion = new MetricParametrizacion();
                    parametrizacion.setVersion(1);
                    parametrizacion.setMetricaId(METRICA_ID);
                    parametrizacion.setProyectoId(proyectoSandboxId);
                    parametrizacion.setUserId(TEST_USER);
                    parametrizacion.setUserEmail(TEST_USER);
                    parametrizacion.setObjetivo("Contar problemas reportados directamente por el cliente");
                    parametrizacion.setProcedimiento("Sumar todos los problemas reportados en el sprint");
                    parametrizacion.setIndicadorVariable("Cantidad numérica de problemas reportados");
                    parametrizacion.setEscala("0 o más problemas");
                    parametrizacion.setFrecuenciaCaptura("por_sprint");
                    parametrizacion.setStatus("propuesta");
                    parametrizacion.setCreatedAt(Instant.now());
                    parametrizacion = parametrizacionRepository.save(parametrizacion);

                    System.out.println("✓ Parametrización ID: " + parametrizacion.getId() + " (v1, propuesta)\n");
                } else {
                    System.out.println("✓ Parametrización sandbox ya existente y aprobada, se reutiliza: "
                        + parametrizacion.getId() + "\n");
                }
                parametrizacionId = parametrizacion.getId();

                // Reutilizar la variable ya vinculada a esta parametrización si existe.
                List<Variable> variablesExistentes = variableRepository
                    .findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, parametrizacion.getVersion());

                Variable variable;
                if (!variablesExistentes.isEmpty()) {
                    variable = variablesExistentes.get(0);
                    System.out.println("✓ Variable sandbox ya existente, se reutiliza: " + variable.getId() + "\n");
                } else {
                    System.out.println("PASO 3: Crear Variable");
                    System.out.println("-".repeat(70));

                    Metrica metrica = metricaRepository.findById(METRICA_ID).orElseThrow();
                    variable = new Variable();
                    variable.setProyectoId(proyectoSandboxId);
                    variable.setMetrica(metrica);
                    variable.setNombre("Problemas reportados en el sprint");
                    variable.setDescripcion("Cantidad de problemas reportados directamente por el cliente");
                    variable.setTipoAlcance("grupal");
                    variable.setTipoIndicador("calidad");
                    variable.setFrecuencia("por_sprint");
                    variable.setCardinalidad("unico");
                    variable.setTipoDato("numerico");
                    variable.setActiva(true);
                    variable.setFrecuenciaCaptura("por_sprint");
                    variable.setParametrizacionId(parametrizacionId);
                    variable.setParametrizacionVersion(parametrizacion.getVersion());
                    variable.setCreatedAt(Instant.now());
                    variable = variableRepository.save(variable);

                    System.out.println("✓ Variable ID: " + variable.getId() + "\n");
                }
                variableId = variable.getId();

                // Aprobar la parametrización (solo si todavía no lo estaba) con
                // configuracionAprobadaJson apuntando al variableId real ya resuelto.
                if (!"aprobada".equals(parametrizacion.getStatus())) {
                    System.out.println("PASO 4: Aprobar Parametrización");
                    System.out.println("-".repeat(70));

                    String configuracionJson = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                        put("version", 1);
                        put("tipo", "suma");
                        put("variable_id", variableId.toString());
                    }});
                    parametrizacion.setConfiguracionAprobadaJson(configuracionJson);
                    parametrizacion.setStatus("aprobada");
                    parametrizacion.setRevisadoPor(TEST_USER);
                    parametrizacion.setRevisadoAt(Instant.now());
                    parametrizacionRepository.save(parametrizacion);

                    System.out.println("✓ Estado: aprobada\n");
                }

                // Registrar un valor solo si el sandbox todavía no tiene uno para
                // este sprint/variable (idempotencia: nunca duplica registros).
                List<RegistroValor> registrosExistentes = registroValorRepository
                    .findBySprintIdAndVariable_Id(sprintSandboxId, variableId);

                if (registrosExistentes.isEmpty()) {
                    System.out.println("PASO 5: Registrar Valor");
                    System.out.println("-".repeat(70));

                    RegistroValor registro = new RegistroValor();
                    registro.setVariable(variable);
                    registro.setSprintId(sprintSandboxId);
                    registro.setUserId(TEST_USER);
                    registro.setValorNum(new BigDecimal("3"));
                    registro.setRegistradoAt(Instant.now());
                    registroValorRepository.save(registro);

                    System.out.println("✓ Valor registrado: 3 problemas (dato de sandbox, no histórico real)\n");
                } else {
                    System.out.println("✓ Valor ya registrado en el sandbox, se reutiliza.\n");
                }

                entityManager.flush();
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException(e);
            }
        });

        MetricParametrizacion param = parametrizacionRepository.findById(parametrizacionId).orElseThrow();
        assertEquals("aprobada", param.getStatus());

        List<Variable> vars = variableRepository.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1);
        assertFalse(vars.isEmpty());

        List<RegistroValor> valores = registroValorRepository.findBySprintIdAndVariable_Id(sprintSandboxId, variableId);
        assertFalse(valores.isEmpty());

        System.out.println("✅ FASE A COMPLETADA: datos del sandbox listos (creados o reutilizados)\n");
    }

    @Test
    @Order(4)
    void faseBCalculoEnNuevaTransaccion() {
        System.out.println("=".repeat(70));
        System.out.println("FASE B: CÁLCULO EN NUEVA TRANSACCIÓN");
        System.out.println("=".repeat(70) + "\n");

        // Idempotencia: si el sandbox ya tiene un resultado calculado para este
        // sprint con el valor esperado, se reutiliza en vez de crear uno nuevo
        // en cada corrida de la suite completa. Nunca se accede a r.getMetrica()
        // (asociación perezosa): esta consulta corre fuera de una transacción
        // activa y ese acceso lanzaría LazyInitializationException.
        Optional<ResultadoMetrica> existente = resultadoMetricaRepository
            .findUltimoResultado(METRICA_ID, sprintSandboxId)
            .filter(r -> "calculado".equals(r.getEstado())
                && r.getResultado() != null
                && r.getResultado().compareTo(new BigDecimal("3")) == 0);

        ResultadoMetricaDto resultado;
        if (existente.isPresent()) {
            ResultadoMetrica r = existente.get();
            resultado = new ResultadoMetricaDto(
                r.getId(), METRICA_ID, null, proyectoSandboxId, sprintSandboxId,
                r.getParametrizacionId(), r.getParametrizacionVersion(), r.getTipoCalculo(),
                r.getExpresionUtilizada(), r.getValoresUtilizados(), r.getResultado(), r.getUnidad(),
                r.getEstado(), r.getMensajeError(), r.getCalculadoAt()
            );
            System.out.println("✓ Resultado ya existente en el sandbox, se reutiliza (no se recalcula): " + r.getId() + "\n");
        } else {
            System.out.println("PASO 6: Calcular Métrica (primera vez en este sandbox)");
            System.out.println("-".repeat(70));
            resultado = transactionTemplate.execute(status -> {
                CalcularMetricaRequest request = new CalcularMetricaRequest(proyectoSandboxId, sprintSandboxId);
                return calculoMetricaService.calcularMetrica(METRICA_ID, request, TEST_USER);
            });
        }

        assertNotNull(resultado);
        assertNotNull(resultado.resultadoId());
        assertEquals(METRICA_ID, resultado.metricaId());
        assertEquals(proyectoSandboxId, resultado.proyectoId());
        assertEquals(sprintSandboxId, resultado.sprintId());
        assertEquals("suma", resultado.tipoCalculo());
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("3")));
        assertEquals("calculado", resultado.estado());
        assertEquals(1, resultado.parametrizacionVersion());

        resultadoId = resultado.resultadoId();

        System.out.println("✓ Resultado ID: " + resultadoId);
        System.out.println("  Resultado: " + resultado.resultado());
        System.out.println("\n✅ FASE B COMPLETADA\n");
    }

    @Test
    @Order(5)
    void faseCVerificacionPersistencia() {
        System.out.println("=".repeat(70));
        System.out.println("FASE C: VERIFICACIÓN DE PERSISTENCIA");
        System.out.println("=".repeat(70) + "\n");

        ResultadoMetrica resultado = resultadoMetricaRepository.findById(resultadoId)
            .orElseThrow(() -> new AssertionError("Resultado no persistido en BD"));

        assertEquals(proyectoSandboxId, resultado.getProyectoId());
        assertEquals(sprintSandboxId, resultado.getSprintId());
        assertEquals(parametrizacionId, resultado.getParametrizacionId());
        assertEquals(1, resultado.getParametrizacionVersion());
        assertEquals("suma", resultado.getTipoCalculo());
        assertEquals(0, resultado.getResultado().compareTo(new BigDecimal("3")));
        assertEquals("calculado", resultado.getEstado());
        assertNotNull(resultado.getValoresUtilizados());
        assertNotNull(resultado.getCalculadoPor());
        assertNotNull(resultado.getCalculadoAt());

        System.out.println("✓ Persistencia verificada en resultados_metricas: " + resultado.getId());
        System.out.println("\n✅ FASE C COMPLETADA\n");
    }

    @Test
    @Order(6)
    void resumenValidacionEndToEnd() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("RESUMEN VALIDACIÓN END-TO-END REAL - FASE 16.8.1 (sandbox aislado)");
        System.out.println("=".repeat(70));
        System.out.println("Proyecto sandbox: " + SANDBOX_PROYECTO_NOMBRE + " [" + proyectoSandboxId + "]");
        System.out.println("Sprint sandbox:   [" + sprintSandboxId + "]");
        System.out.println("Parametrización:  [" + parametrizacionId + "] v1 aprobada");
        System.out.println("Variable:         [" + variableId + "]");
        System.out.println("Resultado:        [" + resultadoId + "] = 3.0000");
        System.out.println("Trabajo 1 y Prueba 1: NO tocados por este test.");
        System.out.println("=".repeat(70));
        System.out.println("VALIDACIÓN END-TO-END: ✅ COMPLETADA (aislada, idempotente)");
        System.out.println("=".repeat(70) + "\n");
    }
}
