// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.VerificarParametrizacionRequest;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.MetricUsoRankingRepository;
import com.mpdia.repository.MetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private MetricRankingService service;

    private UUID proyectoId;
    private UUID metricaId;
    private final String userId    = "user-1";
    private final String userEmail = "user1@example.com";

    @BeforeEach
    void setUp() {
        service = new MetricRankingService(
                parametrizacionRepo, rankingRepo, factorRepo, metricaRepo,
                planeacionService, variableDinamicaService);

        proyectoId = UUID.randomUUID();
        metricaId  = UUID.randomUUID();

        // lenient: el nuevo test de propagación de NombreVariableInvalidoException
        // (FASE 17) nunca llega a invocar save(), a propósito — eso es justo lo que
        // prueba que la parametrización no queda persistida como "aprobada".
        lenient().when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private GuardarParametrizacionRequest request() {
        return request(null);
    }

    private GuardarParametrizacionRequest request(String frecuenciaCaptura) {
        return new GuardarParametrizacionRequest(
                null, "objetivo", "procedimiento", "indicador", "escala",
                null, proyectoId, metricaId,
                "SUMA", "SUMA(indicador)", "unidades", "fuente", frecuenciaCaptura);
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

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        service.verificar(new VerificarParametrizacionRequest(p.getId(), "aprobar", null), "sm@example.com");

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

        when(parametrizacionRepo.findById(nueva.getId())).thenReturn(Optional.of(nueva));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.of(anterior));
        when(planeacionService.listarSeleccionadas(proyectoId)).thenReturn(List.of());

        service.verificar(new VerificarParametrizacionRequest(nueva.getId(), "aprobar", null), "sm@example.com");

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

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        doThrow(new NombreVariableInvalidoException(
                "El campo \"Indicador y Variables\" contiene un valor de 121 caracteres, " +
                "que excede el máximo de 120 permitido."))
                .when(variableDinamicaService).materializarVariables(p);

        assertThatThrownBy(() ->
                service.verificar(new VerificarParametrizacionRequest(p.getId(), "aprobar", null),
                        "sm@example.com"))
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

        when(parametrizacionRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("fallo no relacionado"))
                .when(variableDinamicaService).materializarVariables(p);

        MetricParametrizacionDto dto = service.verificar(
                new VerificarParametrizacionRequest(p.getId(), "aprobar", null), "sm@example.com");

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
                "sm@example.com");

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
}
