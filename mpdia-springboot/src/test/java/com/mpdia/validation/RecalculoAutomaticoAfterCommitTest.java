// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.dto.CalcularMetricaRequest;
import com.mpdia.dto.ResultadoMetricaDto;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import com.mpdia.service.CalculoMetricaService;
import com.mpdia.service.EjecucionService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validación end-to-end real (base de datos real, transacciones reales con
 * COMMIT de verdad) de la corrección del bug de visibilidad transaccional en
 * el recálculo automático de métricas EQUIPO.
 *
 * CAUSA RAÍZ CORREGIDA: EjecucionService.guardarOActualizarValor() guardaba el
 * registro y disparaba CalculoMetricaService.recalcularSilenciosamente()
 * (anotado @Transactional(REQUIRES_NEW)) TODAVÍA dentro de la transacción
 * externa de la captura, que aún no había hecho COMMIT. Esa transacción nueva
 * e independiente, bajo READ COMMITTED, nunca podía ver el registro que la
 * transacción externa todavía no había confirmado — así que cada recálculo
 * automático excluía sistemáticamente el valor de quien lo disparaba (ej.
 * A=22 + B=12 + C=25 terminaba en 34, no 59). La corrección difiere el
 * disparo a un TransactionSynchronization.afterCommit(), de forma que el
 * recálculo solo corre después de que la captura ya esté confirmada.
 *
 * Cada @Test llama a EjecucionService.guardarOActualizarValor() directamente
 * desde un método SIN @Transactional propio — Spring abre y confirma una
 * transacción real e independiente en cada llamada (igual que si fueran 3
 * peticiones HTTP separadas), exactamente el escenario que exponía el bug
 * original y que una prueba con Mockito no puede reproducir de verdad.
 *
 * Aislado en su propio proyecto sandbox (scrumMasterId ficticio, nunca
 * coincide con una cuenta real) — no toca Trabajo 1, Prueba 1 ni ningún otro
 * dato real. Idempotente: reutiliza sus propios datos si ya existen.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecalculoAutomaticoAfterCommitTest {

    private static final String TEST_USER = "test-recalculo-aftercommit@mpdia.com";
    private static final String SANDBOX_PROYECTO_NOMBRE =
        "Sandbox Recalculo AfterCommit (dato de test automatizado — no editar)";

    // Métricas de catálogo compartidas (dato de referencia global, no de proyecto) — reutilizadas
    // sin modificarlas; la parametrización real vive en este proyecto sandbox, aislada de
    // cualquier otro proyecto que también las use.
    private static final UUID METRICA_SUMA = UUID.fromString("ec0d74fe-0bf4-4970-af89-dcaa0736c8ed"); // "Defectos"
    private static final UUID METRICA_PROMEDIO = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3"); // "FAT"

    private static UUID proyectoId;
    private static UUID sprintSecuencial;
    private static UUID sprintOrdenInverso;
    private static UUID sprintSumaSimple;
    private static UUID sprintPromedio;

    private static UUID variableSuma;
    private static UUID variablePromedio;

    @Autowired private EjecucionService ejecucionService;
    @Autowired private CalculoMetricaService calculoMetricaService;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private MetricaRepository metricaRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private MetricParametrizacionRepository parametrizacionRepository;
    @Autowired private VariableRepository variableRepository;
    @Autowired private RegistroValorRepository registroValorRepository;
    @Autowired private ResultadoMetricaRepository resultadoMetricaRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    /** Todos los userId sintéticos usados en este test — deben ser miembros del
     *  proyecto sandbox para que CalculoMetricaService.calcularMetrica() (llamado
     *  tanto por el recálculo automático como por el /calcular manual) no
     *  rechace con "No tienes acceso a este proyecto". */
    private static final List<String> USUARIOS_TEST = List.of(
        TEST_USER, "recalc-user-a", "recalc-user-b", "recalc-user-c",
        "recalc-user-x", "recalc-user-y", "recalc-user-z");

    @Test
    @Order(1)
    void prepararSandboxAislado() {
        Proyecto proyecto = proyectoRepository.findByScrumMasterIdOrderByCreatedAtDesc(TEST_USER).stream()
            .filter(p -> SANDBOX_PROYECTO_NOMBRE.equals(p.getNombre()))
            .findFirst()
            .orElseGet(() -> transactionTemplate.execute(status -> {
                Proyecto nuevo = new Proyecto();
                nuevo.setNombre(SANDBOX_PROYECTO_NOMBRE);
                nuevo.setDescripcion("Proyecto aislado para validar la corrección del recálculo after-commit. No es dato real.");
                nuevo.setMetodo("scrum");
                nuevo.setTimeBoxSemanas(2);
                nuevo.setProductGoal("Sandbox de validación automática");
                nuevo.setSprintGoal("Sandbox de validación automática");
                nuevo.setNumeroSprints(4);
                nuevo.setScrumMasterId(TEST_USER);
                return proyectoRepository.save(nuevo);
            }));
        proyectoId = proyecto.getId();

        transactionTemplate.execute(status -> {
            for (String userId : USUARIOS_TEST) {
                if (!projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)) {
                    ProjectMember m = new ProjectMember();
                    m.setProyectoId(proyectoId);
                    m.setUserId(userId);
                    m.setUserEmail(userId.contains("@") ? userId : userId + "@sandbox.mpdia.test");
                    m.setRol(userId.equals(TEST_USER) ? "scrum_master" : "scrum_member");
                    projectMemberRepository.save(m);
                }
            }
            return null;
        });

        sprintSecuencial = obtenerOCrearSprint(1, "Secuencial A->B->C (SUMA)");
        sprintOrdenInverso = obtenerOCrearSprint(2, "Orden inverso (SUMA)");
        sprintSumaSimple = obtenerOCrearSprint(3, "SUMA simple 2+1+3");
        sprintPromedio = obtenerOCrearSprint(4, "PROMEDIO 80,60,100");

        MetricParametrizacion paramSuma = obtenerOCrearParametrizacionAprobada(
            METRICA_SUMA, "SUMA", "defectos_recalculo_aftercommit");
        variableSuma = obtenerOCrearVariable(paramSuma).getId();

        MetricParametrizacion paramPromedio = obtenerOCrearParametrizacionAprobada(
            METRICA_PROMEDIO, "PROMEDIO", "clima_recalculo_aftercommit");
        variablePromedio = obtenerOCrearVariable(paramPromedio).getId();

        // Idempotencia: los TEST 1-3 verifican una progresión incremental exacta
        // (22 -> 34 -> 59), así que no pueden reutilizar registros de una corrida
        // anterior de este mismo sandbox — se limpian solo los registros/resultados
        // de ESTOS 4 sprints (propios y exclusivos de este test) antes de empezar,
        // nunca datos de otro proyecto.
        transactionTemplate.execute(status -> {
            for (UUID sprintId : List.of(sprintSecuencial, sprintOrdenInverso, sprintSumaSimple, sprintPromedio)) {
                resultadoMetricaRepository.deleteAll(resultadoMetricaRepository.findBySprintIdOrderByCalculadoAtDesc(sprintId));
                registroValorRepository.deleteAll(registroValorRepository.findBySprintId(sprintId));
            }
            return null;
        });

        assertNotNull(proyectoId);
        assertNotNull(variableSuma);
        assertNotNull(variablePromedio);
    }

    private UUID obtenerOCrearSprint(int numero, String goal) {
        return sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId).stream()
            .filter(s -> s.getNumero() == numero)
            .findFirst()
            .map(Sprint::getId)
            .orElseGet(() -> transactionTemplate.execute(status -> {
                Sprint nuevo = new Sprint();
                nuevo.setProyectoId(proyectoId);
                nuevo.setNumero(numero);
                nuevo.setSprintGoal(goal);
                nuevo.setEstado("en_ejecucion");
                nuevo.setFechaInicio(LocalDate.now());
                return sprintRepository.save(nuevo).getId();
            }));
    }

    private MetricParametrizacion obtenerOCrearParametrizacionAprobada(
            UUID metricaId, String tipoOperacion, String indicadorTecnico) {
        return parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId)
            .orElseGet(() -> transactionTemplate.execute(status -> {
                MetricParametrizacion p = new MetricParametrizacion();
                p.setVersion(1);
                p.setMetricaId(metricaId);
                p.setProyectoId(proyectoId);
                p.setUserId(TEST_USER);
                p.setUserEmail(TEST_USER);
                p.setObjetivo("Validar el recálculo automático after-commit");
                p.setProcedimiento("Cada integrante registra su propio valor; el equipo agrega " + tipoOperacion);
                p.setIndicadorVariable(indicadorTecnico);
                p.setEscala("0 o más");
                p.setFrecuenciaCaptura("por_sprint");
                p.setResponsableCaptura("EQUIPO");
                p.setTipoOperacion(tipoOperacion);
                p.setStatus("aprobada");
                p.setRevisadoPor(TEST_USER);
                p.setRevisadoAt(Instant.now());
                p.setCreatedAt(Instant.now());
                return parametrizacionRepository.save(p);
            }));
    }

    private Variable obtenerOCrearVariable(MetricParametrizacion parametrizacion) {
        List<Variable> existentes = variableRepository.findByParametrizacionIdAndParametrizacionVersion(
            parametrizacion.getId(), parametrizacion.getVersion());
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        return transactionTemplate.execute(status -> {
            Metrica metrica = metricaRepository.findById(parametrizacion.getMetricaId()).orElseThrow();
            Variable v = new Variable();
            v.setProyectoId(proyectoId);
            v.setMetrica(metrica);
            v.setNombre(parametrizacion.getIndicadorVariable());
            v.setDescripcion("Variable de test — corrección de recálculo after-commit");
            v.setTipoAlcance("individual"); // EQUIPO: cada integrante registra su propio valor
            v.setTipoIndicador("calidad");
            v.setFrecuencia("por_sprint");
            v.setCardinalidad("unico");
            v.setTipoDato("numerico");
            v.setActiva(true);
            v.setFrecuenciaCaptura("por_sprint");
            v.setParametrizacionId(parametrizacion.getId());
            v.setParametrizacionVersion(parametrizacion.getVersion());
            v.setCreatedAt(Instant.now());
            return variableRepository.save(v);
        });
    }

    private Variable cargarVariable(UUID variableId) {
        return variableRepository.findById(variableId).orElseThrow();
    }

    private BigDecimal resultadoVigente(UUID metricaId, UUID sprintId) {
        return resultadoMetricaRepository
            .findByProyectoIdAndMetrica_IdAndSprintIdAndParametrizacionVersionAndVigenteTrue(
                proyectoId, metricaId, sprintId, 1)
            .map(ResultadoMetrica::getResultado)
            .orElse(null);
    }

    // ════════════════════════════════════════════════════════════════════
    // TEST 1, 2, 3: A registra 22 -> 22; B registra 12 -> 34; C registra 25
    // -> 59. Cada captura es una llamada independiente (transacción real,
    // separada, con commit real) — exactamente como 3 peticiones HTTP
    // distintas. Antes de la corrección, este mismo test habría terminado en
    // 34 (excluyendo el 25 de C).
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    void test1_A_registra22_elResultadoVigenteEs22() {
        Variable variable = cargarVariable(variableSuma);

        ejecucionService.guardarOActualizarValor(
            variable, sprintSecuencial, "recalc-user-a", new BigDecimal("22"), null, null, null);

        assertEquals(0, new BigDecimal("22").compareTo(resultadoVigente(METRICA_SUMA, sprintSecuencial)));
    }

    @Test
    @Order(3)
    void test2_B_registra12_elResultadoVigenteEs34() {
        Variable variable = cargarVariable(variableSuma);

        ejecucionService.guardarOActualizarValor(
            variable, sprintSecuencial, "recalc-user-b", new BigDecimal("12"), null, null, null);

        assertEquals(0, new BigDecimal("34").compareTo(resultadoVigente(METRICA_SUMA, sprintSecuencial)));
    }

    @Test
    @Order(4)
    void test3_C_registra25_elResultadoVigenteEs59_no34() {
        Variable variable = cargarVariable(variableSuma);

        ejecucionService.guardarOActualizarValor(
            variable, sprintSecuencial, "recalc-user-c", new BigDecimal("25"), null, null, null);

        BigDecimal resultado = resultadoVigente(METRICA_SUMA, sprintSecuencial);
        assertNotNull(resultado, "Debe existir un resultado vigente tras la tercera captura");
        assertEquals(0, new BigDecimal("59").compareTo(resultado),
            "El resultado vigente debe ser 22+12+25=59, no 34 (el bug excluía el valor de quien disparaba el recálculo)");
    }

    // TEST 4: los tres registros pertenecen a usuarios distintos y ninguno sobrescribió al otro.
    @Test
    @Order(5)
    void test4_losTresRegistrosPertenecenAUsuariosDistintosSinSobrescritura() {
        List<RegistroValor> registros = registroValorRepository
            .findBySprintIdAndVariable_Id(sprintSecuencial, variableSuma);

        assertEquals(3, registros.size(), "Deben existir exactamente 3 filas independientes, una por integrante");
        assertEquals(3, registros.stream().map(RegistroValor::getUserId).distinct().count(),
            "Las 3 filas deben pertenecer a 3 userId distintos");

        var porUsuario = registros.stream()
            .collect(java.util.stream.Collectors.toMap(RegistroValor::getUserId, RegistroValor::getValorNum));
        assertEquals(0, new BigDecimal("22").compareTo(porUsuario.get("recalc-user-a")));
        assertEquals(0, new BigDecimal("12").compareTo(porUsuario.get("recalc-user-b")));
        assertEquals(0, new BigDecimal("25").compareTo(porUsuario.get("recalc-user-c")));
    }

    // TEST 10: el endpoint manual /calcular (CalculoMetricaService.calcularMetrica) sigue
    // calculando correctamente TODOS los registros ya confirmados — no solo el auto-recálculo.
    @Test
    @Order(6)
    void test10_calculoManualExplicito_tambienDa59() {
        ResultadoMetricaDto resultado = transactionTemplate.execute(status ->
            calculoMetricaService.calcularMetrica(
                METRICA_SUMA, new CalcularMetricaRequest(proyectoId, sprintSecuencial), TEST_USER));

        assertNotNull(resultado);
        assertEquals(0, new BigDecimal("59").compareTo(resultado.resultado()));
    }

    // ════════════════════════════════════════════════════════════════════
    // TEST 6: la misma lógica funciona sin importar cuál integrante hizo la
    // última captura — orden inverso (C, luego A, luego B) sobre un sprint
    // distinto, con valores distintos, para no depender de ningún orden
    // específico de llegada.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    void test6_ordenInverso_CluegoALuegoB_resultadoFinalCorrectoIndependienteDelOrden() {
        Variable variable = cargarVariable(variableSuma);

        ejecucionService.guardarOActualizarValor(
            variable, sprintOrdenInverso, "recalc-user-c", new BigDecimal("5"), null, null, null);
        assertEquals(0, new BigDecimal("5").compareTo(resultadoVigente(METRICA_SUMA, sprintOrdenInverso)));

        ejecucionService.guardarOActualizarValor(
            variable, sprintOrdenInverso, "recalc-user-a", new BigDecimal("10"), null, null, null);
        assertEquals(0, new BigDecimal("15").compareTo(resultadoVigente(METRICA_SUMA, sprintOrdenInverso)));

        ejecucionService.guardarOActualizarValor(
            variable, sprintOrdenInverso, "recalc-user-b", new BigDecimal("15"), null, null, null);

        BigDecimal resultado = resultadoVigente(METRICA_SUMA, sprintOrdenInverso);
        assertNotNull(resultado);
        assertEquals(0, new BigDecimal("30").compareTo(resultado),
            "5+10+15=30 sin importar que C haya sido el primero y B el último en capturar");
    }

    // TEST 8: SUMA con 2, 1 y 3 produce 6 (mismo motor, otro sprint, otros usuarios).
    @Test
    @Order(8)
    void test8_sumaConDosUnoYTres_produceSeis() {
        Variable variable = cargarVariable(variableSuma);

        ejecucionService.guardarOActualizarValor(
            variable, sprintSumaSimple, "recalc-user-x", new BigDecimal("2"), null, null, null);
        ejecucionService.guardarOActualizarValor(
            variable, sprintSumaSimple, "recalc-user-y", new BigDecimal("1"), null, null, null);
        ejecucionService.guardarOActualizarValor(
            variable, sprintSumaSimple, "recalc-user-z", new BigDecimal("3"), null, null, null);

        BigDecimal resultado = resultadoVigente(METRICA_SUMA, sprintSumaSimple);
        assertNotNull(resultado);
        assertEquals(0, new BigDecimal("6").compareTo(resultado));
    }

    // TEST 7: PROMEDIO con 80, 60 y 100 produce 80 — confirma que la corrección
    // del recálculo after-commit no altera ninguna semántica de agregación.
    @Test
    @Order(9)
    void test7_promedioConOchentaSesentaYCien_produceOchenta() {
        Variable variable = cargarVariable(variablePromedio);

        ejecucionService.guardarOActualizarValor(
            variable, sprintPromedio, "recalc-user-a", new BigDecimal("80"), null, null, null);
        ejecucionService.guardarOActualizarValor(
            variable, sprintPromedio, "recalc-user-b", new BigDecimal("60"), null, null, null);
        ejecucionService.guardarOActualizarValor(
            variable, sprintPromedio, "recalc-user-c", new BigDecimal("100"), null, null, null);

        BigDecimal resultado = resultadoVigente(METRICA_PROMEDIO, sprintPromedio);
        assertNotNull(resultado);
        assertEquals(0, new BigDecimal("80").compareTo(resultado));
    }

    // ════════════════════════════════════════════════════════════════════
    // TEST vigente/histórico: la lógica ya implementada (V37) sigue intacta —
    // cada recálculo marca el anterior como histórico y el nuevo como vigente.
    // ════════════════════════════════════════════════════════════════════
    @Test
    @Order(10)
    void resultadosAnterioresQuedanHistoricosYSoloElUltimoEsVigente() {
        List<ResultadoMetrica> todos = resultadoMetricaRepository.findAll().stream()
            .filter(r -> proyectoId.equals(r.getProyectoId())
                && METRICA_SUMA.equals(r.getMetrica().getId())
                && sprintSecuencial.equals(r.getSprintId()))
            .toList();

        long vigentes = todos.stream().filter(ResultadoMetrica::getVigente).count();
        assertEquals(1, vigentes, "Debe haber exactamente un resultado vigente para esta métrica/sprint");

        ResultadoMetrica elVigente = todos.stream().filter(ResultadoMetrica::getVigente).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("59").compareTo(elVigente.getResultado()));

        assertTrue(todos.stream().filter(r -> !r.getVigente())
                .anyMatch(r -> new BigDecimal("22").compareTo(r.getResultado()) == 0),
            "El resultado intermedio (22, tras la primera captura) debe seguir existiendo como histórico");
        assertTrue(todos.stream().filter(r -> !r.getVigente())
                .anyMatch(r -> new BigDecimal("34").compareTo(r.getResultado()) == 0),
            "El resultado intermedio (34, tras la segunda captura) debe seguir existiendo como histórico");
    }
}
