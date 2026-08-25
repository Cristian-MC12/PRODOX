// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.PosibleDuplicadoDto;
import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricaCategoria;
import com.mpdia.repository.MetricaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * FASE 23 — Tests de MetricaSimilitudService, la capa ADICIONAL (nunca un
 * reemplazo) del chequeo de nombre exacto existente. Los datos de este test
 * (nombres/descripciones) reproducen el caso real que motivó la fase: el
 * catálogo del proyecto "Sandbox FASE 21" ya tiene varias métricas de
 * "ánimo/clima del equipo" creadas con nombres distintos (IA-001..IA-007).
 */
@ExtendWith(MockitoExtension.class)
class MetricaSimilitudServiceTest {

    @Mock private MetricaRepository metricaRepository;

    private MetricaSimilitudService service;

    @BeforeEach
    void setUp() {
        service = new MetricaSimilitudService(metricaRepository);
    }

    private MetricaCategoria categoria(short id, String nombre) {
        MetricaCategoria c = new MetricaCategoria();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private Metrica metrica(String codigo, String nombre, String descripcion, MetricaCategoria categoria) {
        Metrica m = new Metrica();
        m.setId(UUID.randomUUID());
        m.setCodigo(codigo);
        m.setNombre(nombre);
        m.setDescripcion(descripcion);
        m.setCategoria(categoria);
        return m;
    }

    // Catálogo real usado como fixture: 4 variantes de "ánimo del equipo" y 3
    // métricas claramente distintas (control negativo), todas categoría "Significado"
    // salvo Defectos ("Impacto") — igual que en la base real que motivó la fase.
    private List<Metrica> catalogoRealista() {
        MetricaCategoria significado = categoria((short) 1, "Significado");
        MetricaCategoria impacto = categoria((short) 3, "Impacto");
        return List.of(
                metrica("IA-001", "Clima Emocional del Equipo (editado por SM)",
                        "Medición del sentimiento colectivo del equipo respecto a su bienestar y satisfacción.",
                        significado),
                metrica("IA-002", "Pulso de Ánimo del Equipo",
                        "Mide la percepción general del equipo sobre su estado de ánimo, bienestar y satisfacción.",
                        significado),
                metrica("IA-007", "Estado de ánimo del equipo",
                        "Refleja la percepción colectiva del bienestar y satisfacción de los miembros del equipo.",
                        significado),
                metrica("IMP-CAL-01", "Defectos encontrados",
                        "Total de defectos encontrados durante el sprint antes de la entrega.",
                        impacto),
                metrica("SIG-CE-01", "Capacidad del equipo",
                        "Capacidad general del equipo para afrontar el trabajo planificado en el sprint.",
                        significado),
                metrica("SIG-VEL-01", "Velocidad",
                        "Story points completados por el equipo durante el sprint.",
                        significado)
        );
    }

    // ── Test 2 (obligatorio): nombre distinto pero concepto claramente equivalente ──

    @Test
    void nombreDistintoPeroConceptoEquivalente_devuelvePosibleDuplicado() {
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(catalogoRealista());

        List<PosibleDuplicadoDto> resultado = service.buscarPosiblesDuplicados(
                "Estado de ánimo", null, (short) 1, null, null, null);

        assertThat(resultado).isNotEmpty();
        assertThat(resultado).anyMatch(c -> c.metrica().codigo().equals("IA-007"));
        assertThat(resultado.get(0).razones()).isNotEmpty();
    }

    // ── Test 3 (obligatorio): variantes conceptualmente similares (clima vs ánimo) ──

    @Test
    void climaEmocionalVsEstadoDeAnimo_devuelvePosibleDuplicado() {
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(catalogoRealista());

        List<PosibleDuplicadoDto> resultado = service.buscarPosiblesDuplicados(
                "Clima emocional del equipo",
                "Mide el bienestar y la satisfacción del equipo respecto a su clima emocional.",
                (short) 1, null, null, null);

        assertThat(resultado).anyMatch(c -> c.metrica().codigo().equals("IA-007"));
        PosibleDuplicadoDto match = resultado.stream()
                .filter(c -> c.metrica().codigo().equals("IA-007")).findFirst().orElseThrow();
        // Explicable: el motivo no puede depender solo de que compartan una palabra del nombre.
        assertThat(match.razones()).isNotEmpty();
        assertThat(match.score()).isGreaterThanOrEqualTo(MetricaSimilitudService.UMBRAL_POSIBLE_DUPLICADO);
    }

    // ── Test 4 (obligatorio): métricas genuinamente distintas nunca se marcan ──

    @Test
    void metricasGenuinamenteDistintas_noSeMarcanComoDuplicadas() {
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(catalogoRealista());

        List<PosibleDuplicadoDto> resultado = service.buscarPosiblesDuplicados(
                "Defectos encontrados",
                "Total de defectos encontrados durante el sprint antes de la entrega.",
                (short) 3, null, null, null);

        assertThat(resultado).noneMatch(c -> c.metrica().codigo().equals("IA-007"));
        assertThat(resultado).noneMatch(c -> c.metrica().codigo().equals("IA-001"));
        assertThat(resultado).noneMatch(c -> c.metrica().codigo().equals("IA-002"));
    }

    @Test
    void catalogoVacio_nuncaLanzaNiMarcaNada() {
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of());

        List<PosibleDuplicadoDto> resultado = service.buscarPosiblesDuplicados(
                "Estado de ánimo", "desc", (short) 1, null, null, null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void soloCategoriaEnComun_sinNombreNiDescripcionParecidos_noEsSuficiente() {
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(catalogoRealista());

        // "Capacidad del equipo" comparte categoría con IA-007 pero no comparte
        // nombre ni descripción reales: la sola coincidencia de categoría no basta.
        List<PosibleDuplicadoDto> resultado = service.buscarPosiblesDuplicados(
                "Estado de ánimo del equipo",
                "Refleja la percepción colectiva del bienestar y satisfacción de los miembros del equipo.",
                (short) 1, null, null, null);

        assertThat(resultado).noneMatch(c -> c.metrica().codigo().equals("SIG-CE-01"));
        assertThat(resultado).noneMatch(c -> c.metrica().codigo().equals("SIG-VEL-01"));
    }
}
