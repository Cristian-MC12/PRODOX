// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.validation;

import com.prodox.dto.CrearProyectoRequest;
import com.prodox.dto.GuardarParametrizacionRequest;
import com.prodox.dto.MetricParametrizacionDto;
import com.prodox.entity.AppUser;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.MetricParametrizacionRanking;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.MetricParametrizacionRankingRepository;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.service.MetricRankingService;
import com.prodox.service.ProyectoService;
import com.prodox.util.FingerprintUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V43 — Contra Postgres real (mismo patrón que MetricRankingDuplicadoPendienteTest:
 * sin @Transactional a nivel de test, porque la prueba de concurrencia necesita
 * transacciones REALES y separadas por hilo).
 *
 * Cierre del diseño (revisión final, segunda vuelta): esta suite se reescribió
 * completa cuando la propia ejecución de sus tests originales, contra Postgres
 * real, demostró una dependencia circular en la primera versión de la
 * corrección "+1 solo vía Usar" — el Top3 solo muestra configuraciones que ya
 * tienen fila en metric_parametrizacion_ranking, "Usar" solo puede pulsarse
 * sobre algo visible en el Top3, y el único punto que creaba esa fila exigía
 * que ya existiera. Ninguna configuración nueva podía entrar jamás a la tabla.
 *
 * Corrección: MetricRankingService separa dos operaciones —
 * asegurarExistenciaRanking() (CUALQUIER guardado exitoso, manual o "Usar",
 * garantiza que la fila exista con usos=0; nunca incrementa) y
 * registrarUsoRanking() (solo +1, solo si usadaDesdeRankingId fue enviado y
 * validado). Esta suite cubre ambas, incluida la concurrencia real de cada
 * una por separado.
 */
@SpringBootTest
@ActiveProfiles("test")
class MetricParametrizacionRankingConcurrenciaTest {

    // Misma métrica global reutilizada por MetricRankingDuplicadoPendienteTest —
    // seguro porque el fingerprint (no la métrica) es lo que aísla cada test.
    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");

    @Autowired private MetricRankingService rankingService;
    @Autowired private ProyectoService proyectoService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private MetricParametrizacionRepository parametrizacionRepo;
    @Autowired private MetricParametrizacionRankingRepository rankingPorMetricaRepo;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID proyectoId;
    private UUID qaUserId;
    private String userId;
    private String userEmail;
    /** IDs de toda parametrización creada por el test en curso — limpieza garantizada por ID. */
    private final ConcurrentLinkedQueue<UUID> parametrizacionesCreadas = new ConcurrentLinkedQueue<>();
    /** Proyectos adicionales creados por un test (más allá del principal) — para simular "otro proyecto". */
    private final ConcurrentLinkedQueue<UUID> proyectosAdicionales = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void crearActorYProyectoTemporal() {
        parametrizacionesCreadas.clear();
        proyectosAdicionales.clear();

        AppUser qa = new AppUser();
        qa.setEmail("qa-v43-ranking-" + UUID.randomUUID() + "@mpdiaqa.test");
        qa.setPasswordHash("{noop}test-no-login");
        qa.setRole("scrum_master");
        qa = userRepo.save(qa);
        qaUserId = qa.getId();
        userId = qaUserId.toString();
        userEmail = qa.getEmail();

        var creado = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-V43-RANKING-" + UUID.randomUUID(), "test ranking V43", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        proyectoId = creado.id();
    }

    @AfterEach
    void limpiarProyectosActorYParametrizaciones() {
        // Mismo orden que MetricRankingDuplicadoPendienteTest: proyectos primero
        // (cascada real de sprints/project_members/variables), luego
        // parametrizaciones por ID. Al borrar cada metric_parametrizaciones,
        // ON DELETE CASCADE (V43) borra automáticamente su fila de
        // metric_parametrizacion_ranking si esa parametrización era la canónica
        // — no hace falta limpieza explícita de la tabla de ranking.
        for (UUID otroProyecto : proyectosAdicionales) {
            try {
                proyectoService.eliminar(otroProyecto, userId);
            } catch (Exception ignored) { }
        }
        if (proyectoId != null) {
            try {
                proyectoService.eliminar(proyectoId, userId);
            } catch (Exception ignored) { }
        }
        if (!parametrizacionesCreadas.isEmpty()) {
            parametrizacionRepo.deleteAllById(parametrizacionesCreadas);
        }
        if (qaUserId != null) {
            userRepo.deleteById(qaUserId);
        }

        List<?> sobrevivientes = parametrizacionRepo.findAllById(parametrizacionesCreadas);
        assertEquals(0, sobrevivientes.size(), "No debe quedar ninguna parametrización de este test tras la limpieza");
        assertTrue(userRepo.findById(qaUserId).isEmpty(), "La cuenta QA desechable debe quedar eliminada");
    }

    private GuardarParametrizacionRequest requestConEscala(String objetivo, UUID proyectoDestino) {
        return requestConEscala(objetivo, proyectoDestino, null);
    }

    /** Variante con señal de "Usar" — usadaDesdeRankingId es el id canónico ya visible en el Top3 que el usuario seleccionó. */
    private GuardarParametrizacionRequest requestConEscala(String objetivo, UUID proyectoDestino, UUID usadaDesdeRankingId) {
        return new GuardarParametrizacionRequest(
                null, objetivo, "procedimiento test", "indicador test", "escala texto",
                null, proyectoDestino, METRICA_FAT,
                "SUMA", null, "unidad", "fuente", "por_sprint", "SCRUM_MASTER",
                "NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true,
                "Cantidad de aprendizajes documentados.",
                usadaDesdeRankingId
        );
    }

    /** Guardado MANUAL (sin señal de "Usar") — asegura existencia en el ranking, nunca incrementa. */
    private MetricParametrizacionDto guardarYRegistrar(String objetivo, UUID proyectoDestino) {
        MetricParametrizacionDto dto = rankingService.guardar(requestConEscala(objetivo, proyectoDestino), userId, userEmail);
        parametrizacionesCreadas.add(dto.id());
        return dto;
    }

    /** Guardado ORIGINADO en "Usar" (señal validada) — SÍ incrementa +1 si el guardado es exitoso. */
    private MetricParametrizacionDto usarYRegistrar(UUID usadaDesdeRankingId, String objetivo, UUID proyectoDestino,
                                                      String actorId, String actorEmail) {
        MetricParametrizacionDto dto = rankingService.guardar(
                requestConEscala(objetivo, proyectoDestino, usadaDesdeRankingId), actorId, actorEmail);
        parametrizacionesCreadas.add(dto.id());
        return dto;
    }

    private Optional<MetricParametrizacionRanking> buscarEntradaDeRanking(UUID parametrizacionId) {
        MetricParametrizacion p = parametrizacionRepo.findById(parametrizacionId).orElseThrow();
        String fingerprint = FingerprintUtil.calcularFingerprint(p);
        return rankingPorMetricaRepo.findByMetricaIdOrderByUsosDesc(METRICA_FAT).stream()
                .filter(r -> r.getFingerprint().equals(fingerprint))
                .findFirst();
    }

    /**
     * Fingerprint que produciría un guardado exitoso de requestConEscala(objetivo, *) —
     * usado para verificar la AUSENCIA de una fila de ranking cuando la parametrización
     * en sí nunca llegó a existir (guardado fallido) o dejó de existir (rollback).
     */
    private String fingerprintEsperado(String objetivo) {
        MetricParametrizacion candidato = new MetricParametrizacion();
        candidato.setMetricaId(METRICA_FAT);
        candidato.setObjetivo(objetivo);
        candidato.setProcedimiento("procedimiento test");
        candidato.setIndicadorVariable("indicador test");
        candidato.setEscala("escala texto");
        candidato.setEscalaTipo("NUMERICA_ENTERA");
        candidato.setEscalaMin(BigDecimal.ZERO);
        candidato.setEscalaPaso(BigDecimal.ONE);
        candidato.setEscalaSinLimite(true);
        candidato.setTipoOperacion("SUMA");
        candidato.setUnidadResultado("unidad");
        candidato.setFuenteAcademica("fuente");
        candidato.setFrecuenciaCaptura("por_sprint");
        candidato.setResponsableCaptura("SCRUM_MASTER");
        return FingerprintUtil.calcularFingerprint(candidato);
    }

    private long contarFilasDeRankingParaFingerprint(String fingerprint) {
        return rankingPorMetricaRepo.findByMetricaIdOrderByUsosDesc(METRICA_FAT).stream()
                .filter(r -> r.getFingerprint().equals(fingerprint))
                .count();
    }

    // ── A: guardado manual de una configuración NUEVA -> crea fila con usos=0 ──

    @Test
    void guardadoManualDeConfiguracionNueva_creaEntradaDeRankingConUsosCero() {
        String objetivo = "V43-manual-nueva-" + UUID.randomUUID();
        MetricParametrizacionDto dto = guardarYRegistrar(objetivo, proyectoId);

        MetricParametrizacionRanking entrada = buscarEntradaDeRanking(dto.id()).orElseThrow();
        assertEquals(0, entrada.getUsos(), "Un guardado manual (sin 'Usar') nunca debe incrementar usos, ni siquiera al crear la fila");
        assertEquals(dto.id(), entrada.getParametrizacionCanonicaId());
    }

    // ── B: guardado manual de una configuración YA existente -> usos no cambia ──

    @Test
    void guardadoManualDeConfiguracionExistente_noCambiaUsos() {
        String objetivo = "V43-manual-existente-" + UUID.randomUUID();
        MetricParametrizacionDto primero = guardarYRegistrar(objetivo, proyectoId);
        MetricParametrizacionRanking trasElPrimero = buscarEntradaDeRanking(primero.id()).orElseThrow();
        assertEquals(0, trasElPrimero.getUsos());

        // Segundo guardado MANUAL (mismo usuario, mismo proyecto -> reenvío idéntico
        // sobre la misma versión pendiente; no crea fila física nueva, y tampoco toca usos).
        MetricParametrizacionDto segundo = guardarYRegistrar(objetivo, proyectoId);
        assertEquals(primero.id(), segundo.id(), "Reenvío idéntico manual reutiliza la misma versión pendiente");

        MetricParametrizacionRanking trasElSegundo = buscarEntradaDeRanking(primero.id()).orElseThrow();
        assertEquals(0, trasElSegundo.getUsos(), "Un segundo guardado MANUAL de la misma configuración no debe incrementar usos");
    }

    // ── C/F: "Usar" válido incrementa usos; autor canónico nunca cambia ──────

    @Test
    void reutilizacionesSucesivasViaUsar_incrementanUsosSinCrearOtraFilaYSinCambiarAutor() {
        String objetivo = "V43-reuso-" + UUID.randomUUID();

        // Paso 0: alguien guarda esta configuración por primera vez, MANUALMENTE
        // (sin "Usar" — todavía no hay nada que usar). Esto es lo que la hace
        // aparecer en el Top3 por primera vez, con usos=0.
        MetricParametrizacionDto primero = guardarYRegistrar(objetivo, proyectoId);
        assertEquals(0, buscarEntradaDeRanking(primero.id()).orElseThrow().getUsos());

        // Uso 1 real: MISMO usuario, OTRO proyecto, esta vez SÍ vía "Usar" —
        // selecciona en el Top3 la entrada canónica creada en el paso 0.
        var segundoProyecto = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-V43-SEGUNDO-" + UUID.randomUUID(), "segundo proyecto", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        proyectosAdicionales.add(segundoProyecto.id());
        MetricParametrizacionDto segundo = usarYRegistrar(primero.id(), objetivo, segundoProyecto.id(), userId, userEmail);

        MetricParametrizacionRanking entradaTrasSegundo = buscarEntradaDeRanking(primero.id()).orElseThrow();
        assertEquals(1, entradaTrasSegundo.getUsos());
        assertEquals(primero.id(), entradaTrasSegundo.getParametrizacionCanonicaId(),
                "El autor/canónica original (primero) nunca cambia porque otro guardado haya reutilizado la configuración");
        assertNotEquals(primero.id(), segundo.id(), "guardarPorMetrica() sigue creando su propia fila física por proyecto");

        // Uso 2 real: OTRO usuario, vía "Usar" sobre la misma entrada del Top3.
        AppUser otroUsuario = new AppUser();
        otroUsuario.setEmail("qa-v43-otro-" + UUID.randomUUID() + "@mpdiaqa.test");
        otroUsuario.setPasswordHash("{noop}test-no-login");
        otroUsuario.setRole("scrum_master");
        otroUsuario = userRepo.save(otroUsuario);
        String otroUserId = otroUsuario.getId().toString();
        var tercerProyecto = proyectoService.crear(otroUserId, new CrearProyectoRequest(
                "TEST-V43-TERCERO-" + UUID.randomUUID(), "tercer proyecto", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        try {
            MetricParametrizacionDto tercero = usarYRegistrar(primero.id(), objetivo, tercerProyecto.id(), otroUserId, otroUsuario.getEmail());

            MetricParametrizacionRanking entradaTrasTercero = buscarEntradaDeRanking(primero.id()).orElseThrow();
            assertEquals(2, entradaTrasTercero.getUsos());
            assertEquals(primero.id(), entradaTrasTercero.getParametrizacionCanonicaId(),
                    "El autor original sigue siendo 'primero' aunque un tercer usuario haya reutilizado la configuración");

            // Nunca aparece como una fila de ranking separada para la misma métrica+fingerprint.
            String fingerprint = FingerprintUtil.calcularFingerprint(parametrizacionRepo.findById(primero.id()).orElseThrow());
            assertEquals(1, contarFilasDeRankingParaFingerprint(fingerprint));
        } finally {
            proyectoService.eliminar(tercerProyecto.id(), otroUserId);
            userRepo.deleteById(otroUsuario.getId());
        }
    }

    // ── 5/6/7/8: configuración realmente distinta produce fila DISTINTA ──────

    @Test
    void dosConfiguracionesRealmenteDistintas_produceDosFilasDeRankingDistintasAmbasEnUsosCero() {
        String objetivoA = "V43-distinta-A-" + UUID.randomUUID();
        String objetivoB = "V43-distinta-B-" + UUID.randomUUID();

        MetricParametrizacionDto a = guardarYRegistrar(objetivoA, proyectoId);
        MetricParametrizacionDto b = guardarYRegistrar(objetivoB, proyectoId);

        MetricParametrizacionRanking entradaA = buscarEntradaDeRanking(a.id()).orElseThrow();
        MetricParametrizacionRanking entradaB = buscarEntradaDeRanking(b.id()).orElseThrow();

        assertNotEquals(entradaA.getId(), entradaB.getId());
        assertEquals(0, entradaA.getUsos());
        assertEquals(0, entradaB.getUsos());
    }

    // ── E: guardado fallido/validación rechazada -> nunca crea ni toca entrada de ranking ──

    @Test
    void guardadoQueLanzaExcepcion_noRegistraNadaEnElRanking() {
        // proyectoId inexistente -> SecurityException antes de llegar a
        // guardarPorMetrica()/asegurarExistenciaRanking()/registrarUsoRanking().
        UUID proyectoInexistente = UUID.randomUUID();
        String objetivo = "V43-fallido-" + UUID.randomUUID();

        assertThrows(SecurityException.class, () ->
                rankingService.guardar(requestConEscala(objetivo, proyectoInexistente), userId, userEmail));

        // Sin fila creada, no hay forma de buscar por parametrizacionId — se confirma
        // indirectamente: ninguna fila de metric_parametrizacion_ranking en TODA la
        // tabla para METRICA_FAT tiene el fingerprint que este guardado habría
        // producido (no se pudo haber creado, porque nunca se construyó la entidad).
        assertEquals(0, contarFilasDeRankingParaFingerprint(fingerprintEsperado(objetivo)),
                "Un guardado que falló nunca debe dejar una entrada de ranking");
    }

    // ── 11: abrir/consultar el ranking (solo lectura) nunca incrementa nada ──

    @Test
    void consultarElTop3RepetidamenteNoIncrementaUsos() {
        String objetivo = "V43-solo-lectura-" + UUID.randomUUID();
        MetricParametrizacionDto dto = guardarYRegistrar(objetivo, proyectoId);

        rankingService.getTop3ByMetricaId(METRICA_FAT);
        rankingService.getTop3ByMetricaId(METRICA_FAT);
        rankingService.getTop3ByMetricaId(METRICA_FAT);

        MetricParametrizacionRanking entrada = buscarEntradaDeRanking(dto.id()).orElseThrow();
        assertEquals(0, entrada.getUsos(), "Consultar el ranking repetidamente no debe cambiar el contador de usos");
    }

    // ── H (CRÍTICO): dos usos concurrentes REALES (vía "Usar" validado) de la ──
    // MISMA configuración ya existente -> ambos quedan registrados (usos +2),
    // UNA sola fila de ranking. Este es el escenario exacto reportado:
    // "usos=9, A usa X, B usa X simultáneamente -> usos=11 (no 10), una sola
    // fila (no dos)". La garantía de atomicidad del UPDATE atómico es
    // independiente del valor de partida — probarlo desde 0 (-> 2) certifica
    // exactamente la misma propiedad que desde 9 (-> 11).

    @Test
    void dosUsosConcurrentesRealesSobreLaMismaConfiguracion_ambosQuedanRegistradosEnUnaSolaFila() throws Exception {
        String objetivoCompartido = "V43-concurrencia-uso-" + UUID.randomUUID();

        // Paso 0: la configuración ya existe en el ranking (usos=0) — creada por
        // un guardado manual previo, exactamente como ocurriría en producción:
        // alguien la guarda, aparece en el Top3, y RECIÉN AHÍ otros pueden "usarla".
        MetricParametrizacionDto seed = guardarYRegistrar(objetivoCompartido, proyectoId);
        assertEquals(0, buscarEntradaDeRanking(seed.id()).orElseThrow().getUsos());

        // Segundo proyecto: cada hilo guarda en un proyecto DISTINTO a propósito.
        // guardarPorMetrica() ya serializa por (proyectoId, metricaId) con un
        // advisory lock — si ambos hilos usaran el MISMO proyecto, ese lock
        // preexistente absorbería toda la concurrencia y este test terminaría
        // probando esa protección (ya cubierta en MetricRankingDuplicadoPendienteTest),
        // no la del UPDATE atómico de usos. Con proyectos distintos, ambos
        // guardados corren en paralelo de verdad.
        var segundoProyecto = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-V43-CONC-" + UUID.randomUUID(), "concurrencia", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        proyectosAdicionales.add(segundoProyecto.id());
        UUID proyectoB = segundoProyecto.id();

        CountDownLatch salida = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Exception> errores = Collections.synchronizedList(new ArrayList<>());
        List<UUID> idsCreados = Collections.synchronizedList(new ArrayList<>());

        Runnable tareaA = () -> {
            try {
                MetricParametrizacionDto dto = rankingService.guardar(
                        requestConEscala(objetivoCompartido, proyectoId, seed.id()), userId, userEmail);
                idsCreados.add(dto.id());
            } catch (Exception e) {
                errores.add(e);
            } finally {
                salida.countDown();
            }
        };
        Runnable tareaB = () -> {
            try {
                MetricParametrizacionDto dto = rankingService.guardar(
                        requestConEscala(objetivoCompartido, proyectoB, seed.id()), userId, userEmail);
                idsCreados.add(dto.id());
            } catch (Exception e) {
                errores.add(e);
            } finally {
                salida.countDown();
            }
        };

        pool.submit(tareaA);
        pool.submit(tareaB);
        assertTrue(salida.await(30, TimeUnit.SECONDS), "Ambas peticiones concurrentes deben terminar");
        pool.shutdown();
        parametrizacionesCreadas.addAll(idsCreados);

        assertTrue(errores.isEmpty(), "Ninguna de las dos peticiones concurrentes debe fallar: " + errores);
        assertEquals(2, idsCreados.size(), "Cada hilo crea su propia fila física en metric_parametrizaciones (uno por proyecto)");

        MetricParametrizacionRanking entrada = buscarEntradaDeRanking(seed.id()).orElseThrow();
        assertEquals(2, entrada.getUsos(),
                "Ambos usos concurrentes deben quedar registrados — nunca 1 (incremento perdido)");

        String fingerprint = FingerprintUtil.calcularFingerprint(parametrizacionRepo.findById(seed.id()).orElseThrow());
        assertEquals(1, contarFilasDeRankingParaFingerprint(fingerprint),
                "Dos usos concurrentes de la MISMA configuración nunca deben dejar dos filas de ranking");
    }

    // ── I: dos guardados MANUALES concurrentes de una configuración NUEVA ────
    // -> exactamente una fila de ranking (el ON CONFLICT DO NOTHING de
    // asegurarExistenciaRanking() es igual de atómico que el UPDATE de
    // registrarUsoRanking()), y usos permanece en 0 (ninguno de los dos vino
    // de "Usar").

    @Test
    void dosGuardadosManualesConcurrentesDeConfiguracionNueva_unaSolaFilaConUsosCero() throws Exception {
        String objetivoCompartido = "V43-concurrencia-manual-" + UUID.randomUUID();

        var segundoProyecto = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-V43-CONC-MANUAL-" + UUID.randomUUID(), "concurrencia manual", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        proyectosAdicionales.add(segundoProyecto.id());
        UUID proyectoB = segundoProyecto.id();

        CountDownLatch salida = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Exception> errores = Collections.synchronizedList(new ArrayList<>());
        List<UUID> idsCreados = Collections.synchronizedList(new ArrayList<>());

        Runnable tareaA = () -> {
            try {
                MetricParametrizacionDto dto = rankingService.guardar(
                        requestConEscala(objetivoCompartido, proyectoId), userId, userEmail);
                idsCreados.add(dto.id());
            } catch (Exception e) {
                errores.add(e);
            } finally {
                salida.countDown();
            }
        };
        Runnable tareaB = () -> {
            try {
                MetricParametrizacionDto dto = rankingService.guardar(
                        requestConEscala(objetivoCompartido, proyectoB), userId, userEmail);
                idsCreados.add(dto.id());
            } catch (Exception e) {
                errores.add(e);
            } finally {
                salida.countDown();
            }
        };

        pool.submit(tareaA);
        pool.submit(tareaB);
        assertTrue(salida.await(30, TimeUnit.SECONDS), "Ambos guardados manuales concurrentes deben terminar");
        pool.shutdown();
        parametrizacionesCreadas.addAll(idsCreados);

        assertTrue(errores.isEmpty(), "Ningún guardado manual concurrente debe fallar: " + errores);
        assertEquals(2, idsCreados.size());

        String fingerprint = fingerprintEsperado(objetivoCompartido);
        assertEquals(1, contarFilasDeRankingParaFingerprint(fingerprint),
                "Dos guardados MANUALES concurrentes de la MISMA configuración nueva nunca deben dejar dos filas de ranking");

        MetricParametrizacionRanking entrada = rankingPorMetricaRepo.findByMetricaIdOrderByUsosDesc(METRICA_FAT).stream()
                .filter(r -> r.getFingerprint().equals(fingerprint))
                .findFirst().orElseThrow();
        assertEquals(0, entrada.getUsos(), "Ningún guardado manual concurrente incrementa usos, aunque ambos corran a la vez");
    }

    // ── J: transacción con rollback -> no debe quedar fila de ranking huérfana ──
    // guardar() es @Transactional; al llamarlo desde dentro de una transacción
    // ya abierta por el test (misma semántica REQUIRED de Spring), se une a
    // ELLA en vez de abrir la suya propia. Forzar el rollback de esa
    // transacción exterior deshace TODO lo que ocurrió dentro — incluida la
    // fila de metric_parametrizacion_ranking que asegurarExistenciaRanking()
    // haya insertado — exactamente igual que cualquier fallo real a mitad de
    // guardarPorMetrica() lo haría.

    @Test
    void configuracionNuevaGuardadaConRollbackPosterior_noDejaFilaDeRankingHuerfana() {
        String objetivo = "V43-rollback-" + UUID.randomUUID();
        TransactionTemplate tt = new TransactionTemplate(transactionManager);

        tt.execute(status -> {
            MetricParametrizacionDto dto = rankingService.guardar(requestConEscala(objetivo, proyectoId), userId, userEmail);
            assertNotNull(dto.id());
            status.setRollbackOnly(); // fuerza el rollback de TODO lo hecho en esta transacción
            return null;
        });
        // No se agrega el id a parametrizacionesCreadas: tras el rollback, no existe.

        assertEquals(0, contarFilasDeRankingParaFingerprint(fingerprintEsperado(objetivo)),
                "El rollback de la transacción de guardado debe deshacer también la fila de ranking recién asegurada — sin importar que el INSERT en sí haya corrido sin error");
    }

    // ── 13: Top3 ordenado por usos ────────────────────────────────────────────

    @Test
    void getTop3ByMetricaId_devuelveOrdenadoPorUsosDescendente() {
        String masUsada = "V43-masusada-" + UUID.randomUUID();
        String menosUsada = "V43-menosusada-" + UUID.randomUUID();

        // masUsada: guardado manual (crea, usos=0) + un uso real vía "Usar" (usos=1).
        MetricParametrizacionDto principal = guardarYRegistrar(masUsada, proyectoId);
        var segundoProyecto = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-V43-ORDEN-" + UUID.randomUUID(), "orden", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal test"));
        proyectosAdicionales.add(segundoProyecto.id());
        usarYRegistrar(principal.id(), masUsada, segundoProyecto.id(), userId, userEmail);

        // menosUsada: solo guardado manual -> usos=0, igual queda visible/candidata en el Top3.
        guardarYRegistrar(menosUsada, proyectoId);

        var top3 = rankingService.getTop3ByMetricaId(METRICA_FAT);

        int idxMasUsada = -1, idxMenosUsada = -1;
        for (int i = 0; i < top3.size(); i++) {
            if (top3.get(i).objetivo().equals(masUsada)) idxMasUsada = i;
            if (top3.get(i).objetivo().equals(menosUsada)) idxMenosUsada = i;
        }
        assertTrue(idxMasUsada >= 0, "La configuración con más usos debe aparecer en el Top3");
        if (idxMenosUsada >= 0) {
            assertTrue(idxMasUsada < idxMenosUsada, "La de más usos debe aparecer antes que la de menos usos");
        }
        assertEquals(1, top3.get(idxMasUsada).usos());
    }
}
