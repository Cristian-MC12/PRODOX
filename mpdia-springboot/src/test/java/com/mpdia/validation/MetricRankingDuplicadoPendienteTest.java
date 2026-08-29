// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.dto.CrearProyectoRequest;
import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.VerificarParametrizacionRequest;
import com.mpdia.entity.AppUser;
import com.mpdia.entity.Variable;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.VariableRepository;
import com.mpdia.service.MetricRankingService;
import com.mpdia.service.ProyectoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduce contra Postgres real (mismo patrón que EliminarProyectoE2ETest /
 * Fase3CrearParametrizacionesPrueba1Test: sin @Transactional a nivel de test,
 * porque la prueba de concurrencia necesita transacciones REALES y separadas
 * por hilo — una sola transacción envolvente haría que ambos hilos compartieran
 * la misma conexión/transacción y el escenario de carrera dejaría de existir)
 * el defecto reportado: dos envíos de "Enviar al Scrum Master" para la misma
 * métrica+proyecto terminaban creando DOS filas 'pendiente' en vez de una.
 *
 * Causa raíz real, confirmada por SQL directo contra datos reales del usuario
 * (proyecto real, métrica "Aprendizaje organizacional (FAT)" / "Problemas
 * Recurrentes de Software"): resumen-seleccion.component.ts:aceptar() podía
 * reenviar una métrica ya enviada desde parametrizacion.component.ts:guardar()
 * SIN la escala estructurada (payload incompleto) — MetricRankingService
 * consideraba (correctamente, dado el payload que recibía) que el contenido
 * había cambiado y creaba una versión nueva. Corregido en el frontend
 * (ambos payloads ahora envían los mismos campos) y reforzado acá en el
 * backend con un advisory lock de Postgres que hace atómica la sección
 * "leer historial -> decidir -> insertar" de guardarPorMetrica(), para que
 * ninguna combinación de reintentos/doble clic/pestañas pueda volver a
 * producir dos filas 'pendiente' con el MISMO contenido.
 *
 * LIMPIEZA (corregida): la versión original de este archivo reutilizaba la
 * cuenta REAL "sm9130109@gmail.com" como actor y confiaba en que borrar el
 * proyecto temporal (ProyectoService.eliminar(), cascada real) también
 * borrara sus parametrizaciones. Eso es falso: metric_parametrizaciones.
 * proyecto_id tiene ON DELETE SET NULL (V6__parametrizacion_proyecto.sql),
 * NO CASCADE — borrar el proyecto deja la parametrización viva, huérfana
 * (proyecto_id=NULL), atribuida a la cuenta real del usuario. Con 4 tests y
 * decenas de ejecuciones de mvn test, esto generó 33+ filas huérfanas reales
 * en la BD de desarrollo (auditadas y limpiadas manualmente aparte de esta
 * corrección). Ahora: 1) cada test usa una cuenta QA propia y desechable
 * (nunca la cuenta real), 2) cada parametrización creada se registra por ID
 * en {@link #parametrizacionesCreadas} en el momento de crearse, 3) el
 * @AfterEach borra explícitamente esas filas POR ID (no depende de qué haya
 * quedado en proyecto_id), además de la cascada real del proyecto y la
 * cuenta QA — así el resultado es correcto sin importar si la FK deja
 * proyecto_id en NULL o no. @AfterEach de JUnit 5 se ejecuta SIEMPRE, incluso
 * si el cuerpo del test lanzó una aserción fallida — es el mecanismo de
 * limpieza garantizada ya usado en el resto de esta suite (ver
 * EliminarProyectoE2ETest), no se introduce ningún framework nuevo.
 */
@SpringBootTest
@ActiveProfiles("test")
class MetricRankingDuplicadoPendienteTest {

    // Métrica global reutilizada por Fase3CrearParametrizacionesPrueba1Test bajo OTRO
    // proyecto (Prueba 1) — reutilizarla acá bajo un proyecto temporal nuevo es seguro:
    // metric_parametrizaciones está aislada por (proyecto_id, metrica_id, version).
    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");

    @Autowired private MetricRankingService rankingService;
    @Autowired private ProyectoService proyectoService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private MetricParametrizacionRepository parametrizacionRepo;
    @Autowired private VariableRepository variableRepository;

    private UUID proyectoId;
    private UUID qaUserId;
    private String userId;
    private String userEmail;
    /** IDs de toda parametrización creada por el test en curso — limpieza garantizada por ID, sin depender de proyecto_id. */
    private final ConcurrentLinkedQueue<UUID> parametrizacionesCreadas = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void crearActorYProyectoTemporal() {
        parametrizacionesCreadas.clear();

        // Cuenta QA propia y desechable — nunca la cuenta real del usuario. Nombre
        // claramente distinguible de datos reales (prefijo qa-, dominio mpdiaqa.test,
        // sufijo aleatorio) para que cualquier fila que sobreviva a un fallo de
        // limpieza sea inequívocamente identificable como residuo de este test.
        AppUser qa = new AppUser();
        qa.setEmail("qa-metricranking-duppendiente-" + UUID.randomUUID() + "@mpdiaqa.test");
        qa.setPasswordHash("{noop}test-no-login");
        qa.setRole("scrum_master");
        qa = userRepo.save(qa);
        qaUserId = qa.getId();
        userId = qaUserId.toString();
        userEmail = qa.getEmail();

        var creado = proyectoService.crear(userId, new CrearProyectoRequest(
                "TEST-DUP-PENDIENTE-" + UUID.randomUUID(), "test duplicado pendiente", "scrum",
                1, 2, LocalDate.now(), "goal test"));
        proyectoId = creado.id();
    }

    @AfterEach
    void limpiarProyectoActorYParametrizaciones() {
        // 1) Cascada real del proyecto: borra sprints, project_members, variables y
        //    resultados_metricas (todas ON DELETE CASCADE desde proyectos). Esto debe
        //    ir ANTES de borrar las parametrizaciones por ID: variables.parametrizacion_id
        //    no tiene ON DELETE CASCADE hacia metric_parametrizaciones (V22), así que si
        //    quedara una Variable viva referenciando una parametrización, el DELETE del
        //    paso 2 fallaría por violación de FK. Al borrar el proyecto primero, esa
        //    Variable ya desaparece por la cascada de variables.proyecto_id.
        if (proyectoId != null) {
            try {
                proyectoService.eliminar(proyectoId, userId);
            } catch (Exception ignored) {
                // Proyecto ya pudo haber sido borrado por el propio test; no bloquear la limpieza.
            }
        }

        // 2) Borrado explícito por ID: la corrección real. No depende de qué haya
        //    quedado en proyecto_id (NULL o no) tras la cascada — apunta directo a las
        //    filas que este test creó.
        if (!parametrizacionesCreadas.isEmpty()) {
            parametrizacionRepo.deleteAllById(parametrizacionesCreadas);
        }

        // 3) Cuenta QA desechable.
        if (qaUserId != null) {
            userRepo.deleteById(qaUserId);
        }

        // 4) Verificación dura: nada de lo que este test creó debe sobrevivir, sin
        //    importar el estado final de proyecto_id.
        List<?> sobrevivientes = parametrizacionRepo.findAllById(parametrizacionesCreadas);
        assertEquals(0, sobrevivientes.size(),
                "No debe quedar ninguna parametrización de este test tras la limpieza (por ID, no por proyecto_id)");
        assertTrue(userRepo.findById(qaUserId).isEmpty(), "La cuenta QA desechable debe quedar eliminada");
    }

    private GuardarParametrizacionRequest requestConEscala(String objetivo) {
        return new GuardarParametrizacionRequest(
                null, objetivo, "procedimiento test", "indicador test", "escala texto",
                null, proyectoId, METRICA_FAT,
                "SUMA", null, "unidad", "fuente", "por_sprint",
                "NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true,
                "Cantidad de aprendizajes documentados."
        );
    }

    /** Crea una parametrización y la registra de inmediato para la limpieza garantizada. */
    private MetricParametrizacionDto guardarYRegistrar(String objetivo) {
        MetricParametrizacionDto dto = rankingService.guardar(requestConEscala(objetivo), userId, userEmail);
        parametrizacionesCreadas.add(dto.id());
        return dto;
    }

    // ── 1 y 2: primer envío crea 1 pendiente; el mismo contenido no crea otra ──

    @Test
    void primerEnvio_creaUnaPendiente_reenvioIdentico_noCreaOtra() {
        MetricParametrizacionDto v1 = guardarYRegistrar("obj");
        assertEquals(1, v1.version());
        assertEquals("pendiente", v1.status());

        MetricParametrizacionDto v2 = guardarYRegistrar("obj");

        assertEquals(v1.id(), v2.id(), "Un reenvío con el mismo contenido debe devolver la MISMA fila, no crear una nueva versión");
        List<?> historial = parametrizacionRepo.findHistorialVersiones(METRICA_FAT, proyectoId);
        assertEquals(1, historial.size(), "Solo debe existir 1 parametrización pendiente para esta métrica+proyecto");
    }

    // ── 3: dos solicitudes simultáneas no dejan dos pendientes (el defecto reportado) ──

    @Test
    void dosSolicitudesConcurrentesConElMismoContenido_terminanEnUnaSolaPendiente() throws Exception {
        CountDownLatch salida = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Exception> errores = Collections.synchronizedList(new java.util.ArrayList<>());

        Runnable tarea = () -> {
            try {
                MetricParametrizacionDto dto = rankingService.guardar(requestConEscala("obj concurrente"), userId, userEmail);
                parametrizacionesCreadas.add(dto.id());
            } catch (Exception e) {
                errores.add(e);
            } finally {
                salida.countDown();
            }
        };

        pool.submit(tarea);
        pool.submit(tarea);
        assertTrue(salida.await(30, TimeUnit.SECONDS), "Ambas peticiones concurrentes deben terminar");
        pool.shutdown();

        assertTrue(errores.isEmpty(), "Ninguna de las dos peticiones concurrentes debe fallar: " + errores);

        List<?> historial = parametrizacionRepo.findHistorialVersiones(METRICA_FAT, proyectoId);
        assertEquals(1, historial.size(),
                "Dos envíos concurrentes con el mismo contenido NO deben dejar dos filas 'pendiente' "
                + "(este es exactamente el defecto reportado: duplicados en Verificación)");
    }

    // ── 4: contenido realmente distinto sigue creando una nueva versión legítima ──

    @Test
    void contenidoRealmenteDistinto_creaUnaVersionNuevaLegitima() {
        guardarYRegistrar("objetivo A");
        MetricParametrizacionDto v2 = guardarYRegistrar("objetivo B (editado)");

        assertEquals(2, v2.version(), "Un contenido realmente distinto debe crear la versión 2, no reutilizar la 1");
        List<?> historial = parametrizacionRepo.findHistorialVersiones(METRICA_FAT, proyectoId);
        assertEquals(2, historial.size());
    }

    // ── 5 y 7: una versión aprobada no bloquea una nueva versión legítima,
    //           y la aprobación sigue creando/actualizando la Variable vigente ──

    @Test
    void versionAprobada_noBloqueaNuevaVersion_yLaAprobacionCreaLaVariableVigente() {
        MetricParametrizacionDto v1 = guardarYRegistrar("objetivo v1");

        rankingService.verificar(
                new VerificarParametrizacionRequest(v1.id(), "aprobar", null), userId, userEmail);

        List<Variable> vars = variableRepository.findByParametrizacionIdAndParametrizacionVersion(v1.id(), 1);
        assertEquals(1, vars.size(), "Aprobar debe materializar la Variable versionada de v1");
        assertEquals(BigDecimal.ZERO, vars.get(0).getEscalaMin());
        assertEquals(Boolean.TRUE, vars.get(0).getEscalaSinLimite());

        // Con v1 ya aprobada, un envío con contenido distinto debe seguir permitiendo
        // crear v2 pendiente (versionado legítimo no bloqueado por la corrección).
        MetricParametrizacionDto v2 = guardarYRegistrar("objetivo v2 (editado)");
        assertEquals(2, v2.version());
        assertEquals("pendiente", v2.status());

        List<?> historial = parametrizacionRepo.findHistorialVersiones(METRICA_FAT, proyectoId);
        assertEquals(2, historial.size());
    }
}
