// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.VariableDto;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricaCategoria;
import com.mpdia.entity.ProyectoMetrica;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.ProyectoMetricaRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cubre el bug conceptual encontrado en la reorganización PRODOX AI:
 * autoAprobarMetricasConParametrizacion() marcaba una métrica como aprobada
 * con que existiera CUALQUIER parametrización asociada, sin mirar su status
 * ('propuesta'/'rechazada' contaban igual que 'aprobada'). Una métrica solo
 * debe pasar a estar activa en el catálogo del proyecto cuando su
 * parametrización fue efectivamente aprobada por el Scrum Master.
 */
@ExtendWith(MockitoExtension.class)
class PlaneacionServiceTest {

    @Mock private MetricaRepository metricaRepo;
    @Mock private ProyectoMetricaRepository pmRepo;
    @Mock private VariableRepository variableRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private MetricParametrizacionRepository parametrizacionRepo;

    private PlaneacionService service;

    private UUID proyectoId;
    private UUID metricaId;

    @BeforeEach
    void setUp() {
        service = new PlaneacionService(metricaRepo, pmRepo, variableRepo, proyectoRepo, parametrizacionRepo);
        proyectoId = UUID.randomUUID();
        metricaId = UUID.randomUUID();
    }

    private Metrica metrica() {
        MetricaCategoria cat = new MetricaCategoria();
        cat.setId((short) 1);
        cat.setNombre("Impacto");

        Metrica m = new Metrica();
        m.setId(metricaId);
        m.setCategoria(cat);
        m.setNombre("Métrica de prueba");
        return m;
    }

    private ProyectoMetrica seleccionadaNoAprobada() {
        ProyectoMetrica pm = new ProyectoMetrica();
        pm.getId().setProyectoId(proyectoId);
        pm.getId().setMetricaId(metricaId);
        pm.setMetrica(metrica());
        pm.setAprobada(false);
        return pm;
    }

    // ── Parametrización APROBADA → sí se activa en el catálogo ─────────────
    @Test
    void sincronizarVariables_parametrizacionAprobada_autoApruebaYGeneraVariable() {
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of(seleccionadaNoAprobada()));
        when(parametrizacionRepo.existsByMetricaIdAndProyectoIdAndStatus(metricaId, proyectoId, "aprobada"))
                .thenReturn(true);
        when(pmRepo.findByIdProyectoIdAndAprobadaTrue(proyectoId))
                .thenReturn(List.of(seleccionadaNoAprobada())); // tras el save, se relee como aprobada
        when(variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)).thenReturn(false);
        when(variableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VariableDto> resultado = service.sincronizarVariables(proyectoId);

        verify(parametrizacionRepo).existsByMetricaIdAndProyectoIdAndStatus(metricaId, proyectoId, "aprobada");
        verify(pmRepo, atLeastOnce()).save(argThat(pm -> pm.getAprobada() == Boolean.TRUE));
        verify(variableRepo, atLeastOnce()).save(any());
        assertThat(resultado).isNotNull();
    }

    // ── Parametrización PENDIENTE ('propuesta') → NO debe activarse ────────
    @Test
    void sincronizarVariables_parametrizacionPropuesta_noAutoApruebaNiGeneraVariable() {
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of(seleccionadaNoAprobada()));
        when(parametrizacionRepo.existsByMetricaIdAndProyectoIdAndStatus(metricaId, proyectoId, "aprobada"))
                .thenReturn(false); // existe una parametrización, pero en estado 'propuesta'
        when(pmRepo.findByIdProyectoIdAndAprobadaTrue(proyectoId)).thenReturn(List.of());

        service.sincronizarVariables(proyectoId);

        verify(pmRepo, never()).save(any());
        verify(variableRepo, never()).save(any());
    }

    // ── Parametrización RECHAZADA → NO debe activarse ───────────────────────
    @Test
    void sincronizarVariables_parametrizacionRechazada_noAutoApruebaNiGeneraVariable() {
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of(seleccionadaNoAprobada()));
        when(parametrizacionRepo.existsByMetricaIdAndProyectoIdAndStatus(metricaId, proyectoId, "aprobada"))
                .thenReturn(false); // la única parametrización existente está 'rechazada'
        when(pmRepo.findByIdProyectoIdAndAprobadaTrue(proyectoId)).thenReturn(List.of());

        service.sincronizarVariables(proyectoId);

        verify(pmRepo, never()).save(any());
        verify(variableRepo, never()).save(any());
    }

    // ── listarMetricasConEstado: una métrica sin parametrización aprobada no
    // debe figurar marcada como aprobada en el catálogo visible del proyecto ──
    @Test
    void listarMetricasConEstado_metricaSinAprobar_noApareceComoAprobada() {
        when(metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of(metrica()));
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of(seleccionadaNoAprobada()));
        when(variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)).thenReturn(false);

        var resultado = service.listarMetricasConEstado(proyectoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).aprobada()).isFalse();
    }

    // ── Catálogo global por proyecto: Disponible/Seleccionada y aislamiento ──
    // (revisión post-implementación — mejora de UX sobre el catálogo global)

    // Caso 1: el proyecto no seleccionó la métrica todavía → aparece DISPONIBLE
    // (seleccionada=false), aunque la Metrica ya exista en el catálogo global.
    @Test
    void listarMetricasConEstado_caso1_proyectoSinSeleccionarLaMetrica_apareceDisponible() {
        when(metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of(metrica()));
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of()); // nada seleccionado aún
        when(variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)).thenReturn(false);

        var resultado = service.listarMetricasConEstado(proyectoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).seleccionada()).isFalse();
    }

    // Caso 2: el proyecto ya la seleccionó → aparece SELECCIONADA.
    @Test
    void listarMetricasConEstado_caso2_proyectoYaSeleccionoLaMetrica_apareceSeleccionada() {
        when(metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of(metrica()));
        when(pmRepo.findByIdProyectoId(proyectoId)).thenReturn(List.of(seleccionadaNoAprobada()));
        when(variableRepo.existsByProyectoIdAndMetrica_Id(proyectoId, metricaId)).thenReturn(false);

        var resultado = service.listarMetricasConEstado(proyectoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).seleccionada()).isTrue();
    }

    // Casos 3/4/7: la MISMA Metrica global, consultada para dos proyectos
    // distintos con estados de selección independientes — el estado de A
    // nunca se filtra hacia B ni viceversa. Tras seleccionarla, B también la
    // ve como Seleccionada sin afectar la vista de A (llamadas independientes,
    // cada una scopeada solo a su propio proyectoId).
    @Test
    void listarMetricasConEstado_casos3y4y7_dosProyectosDistintos_estadoDeSeleccionAislado() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        Metrica metricaGlobal = metrica();

        ProyectoMetrica seleccionA = new ProyectoMetrica();
        seleccionA.getId().setProyectoId(proyectoA);
        seleccionA.getId().setMetricaId(metricaId);
        seleccionA.setMetrica(metricaGlobal);
        seleccionA.setAprobada(false);

        when(metricaRepo.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of(metricaGlobal));
        when(variableRepo.existsByProyectoIdAndMetrica_Id(any(), eq(metricaId))).thenReturn(false);

        // Proyecto A ya la seleccionó; Proyecto B todavía no.
        when(pmRepo.findByIdProyectoId(proyectoA)).thenReturn(List.of(seleccionA));
        when(pmRepo.findByIdProyectoId(proyectoB)).thenReturn(List.of());

        var vistaA = service.listarMetricasConEstado(proyectoA);
        var vistaB = service.listarMetricasConEstado(proyectoB);

        assertThat(vistaA.get(0).seleccionada()).isTrue();   // A: Seleccionada
        assertThat(vistaB.get(0).seleccionada()).isFalse();  // B: Disponible — NO bloqueada por A

        // Ahora B también la selecciona (no crea otra Metrica ni otra fila para B).
        ProyectoMetrica seleccionB = new ProyectoMetrica();
        seleccionB.getId().setProyectoId(proyectoB);
        seleccionB.getId().setMetricaId(metricaId);
        seleccionB.setMetrica(metricaGlobal);
        seleccionB.setAprobada(false);
        when(pmRepo.findByIdProyectoId(proyectoB)).thenReturn(List.of(seleccionB));

        var vistaBTrasSeleccionar = service.listarMetricasConEstado(proyectoB);
        var vistaANuevamente = service.listarMetricasConEstado(proyectoA);

        assertThat(vistaBTrasSeleccionar.get(0).seleccionada()).isTrue();  // B: ahora Seleccionada
        assertThat(vistaANuevamente.get(0).seleccionada()).isTrue();       // A: sigue Seleccionada, sin cambios
    }

    // Caso 5/6: seleccionar() nunca crea una segunda fila en Metrica (solo
    // lee, nunca guarda) y, si ya existe la asociación, no crea una
    // ProyectoMetrica duplicada (guard de idempotencia).
    @Test
    void seleccionar_metricaYaAsociada_esIdempotente_noCreaDuplicadoNiTocaMetrica() {
        when(pmRepo.existsByIdProyectoIdAndIdMetricaId(proyectoId, metricaId)).thenReturn(true);

        service.seleccionar(proyectoId, metricaId);

        verify(pmRepo, never()).save(any());
        verifyNoInteractions(metricaRepo); // ni siquiera se busca/lee la Metrica: no hace falta, ya está asociada
    }

    @Test
    void seleccionar_metricaNoAsociadaAun_creaUnaSolaProyectoMetrica_sinCrearMetricaNueva() {
        when(pmRepo.existsByIdProyectoIdAndIdMetricaId(proyectoId, metricaId)).thenReturn(false);
        when(metricaRepo.findById(metricaId)).thenReturn(java.util.Optional.of(metrica()));
        when(pmRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seleccionar(proyectoId, metricaId);

        verify(pmRepo, times(1)).save(any());
        verify(metricaRepo, never()).save(any()); // seleccionar() solo asocia, nunca crea Metrica
    }
}
