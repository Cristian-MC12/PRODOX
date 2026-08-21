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

        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private GuardarParametrizacionRequest request() {
        return new GuardarParametrizacionRequest(
                null, "objetivo", "procedimiento", "indicador", "escala",
                null, proyectoId, metricaId,
                "SUMA", "SUMA(indicador)", "unidades", "fuente");
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
}
