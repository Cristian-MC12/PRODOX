// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.VerificarParametrizacionRequest;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.ProjectMember;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.MetricUsoRankingRepository;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de MetricRankingService — FASE 10.
 *
 * Cubren la corrección del bug crítico confirmado en FASE 9 (bloque 1): guardar() ya
 * NO busca "la parametrización del usuario" de forma global (findByUserIdAndMetricaId,
 * sin proyecto ni status) ni degrada una fila aprobada a "pendiente" — ahora versiona
 * por metricaId+proyectoId igual que ParametrizacionService.guardarPropuesta(), y nunca
 * reutiliza ni muta una fila existente.
 */
@ExtendWith(MockitoExtension.class)
class MetricRankingServiceTest {

    @Mock private MetricParametrizacionRepository parametrizacionRepo;
    @Mock private MetricUsoRankingRepository       rankingRepo;
    @Mock private FactorRepository                 factorRepo;
    @Mock private MetricaRepository                metricaRepo;
    @Mock private PlaneacionService                planeacionService;
    @Mock private VariableDinamicaService          variableDinamicaService;
    @Mock private ProjectMemberRepository          projectMemberRepo;

    // Corrección de duplicados en Verificación: guardarPorMetrica() ahora adquiere un
    // advisory lock de Postgres vía EntityManager.createNativeQuery(...) antes de leer
    // el historial. entityManager es un campo @PersistenceContext (no final, fuera del
    // constructor de Lombok) — en este test unitario, que construye el service a mano,
    // se inyecta por reflexión y se mockea la cadena createNativeQuery/setParameter/
    // getSingleResult para que guardarPorMetrica() no falle con NullPointerException.
    // Los tests reales de concurrencia (con el lock real de Postgres) están en
    // MetricRankingDuplicadoPendienteTest, contra la BD real — acá solo se evita que
    // el mock rompa por una dependencia que este test no necesita ejercitar.
    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;

    private MetricRankingService service;

    private UUID proyectoId;
    private UUID metricaId;
    private final String userId    = "user-1";
    private final String userEmail = "user1@example.com";
    private final String smUserId  = "sm-user-1";

    @BeforeEach
    void setUp() {
        service = new MetricRankingService(
                parametrizacionRepo, rankingRepo, factorRepo, metricaRepo,
                planeacionService, variableDinamicaService, projectMemberRepo,
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.getSingleResult()).thenReturn(null);

        proyectoId = UUID.randomUUID();
        metricaId  = UUID.randomUUID();

        // lenient: el nuevo test de propagación de NombreVariableInvalidoException
        // (FASE 17) nunca llega a invocar save(), a propósito — eso es justo lo que
        // prueba que la parametrización no queda persistida como "aprobada".
        lenient().when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Auditoría transversal: userId es miembro del proyecto (para las pruebas de
        // guardar()/versionado que no son sobre autorización) y smUserId es su Scrum
        // Master (para las pruebas de verificar() que no son sobre autorización).
        lenient().when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        lenient().when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, smUserId))
                .thenReturn(Optional.of(scrumMaster(proyectoId, smUserId)));
    }

    private ProjectMember scrumMaster(UUID proyectoId, String userId) {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(userId);
        m.setRol("scrum_master");
        return m;
    }

    private ProjectMember miembro(UUID proyectoId, String userId) {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(userId);
        m.setRol("scrum_member");
        return m;
    }

    private GuardarParametrizacionRequest request() {
        return request(null);
    }

    private GuardarParametrizacionRequest request(String frecuenciaCaptura) {
        return new GuardarParametrizacionRequest(
                null, "objetivo", "procedimiento", "indicador", "escala",
                null, proyectoId, metricaId,
                "SUMA", "SUMA(indicador)", "unidades", "fuente", frecuenciaCaptura, null, null, null, null, null, null);
    }

    private MetricParametrizacion aprobadaExistente(int version) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(version);
        p.setStatus("aprobada");
        p.setObjetivo("objetivo original aprobado");
        p.setProcedimiento("procedimiento original aprobado");
        p.setEscala("escala original");
        return p;
    }

    // ── A.1 ──────────────────────────────────────────────────────────────

    @Test
    void nuevaMetricaEnProyectoSinHistorial_creaVersion1PendienteYNoAlFactorRepo() {
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());

        MetricParametrizacionDto dto = service.guardar(request(), userId, userEmail);

        assertThat(dto.version()).isEqualTo(1);
        assertThat(dto.status()).isEqualTo("pendiente");
        verifyNoInteractions(factorRepo);
    }

    // ── A.2 ──────────────────────────────────────────────────────────────

    @Test
    void nuevaParametrizacionEnMismoProyecto_creaVersion2() {
        MetricParametrizacion v1 = aprobadaExistente(1);
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(v1)); // ordenado DESC por versión, como en el repo real

        MetricParametrizacionDto dto = service.guardar(request(), userId, userEmail);

        assertThat(dto.version()).isEqualTo(2);
        assertThat(dto.status()).isEqualTo("pendiente");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Revisión de frecuencia de captura — bug real reportado: la tarjeta de
    // Ejecución mostraba "Por sprint" para una métrica configurada como
    // "Diariamente" en Planeación. Causa raíz: GuardarParametrizacionRequest
    // no tenía el campo frecuenciaCaptura, y guardarPorMetrica() nunca lo
    // asignaba a la entidad — quedaba siempre en el default Java "por_sprint"
    // sin importar lo elegido en el formulario.
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void guardar_conFrecuenciaCapturaDiaria_laPersisteEnLaEntidad() {
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);

        service.guardar(request("diaria"), userId, userEmail);

        verify(parametrizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFrecuenciaCaptura()).isEqualTo("diaria");
    }

    @Test
    void guardar_conFrecuenciaCapturaPorSprint_laPersisteEnLaEntidad() {
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);

        service.guardar(request("por_sprint"), userId, userEmail);

        verify(parametrizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFrecuenciaCaptura()).isEqualTo("por_sprint");
    }

    @Test
    void guardar_conFrecuenciaCapturaIlimitada_laPersisteEnLaEntidad() {
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);

        service.guardar(request("ilimitada"), userId, userEmail);

        verify(parametrizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFrecuenciaCaptura()).isEqualTo("ilimitada");
    }

    @Test
    void guardar_sinFrecuenciaCapturaInformada_defaultAPorSprint_comportamientoPreexistenteSinCambios() {
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);

        service.guardar(request(null), userId, userEmail);

        verify(parametrizacionRepo).save(captor.capture());
        assertThat(captor.getValue().getFrecuenciaCaptura()).isEqualTo("por_sprint");
    }

    @Test
    void reenvioConMismaFrecuenciaYRestoIdentico_noCreaDuplicado() {
        MetricParametrizacion pendienteExistente = pendienteConContenidoDeRequest(1);
        pendienteExistente.setFrecuenciaCaptura("diaria");
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(pendienteExistente));

        MetricParametrizacionDto dto = service.guardar(request("diaria"), userId, userEmail);

        verify(parametrizacionRepo, never()).save(any(MetricParametrizacion.class));
        assertThat(dto.version()).isEqualTo(1);
    }

    @Test
    void reenvioConSoloLaFrecuenciaCambiada_siCreaVersionNueva() {
        // Corrección directa del bug: antes, esMismoContenido() no comparaba
        // frecuenciaCaptura, así que cambiar SOLO la frecuencia (todo lo demás
        // igual) se descartaba como "reenvío duplicado" y devolvía la versión
        // vieja sin el cambio — el mismo síntoma reportado, por una vía distinta.
        MetricParametrizacion pendienteExistente = pendienteConContenidoDeRequest(1);
        pendienteExistente.setFrecuenciaCaptura("por_sprint");
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(pendienteExistente));
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);

        MetricParametrizacionDto dto = service.guardar(request("diaria"), userId, userEmail);

        verify(parametrizacionRepo, times(1)).save(captor.capture());
        assertThat(dto.version()).isEqualTo(2);
        assertThat(captor.getValue().getFrecuenciaCaptura()).isEqualTo("diaria");
    }

    // ── A.3 ──────────────────────────────────────────────────────────────

    @Test
    void noConsultaNiReutilizaHistorialDeOtroProyecto() {
        UUID otroProyecto = UUID.randomUUID();
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of());

        service.guardar(request(), userId, userEmail);

        verify(parametrizacionRepo).findHistorialVersiones(metricaId, proyectoId);
        verify(parametrizacionRepo, never()).findHistorialVersiones(metricaId, otroProyecto);
        // FASE 10: guardar() ya no busca ninguna fila global por usuario.
        verify(parametrizacionRepo, never()).findByUserIdAndMetricaId(any(), any());
    }

    // ── FASE 20 (envío duplicado al Scrum Master) ───────────────────────────
    // Confirmado empíricamente: una recarga/navegación mientras el primer envío
    // sigue en curso deja una selección "completa" reenviable en el frontend
    // aunque el primer envío ya se haya guardado en el backend — el guard de
    // frontend (aceptar() con "if (this.enviando) return;") no puede prevenir
    // esto porque es una instancia de componente nueva. guardarPorMetrica() debe
    // reconocer que la última versión "pendiente" tiene el mismo contenido y
    // devolverla en vez de crear una fila idéntica nueva.

    private MetricParametrizacion pendienteConContenidoDeRequest(int version) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(version);
        p.setStatus("pendiente");
        p.setObjetivo("objetivo");
        p.setProcedimiento("procedimiento");
        p.setIndicadorVariable("indicador");
        p.setEscala("escala");
        p.setTipoOperacion("SUMA");
        p.setFormulaAcademica("SUMA(indicador)");
        p.setUnidadResultado("unidades");
        p.setFuenteAcademica("fuente");
        return p;
    }

    @Test
    void reenvioIdenticoSobreUnaPendienteExistente_noCreaDuplicado_devuelveLaExistente() {
        MetricParametrizacion pendienteExistente = pendienteConContenidoDeRequest(1);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(pendienteExistente)); // DESC por versión, como el repo real

        MetricParametrizacionDto dto = service.guardar(request(), userId, userEmail);

        verify(parametrizacionRepo, never()).save(any(MetricParametrizacion.class));
        assertThat(dto.version()).isEqualTo(1);
        assertThat(dto.status()).isEqualTo("pendiente");
    }

    @Test
    void dosEnviosIdenticosSeguidos_elSegundoNoCreaUnaFilaNueva() {
        // Simula el escenario real: primer guardar() exitoso deja una fila
        // "pendiente"; un segundo guardar() con el mismo contenido (el "reenvío
        // duplicado") debe detectarla y no crear una segunda.
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of()); // primer envío: sin historial todavía
        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> {
                    MetricParametrizacion p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        MetricParametrizacionDto primero = service.guardar(request(), userId, userEmail);
        assertThat(primero.version()).isEqualTo(1);

        // Segundo envío: ahora el historial SÍ contiene la fila recién creada.
        MetricParametrizacion creada = pendienteConContenidoDeRequest(1);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(creada));

        MetricParametrizacionDto segundo = service.guardar(request(), userId, userEmail);

        assertThat(segundo.version()).isEqualTo(1);
        // save() se llamó UNA sola vez en total (por el primer envío legítimo).
        verify(parametrizacionRepo, times(1)).save(any(MetricParametrizacion.class));
    }

    @Test
    void reenvioConContenidoDistinto_siCreaVersionNuevaAunqueLaUltimaSigaPendiente() {
        MetricParametrizacion pendienteExistente = pendienteConContenidoDeRequest(1);
        pendienteExistente.setObjetivo("objetivo completamente distinto, edición real del usuario");
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(pendienteExistente));
        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MetricParametrizacionDto dto = service.guardar(request(), userId, userEmail);

        verify(parametrizacionRepo, times(1)).save(any(MetricParametrizacion.class));
        assertThat(dto.version()).isEqualTo(2);
    }

    @Test
    void reenvioIdenticoSobreVersionYaAprobada_siCreaVersionNueva() {
        // La protección de duplicados solo aplica a la última versión "pendiente":
        // reenviar el mismo contenido una vez que la anterior ya fue revisada
        // (aprobada) es un flujo legítimo (ver FASE 10, versionado inmutable),
        // no un doble clic accidental.
        MetricParametrizacion aprobadaIdentica = pendienteConContenidoDeRequest(1);
        aprobadaIdentica.setStatus("aprobada");
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(aprobadaIdentica));
        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MetricParametrizacionDto dto = service.guardar(request(), userId, userEmail);

        verify(parametrizacionRepo, times(1)).save(any(MetricParametrizacion.class));
        assertThat(dto.version()).isEqualTo(2);
    }

    // ── A.4 / A.5 ────────────────────────────────────────────────────────

    @Test
    void parametrizacionAprobadaExistente_noSeDegradaNiSeSobrescribe() {
        MetricParametrizacion v1 = aprobadaExistente(1);
        when(metricaRepo.existsById(metricaId)).thenReturn(true);
        when(parametrizacionRepo.findHistorialVersiones(metricaId, proyectoId))
                .thenReturn(List.of(v1));

        service.guardar(request(), userId, userEmail);

        // La fila aprobada original nunca se pasa a save(): guardar() solo inserta una nueva.
        ArgumentCaptor<MetricParametrizacion> captor = ArgumentCaptor.forClass(MetricParametrizacion.class);
        verify(parametrizacionRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isNotSameAs(v1);

        // El contenido y estado de v1 permanecen intactos en memoria (nunca se mutó).
        assertThat(v1.getStatus()).isEqualTo("aprobada");
        assertThat(v1.getObjetivo()).isEqualTo("objetivo original aprobado");
        assertThat(v1.getProcedimiento()).isEqualTo("procedimiento original aprobado");
        assertThat(v1.getEscala()).isEqualTo("escala original");
    }

    // ── B.6 ──────────────────────────────────────────────────────────────

    @Test
    void aprobar_materializaVariableVersionadaAntesDeAprobarEnPlaneacion() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("pendiente");
        p.setConfiguracionAprobadaJson("{\"nombreVariable\":\"pbi_aceptados\"}");

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        service.verificar(new VerificarParametrizacionRequest(p.getId(), "aprobar", null), smUserId, "sm@example.com");

        // FASE 10: la variable versionada se materializa ANTES de llamar a
        // planeacionService.aprobar() — así su chequeo de "ya existe variable" ve la
        // versionada y nunca genera la variable genérica duplicada (ver diagnóstico
        // FASE 9, bloques 3 y 9).
        InOrder orden = inOrder(variableDinamicaService, planeacionService);
        orden.verify(variableDinamicaService).materializarVariables(p);
        orden.verify(planeacionService).aprobar(proyectoId, metricaId, "sm@example.com");
        assertThat(p.getStatus()).isEqualTo("aprobada");
    }

    @Test
    void aprobar_desactivaLaVersionAprobadaAnteriorDeLaMismaMetricaYProyecto() {
        MetricParametrizacion anterior = aprobadaExistente(1);
        MetricParametrizacion nueva = new MetricParametrizacion();
        nueva.setId(UUID.randomUUID());
        nueva.setMetricaId(metricaId);
        nueva.setProyectoId(proyectoId);
        nueva.setVersion(2);
        nueva.setStatus("pendiente");
        nueva.setConfiguracionAprobadaJson("{\"nombreVariable\":\"pbi_aceptados\"}");

        when(parametrizacionRepo.findById(nueva.getId())).thenReturn(Optional.of(nueva));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.of(anterior));
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        service.verificar(new VerificarParametrizacionRequest(nueva.getId(), "aprobar", null), smUserId, "sm@example.com");

        assertThat(anterior.getStatus()).isEqualTo("inactiva");
        assertThat(nueva.getStatus()).isEqualTo("aprobada");
        verify(parametrizacionRepo).save(anterior);
    }

    // ── FASE 17 (corrección del defecto documentado) ────────────────────────
    // Antes, cualquier excepción de variableDinamicaService.materializarVariables()
    // se registraba en log y se ignoraba, dejando p.setStatus("aprobada") persistido
    // sin variable funcional. Ahora NombreVariableInvalidoException se relanza: en
    // el servicio real esto hace rollback de la transacción completa de verificar()
    // (@Transactional). A nivel de mock, lo verificable es que (a) la excepción
    // propaga fuera de verificar() y (b) el guardado final de p — el que persistiría
    // el status "aprobada" — nunca se ejecuta.

    @Test
    void aprobar_siIndicadorEsInvalido_propagaExcepcionYNoPersisteComoAprobada() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("pendiente");
        // Con Identificador técnico ya guardado: este test cubre un fallo DISTINTO
        // (el que lanza variableDinamicaService al materializar), no la nueva
        // obligatoriedad de nombreVariable agregada en Opción 1.
        p.setConfiguracionAprobadaJson("{\"nombreVariable\":\"pbi_aceptados\"}");

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        doThrow(new NombreVariableInvalidoException(
                "El campo \"Indicador y Variables\" contiene un valor de 121 caracteres, " +
                "que excede el máximo de 120 permitido."))
                .when(variableDinamicaService).materializarVariables(p);

        assertThatThrownBy(() ->
                service.verificar(new VerificarParametrizacionRequest(p.getId(), "aprobar", null),
                        smUserId, "sm@example.com"))
                .isInstanceOf(NombreVariableInvalidoException.class)
                .hasMessageContaining("Indicador y Variables");

        // planeacionService.aprobar() nunca se llega a invocar tras la falla.
        verifyNoInteractions(planeacionService);
        // El guardado final (el que persistiría status="aprobada") nunca ocurre.
        verify(parametrizacionRepo, never()).save(p);
    }

    @Test
    void aprobar_siMaterializarFallaConOtraExcepcion_siguePreservandoElComportamientoPrevio() {
        // Otros tipos de fallo (no relacionados con el defecto de longitud de indicador)
        // deben seguir tolerándose exactamente como antes: no deben convertirse en una
        // nueva regresión para escenarios ya conocidos y aceptados (ej. condiciones de
        // carrera benignas al aprobar en Planeación).
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("pendiente");
        p.setConfiguracionAprobadaJson("{\"nombreVariable\":\"pbi_aceptados\"}");

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("fallo no relacionado"))
                .when(variableDinamicaService).materializarVariables(p);

        MetricParametrizacionDto dto = service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), smUserId, "sm@example.com");

        assertThat(dto.status()).isEqualTo("aprobada");
        verify(parametrizacionRepo).save(p);
    }

    // ── D.16 / D.17 ──────────────────────────────────────────────────────

    @Test
    void rechazar_soloAfectaLaParametrizacionIndicadaYNoTocaOtraAprobada() {
        MetricParametrizacion aprobadaDeOtraVersion = aprobadaExistente(1);
        MetricParametrizacion aRechazar = new MetricParametrizacion();
        aRechazar.setId(UUID.randomUUID());
        aRechazar.setMetricaId(metricaId);
        aRechazar.setProyectoId(proyectoId);
        aRechazar.setVersion(2);
        aRechazar.setStatus("pendiente");

        when(parametrizacionRepo.findById(aRechazar.getId())).thenReturn(Optional.of(aRechazar));

        service.verificar(new VerificarParametrizacionRequest(aRechazar.getId(), "rechazar", "no cumple"),
                smUserId, "sm@example.com");

        assertThat(aRechazar.getStatus()).isEqualTo("rechazada");
        assertThat(aRechazar.getMotivoRechazo()).isEqualTo("no cumple");
        // Rechazar nunca debe consultar ni tocar la versión aprobada de la misma métrica.
        verify(parametrizacionRepo, never()).findUltimaVersionAprobada(any(), any());
        assertThat(aprobadaDeOtraVersion.getStatus()).isEqualTo("aprobada");
        verifyNoInteractions(variableDinamicaService, planeacionService);
    }

    // ── Corrección "Usar" del ranking: el DTO debe transportar la
    // parametrización COMPLETA (antes solo llevaba objetivo/procedimiento/
    // indicadorVariable/escala/usos/createdAt — el frontend no podía copiar
    // frecuenciaCaptura ni los campos académicos aunque quisiera, porque el
    // backend nunca los enviaba). ──────────────────────────────────────────

    private MetricParametrizacion parametrizacionCompleta() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("aprobada");
        p.setUserEmail(userEmail);
        p.setObjetivo("objetivo completo");
        p.setProcedimiento("procedimiento completo");
        p.setIndicadorVariable("indicador_completo");
        p.setEscala("Numérica 1-5");
        p.setFrecuenciaCaptura("semanal");
        p.setFuenteAcademica("Scrum Guide 2020");
        p.setFormulaAcademica("SUMA(indicador_completo)");
        p.setTipoOperacion("SUMA");
        p.setUnidadResultado("puntos");
        return p;
    }

    @Test
    void getTop3ByMetricaId_devuelveTodosLosCamposDeLaParametrizacion_noSoloObjetivo() {
        MetricParametrizacion completa = parametrizacionCompleta();
        when(parametrizacionRepo.findTop3ByMetricaId(metricaId)).thenReturn(List.of(completa));
        when(parametrizacionRepo.countByMetricaId(metricaId)).thenReturn(1L);

        var resultado = service.getTop3ByMetricaId(metricaId);

        assertThat(resultado).hasSize(1);
        var dto = resultado.get(0);
        assertThat(dto.objetivo()).isEqualTo("objetivo completo");
        assertThat(dto.procedimiento()).isEqualTo("procedimiento completo");
        assertThat(dto.indicadorVariable()).isEqualTo("indicador_completo");
        assertThat(dto.escala()).isEqualTo("Numérica 1-5");
        assertThat(dto.frecuenciaCaptura()).isEqualTo("semanal");
        assertThat(dto.fuenteAcademica()).isEqualTo("Scrum Guide 2020");
        assertThat(dto.formulaAcademica()).isEqualTo("SUMA(indicador_completo)");
        assertThat(dto.tipoOperacion()).isEqualTo("SUMA");
        assertThat(dto.unidadResultado()).isEqualTo("puntos");
    }

    @Test
    void getTop3_devuelveTodosLosCamposDeLaParametrizacion_noSoloObjetivo() {
        UUID factorId = UUID.randomUUID();
        MetricParametrizacion completa = parametrizacionCompleta();
        when(parametrizacionRepo.findTop3BaseByFactorId(factorId)).thenReturn(List.of(completa));
        when(rankingRepo.findAll()).thenReturn(List.of());

        var resultado = service.getTop3(factorId);

        assertThat(resultado).hasSize(1);
        var dto = resultado.get(0);
        assertThat(dto.frecuenciaCaptura()).isEqualTo("semanal");
        assertThat(dto.fuenteAcademica()).isEqualTo("Scrum Guide 2020");
        assertThat(dto.formulaAcademica()).isEqualTo("SUMA(indicador_completo)");
        assertThat(dto.tipoOperacion()).isEqualTo("SUMA");
        assertThat(dto.unidadResultado()).isEqualTo("puntos");
    }

    // Si el registro original tiene un campo realmente vacío (null), el DTO
    // debe conservarlo como null — nunca inventar un valor de relleno.
    @Test
    void getTop3ByMetricaId_camposRealmenteVaciosLlegan_comoNull_sinInventarValores() {
        MetricParametrizacion sinCamposAcademicos = parametrizacionCompleta();
        sinCamposAcademicos.setFuenteAcademica(null);
        sinCamposAcademicos.setFormulaAcademica(null);
        sinCamposAcademicos.setTipoOperacion(null);
        sinCamposAcademicos.setUnidadResultado(null);
        when(parametrizacionRepo.findTop3ByMetricaId(metricaId)).thenReturn(List.of(sinCamposAcademicos));
        when(parametrizacionRepo.countByMetricaId(metricaId)).thenReturn(1L);

        var dto = service.getTop3ByMetricaId(metricaId).get(0);

        assertThat(dto.fuenteAcademica()).isNull();
        assertThat(dto.formulaAcademica()).isNull();
        assertThat(dto.tipoOperacion()).isNull();
        assertThat(dto.unidadResultado()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Auditoría transversal de autorización — verificar()/getPendientesPorProyecto()/
    // getResumenPorProyecto()/guardar() no validaban membresía ni rol de Scrum Master
    // por proyecto: cualquier usuario autenticado podía aprobar/rechazar
    // parametrizaciones, ver pendientes o inyectar propuestas en cualquier proyecto
    // conociendo su UUID (o, para pendientes sin proyectoId, ver las de TODOS los
    // proyectos del sistema).
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("verificar: miembro (no Scrum Master) del proyecto lanza SecurityException")
    void verificar_miembroNoScrumMaster_lanzaSecurityException() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setStatus("pendiente");
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(miembro(proyectoId, userId)));

        assertThatThrownBy(() -> service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), userId, "user1@example.com"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(variableDinamicaService, planeacionService);
        verify(parametrizacionRepo, never()).save(any());
    }

    @Test
    @DisplayName("verificar: usuario externo al proyecto lanza SecurityException")
    void verificar_usuarioExterno_lanzaSecurityException() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setStatus("pendiente");
        String externoId = "externo-1";
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, externoId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), externoId, "externo@example.com"))
                .isInstanceOf(SecurityException.class);

        verify(parametrizacionRepo, never()).save(any());
    }

    @Test
    @DisplayName("getPendientesPorProyecto: Scrum Master del proyecto obtiene la lista")
    void getPendientesPorProyecto_scrumMaster_retornaLista() {
        MetricParametrizacion p = pendienteConContenidoDeRequest(1);
        when(parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente")).thenReturn(List.of(p));

        var resultado = service.getPendientesPorProyecto(proyectoId, smUserId);

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("getPendientesPorProyecto: miembro (no Scrum Master) del proyecto lanza SecurityException")
    void getPendientesPorProyecto_miembroNoScrumMaster_lanzaSecurityException() {
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(miembro(proyectoId, userId)));

        assertThatThrownBy(() -> service.getPendientesPorProyecto(proyectoId, userId))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    @DisplayName("getPendientesPorProyecto: usuario externo lanza SecurityException")
    void getPendientesPorProyecto_usuarioExterno_lanzaSecurityException() {
        String externoId = "externo-1";
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPendientesPorProyecto(proyectoId, externoId))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(parametrizacionRepo);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Corrección de aislamiento de Verificación: getPendientesPorProyecto()
    // devolvía TODAS las parametrizaciones con proyecto_id NULL cuando no se
    // informaba proyectoId (pantalla de Verificación sin proyecto activo en
    // localStorage) — permitiendo que propuestas huérfanas/históricas de
    // CUALQUIER usuario aparecieran como si fueran del proyecto que el Scrum
    // Master está revisando. Regla nueva: sin proyectoId explícito, lista
    // vacía siempre (nunca se infiere una asociación con datos huérfanos).
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getPendientesPorProyecto: sin proyectoId, devuelve lista vacía y no consulta el repositorio (C)")
    void getPendientesPorProyecto_sinProyectoId_devuelveListaVacia() {
        var resultado = service.getPendientesPorProyecto(null, smUserId);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    @DisplayName("getPendientesPorProyecto: con proyectoId, excluye las de otro proyecto y las huérfanas proyecto_id=NULL (A, B, D)")
    void getPendientesPorProyecto_conProyectoId_excluyeOtroProyectoYHuerfanas() {
        UUID otroProyecto = UUID.randomUUID();
        MetricParametrizacion pendienteDelProyectoActivo = pendienteConContenidoDeRequest(1); // proyectoId = "A"

        MetricParametrizacion pendienteDeOtroProyecto = new MetricParametrizacion();
        pendienteDeOtroProyecto.setId(UUID.randomUUID());
        pendienteDeOtroProyecto.setMetricaId(metricaId);
        pendienteDeOtroProyecto.setProyectoId(otroProyecto); // proyecto "B"
        pendienteDeOtroProyecto.setStatus("pendiente");

        MetricParametrizacion pendienteHuerfana = new MetricParametrizacion();
        pendienteHuerfana.setId(UUID.randomUUID());
        pendienteHuerfana.setMetricaId(metricaId);
        pendienteHuerfana.setProyectoId(null); // huérfana histórica, sin proyecto
        pendienteHuerfana.setStatus("pendiente");

        when(parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente"))
                .thenReturn(List.of(pendienteDelProyectoActivo, pendienteDeOtroProyecto, pendienteHuerfana));

        var resultado = service.getPendientesPorProyecto(proyectoId, smUserId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).proyectoId()).isEqualTo(proyectoId);
    }

    @Test
    @DisplayName("getResumenPorProyecto: miembro del proyecto obtiene el resumen")
    void getResumenPorProyecto_miembroDelProyecto_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(parametrizacionRepo.countByProyectoIdAndStatus(any(), any())).thenReturn(0L);

        var resumen = service.getResumenPorProyecto(proyectoId, userId);

        assertThat(resumen).isNotNull();
    }

    @Test
    @DisplayName("getResumenPorProyecto: usuario externo lanza SecurityException")
    void getResumenPorProyecto_usuarioExterno_lanzaSecurityException() {
        String externoId = "externo-1";
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        assertThatThrownBy(() -> service.getResumenPorProyecto(proyectoId, externoId))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    @DisplayName("guardar: usuario externo al proyecto lanza SecurityException")
    void guardar_usuarioExterno_lanzaSecurityException() {
        String externoId = "externo-1";
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        assertThatThrownBy(() -> service.guardar(request(), externoId, "externo@example.com"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(parametrizacionRepo);
    }

    // ══════════════════════════════════════════════════════════════════════
    // OPCIÓN 1 — Identificador técnico (nombreVariable) explícito y obligatorio.
    // Reemplaza la derivación automática desde indicadorVariable: el usuario debe
    // indicarlo al editar, y aprobar() lo exige antes de materializar la variable.
    // Cubre longitud límite (120/121), obligatoriedad (vacío/ausente), independencia
    // del texto descriptivo (indicadorVariable largo no se toca ni se trunca) y el
    // flujo completo de aprobación con un identificador ya guardado.
    // ══════════════════════════════════════════════════════════════════════

    private MetricParametrizacion pendienteEditable() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(UUID.randomUUID());
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("pendiente");
        p.setObjetivo("objetivo original");
        p.setProcedimiento("procedimiento original");
        p.setIndicadorVariable("indicador original");
        p.setEscala("escala original");
        return p;
    }

    private com.mpdia.dto.ActualizarParametrizacionRequest actualizarRequest(
            String indicadorVariable, String nombreVariable) {
        return new com.mpdia.dto.ActualizarParametrizacionRequest(
                "objetivo", "procedimiento", indicadorVariable, "escala",
                null, null, null, null, null, null,
                nombreVariable);
    }

    @Test
    @DisplayName("actualizar: nombreVariable válido (<=120) se guarda en el snapshot")
    void actualizar_nombreVariableValido_seGuarda() {
        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));

        MetricParametrizacionDto dto = service.actualizar(
                p.getId(), actualizarRequest("indicador corto", "pbi_aceptados"), smUserId);

        assertThat(dto).isNotNull();
        assertThat(p.getConfiguracionAprobadaJson()).contains("\"nombreVariable\":\"pbi_aceptados\"");
        verify(parametrizacionRepo).save(p);
    }

    @Test
    @DisplayName("actualizar: nombreVariable de exactamente 120 caracteres se acepta")
    void actualizar_nombreVariableDe120_seAcepta() {
        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        String nombre120 = "a".repeat(120); // cumple ^[a-z][a-z0-9_]{0,119}$
        assertThat(nombre120).hasSize(120);

        service.actualizar(p.getId(), actualizarRequest("indicador", nombre120), smUserId);

        assertThat(p.getConfiguracionAprobadaJson())
                .contains("\"nombreVariable\":\"" + nombre120 + "\"");
    }

    @Test
    @DisplayName("actualizar: nombreVariable de 121 caracteres NO se rechaza — se genera uno seguro de máx. 120")
    void actualizar_nombreVariableDe121_generaUnoSeguroEnVezDeRechazar() {
        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        String nombre121 = "a".repeat(121);

        MetricParametrizacionDto dto = service.actualizar(
                p.getId(), actualizarRequest("indicador", nombre121), smUserId);

        assertThat(dto).isNotNull();
        String guardado = leerNombreVariableDeSnapshot(p);
        assertThat(guardado).isNotBlank();
        assertThat(guardado.length()).isLessThanOrEqualTo(120);
        assertThat(guardado).matches("^[a-z][a-z0-9_]{0,119}$");
        verify(parametrizacionRepo).save(p);
    }

    @Test
    @DisplayName("actualizar: nombreVariable vacío NO se rechaza — se genera desde indicadorVariable")
    void actualizar_nombreVariableVacio_generaDesdeIndicador() {
        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));

        service.actualizar(p.getId(), actualizarRequest("impedimentos del sprint", ""), smUserId);

        String guardado = leerNombreVariableDeSnapshot(p);
        assertThat(guardado).isNotBlank();
        assertThat(guardado).matches("^[a-z][a-z0-9_]{0,119}$");
    }

    @Test
    @DisplayName("actualizar: nombreVariable ausente (null) también genera desde indicadorVariable, no se rechaza")
    void actualizar_nombreVariableNull_generaDesdeIndicador() {
        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));

        service.actualizar(p.getId(), actualizarRequest("impedimentos del sprint", null), smUserId);

        String guardado = leerNombreVariableDeSnapshot(p);
        assertThat(guardado).isNotBlank();
        assertThat(guardado).matches("^[a-z][a-z0-9_]{0,119}$");
        verify(parametrizacionRepo).save(p);
    }

    @Test
    @DisplayName("aprobar: indicadorVariable largo con nombreVariable válido se aprueba correctamente, sin truncar el indicador")
    void aprobar_conIndicadorLargoYNombreVariableValido_apruebaCorrectamenteSinTruncar() {
        String indicadorLargo = "Numero de historias de usuario aceptadas por el Product Owner "
                + "sin necesidad de retrabajo durante el sprint, medido sobre el total de "
                + "historias comprometidas en la planificacion inicial del sprint en curso";
        assertThat(indicadorLargo.length()).isGreaterThan(120);

        MetricParametrizacion p = pendienteEditable();
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));

        // 1) Editar: fija un identificador técnico corto, independiente del indicador largo.
        service.actualizar(p.getId(), actualizarRequest(indicadorLargo, "pbi_aceptados"), smUserId);

        // El indicador descriptivo se guarda TAL CUAL — nunca se trunca ni se recorta.
        assertThat(p.getIndicadorVariable()).isEqualTo(indicadorLargo);
        assertThat(p.getIndicadorVariable().length()).isGreaterThan(120);

        // 2) Aprobar: ya no depende de derivar nada de indicadorVariable — usa el
        // nombreVariable ya guardado en el paso anterior.
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        MetricParametrizacionDto dto = service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null),
                smUserId, "sm@example.com");

        assertThat(dto.status()).isEqualTo("aprobada");
        // El indicador descriptivo sigue intacto también después de aprobar.
        assertThat(p.getIndicadorVariable()).isEqualTo(indicadorLargo);
        verify(variableDinamicaService).materializarVariables(p);
        verify(planeacionService).aprobar(proyectoId, metricaId, "sm@example.com");
    }

    @Test
    @DisplayName("aprobar: sin Identificador técnico guardado (nunca editado), NO se rechaza — se genera uno seguro y se aprueba")
    void aprobar_sinNombreVariableGuardado_generaUnoSeguroYAprueba() {
        MetricParametrizacion p = pendienteEditable(); // nunca editado: sin configuracionAprobadaJson
        p.setIndicadorVariable("Numero de impedimentos reportados durante el sprint");

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        MetricParametrizacionDto dto = service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), smUserId, "sm@example.com");

        assertThat(dto.status()).isEqualTo("aprobada");
        String guardado = leerNombreVariableDeSnapshot(p);
        assertThat(guardado).isNotBlank();
        assertThat(guardado).matches("^[a-z][a-z0-9_]{0,119}$");
        verify(variableDinamicaService).materializarVariables(p);
    }

    @Test
    @DisplayName("aprobar: indicadorVariable muy largo (caso PBI) se aprueba sin error 400, sin truncar el indicador")
    void aprobar_conIndicadorMuyLargoSinNombreVariableExplicito_generaIdentificadorCortoYNoTrunca() {
        // Una sola oración continua (sin comas): con coma, extraerNombresVariables()
        // la interpretaría como una lista de variables separadas (soporte existente
        // para métricas FORMULA de más de una variable, sin relación con este caso) y
        // cada segmento tendría su propio límite de 120 en vez de uno solo total.
        String indicadorLargo = "Sumar todos los Product Backlog Items aceptados por el Product Owner "
                + "que cumplen la Definicion de Terminado al cierre del Sprint sin incluir "
                + "aquellos que requirieron retrabajo posterior ni los que fueron reabiertos "
                + "durante la revision de sprint";
        assertThat(indicadorLargo.length()).isGreaterThan(120);

        MetricParametrizacion p = pendienteEditable();
        p.setIndicadorVariable(indicadorLargo);
        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        // No debe lanzar ninguna excepción (ni 400 por longitud de nombreVariable).
        MetricParametrizacionDto dto = service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), smUserId, "sm@example.com");

        assertThat(dto.status()).isEqualTo("aprobada");
        // El indicador original se conserva completo, sin truncar.
        assertThat(p.getIndicadorVariable()).isEqualTo(indicadorLargo);
        String guardado = leerNombreVariableDeSnapshot(p);
        assertThat(guardado).isNotBlank();
        assertThat(guardado.length()).isLessThanOrEqualTo(120);
        assertThat(guardado).matches("^[a-z][a-z0-9_]{0,119}$");
        verify(variableDinamicaService).materializarVariables(p);
    }

    @Test
    @DisplayName("El identificador generado automáticamente es determinista: la misma descripción larga siempre produce el mismo identificador")
    void generarNombreVariableSeguro_esDeterminista_mismaEntradaMismoResultado() {
        String textoLargo = "a".repeat(200);

        MetricParametrizacion p1 = pendienteEditable();
        p1.setId(UUID.randomUUID());
        when(parametrizacionRepo.findById(p1.getId())).thenReturn(Optional.of(p1));
        service.actualizar(p1.getId(), actualizarRequest("indicador", textoLargo), smUserId);

        MetricParametrizacion p2 = pendienteEditable();
        p2.setId(UUID.randomUUID());
        when(parametrizacionRepo.findById(p2.getId())).thenReturn(Optional.of(p2));
        service.actualizar(p2.getId(), actualizarRequest("indicador", textoLargo), smUserId);

        assertThat(leerNombreVariableDeSnapshot(p1)).isEqualTo(leerNombreVariableDeSnapshot(p2));
    }

    @Test
    @DisplayName("Dos indicadores distintos que comparten el mismo prefijo largo generan identificadores distintos (no es un simple substring(0,120))")
    void generarNombreVariableSeguro_evitaColisionesEntreTextosConElMismoPrefijo() {
        String prefijoComun = "palabra ".repeat(20); // > 120 caracteres de prefijo compartido
        String textoA = prefijoComun + "final version alfa";
        String textoB = prefijoComun + "final version beta";

        MetricParametrizacion pA = pendienteEditable();
        pA.setId(UUID.randomUUID());
        when(parametrizacionRepo.findById(pA.getId())).thenReturn(Optional.of(pA));
        service.actualizar(pA.getId(), actualizarRequest("indicador", textoA), smUserId);

        MetricParametrizacion pB = pendienteEditable();
        pB.setId(UUID.randomUUID());
        when(parametrizacionRepo.findById(pB.getId())).thenReturn(Optional.of(pB));
        service.actualizar(pB.getId(), actualizarRequest("indicador", textoB), smUserId);

        assertThat(leerNombreVariableDeSnapshot(pA)).isNotEqualTo(leerNombreVariableDeSnapshot(pB));
    }

    private String leerNombreVariableDeSnapshot(MetricParametrizacion p) {
        try {
            return new ObjectMapper().readTree(p.getConfiguracionAprobadaJson())
                    .get("nombreVariable").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
