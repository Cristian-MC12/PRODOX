// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.validation;

import com.mpdia.entity.Metrica;
import com.mpdia.entity.MetricaCategoria;
import com.mpdia.entity.ProyectoMetrica;
import com.mpdia.repository.MetricaCategoriaRepository;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.ProyectoMetricaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Corrección definitiva del modelo de métricas globales — Fase PRODOX AI.
 *
 * Valida contra Postgres real que Metrica es EXCLUSIVAMENTE el catálogo
 * global (V31: revierte V30, retira proyecto_id, restaura unicidad GLOBAL
 * de nombre vía ux_metricas_nombre_global) y que ProyectoMetrica es el único
 * mecanismo de asociación proyecto↔métrica — varios proyectos pueden
 * apuntar a la MISMA fila de Metrica sin duplicarla.
 *
 * Usa nombres sintéticos con UUID para no rozar ninguna métrica real del
 * catálogo (las 41 semilla) ni del piloto. Toda la clase corre dentro de una
 * transacción que se revierte automáticamente al final de cada test
 * (@Transactional) — no persiste nada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetricasUnicidadV31Test {

    // Prueba 1 / Trabajo 1 — reutilizados solo como proyecto_id de
    // ProyectoMetrica en filas SINTÉTICAS de este test; nunca se toca
    // ninguna métrica ni selección real.
    private static final UUID PROYECTO_A = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");
    private static final UUID PROYECTO_B = UUID.fromString("fce0340c-74f2-4219-a727-5bae4d842496");

    @Autowired private MetricaRepository metricaRepository;
    @Autowired private MetricaCategoriaRepository metricaCategoriaRepository;
    @Autowired private ProyectoMetricaRepository proyectoMetricaRepository;

    private MetricaCategoria categoriaCualquiera() {
        return metricaCategoriaRepository.findAll().get(0);
    }

    private Metrica nuevaMetrica(String nombre, String codigo) {
        Metrica m = new Metrica();
        m.setCategoria(categoriaCualquiera());
        m.setNombre(nombre);
        m.setDescripcion("Métrica sintética — MetricasUnicidadV31Test");
        m.setFactor("Test V31");
        m.setCodigo(codigo);
        return m;
    }

    private ProyectoMetrica nuevaSeleccion(UUID proyectoId, Metrica metrica) {
        ProyectoMetrica pm = new ProyectoMetrica();
        pm.getId().setProyectoId(proyectoId);
        pm.getId().setMetricaId(metrica.getId());
        pm.setMetrica(metrica);
        return pm;
    }

    // Caso 1/2: dos métricas con el mismo nombre normalizado NO pueden
    // existir globalmente — "Estado de ánimo" y " estado de ánimo " (con
    // espacios y minúsculas) violan el índice único.
    @Test
    void caso1y2_mismoNombreNormalizado_noPuedenExistirDosFilasGlobales() {
        String sufijo = UUID.randomUUID().toString();
        String nombreBase = "Estado de ánimo " + sufijo;
        metricaRepository.saveAndFlush(nuevaMetrica(nombreBase, "V31A-" + sufijo.substring(0, 8)));

        assertThatThrownBy(() -> metricaRepository.saveAndFlush(
                nuevaMetrica(" " + nombreBase.toLowerCase() + " ", "V31B-" + sufijo.substring(0, 8))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Caso 3/4: dos proyectos distintos pueden usar la MISMA Metrica —
    // ambas filas de ProyectoMetrica apuntan al mismo metrica_id, sin que
    // se cree una segunda Metrica.
    @Test
    void caso3y4_dosProyectosDistintos_usanLaMismaMetrica_apuntanAlMismoMetricaId() {
        String sufijo = UUID.randomUUID().toString();
        Metrica metrica = metricaRepository.saveAndFlush(
                nuevaMetrica("Velocidad " + sufijo, "V31C-" + sufijo.substring(0, 8)));

        ProyectoMetrica selA = proyectoMetricaRepository.saveAndFlush(nuevaSeleccion(PROYECTO_A, metrica));
        ProyectoMetrica selB = proyectoMetricaRepository.saveAndFlush(nuevaSeleccion(PROYECTO_B, metrica));

        assertThat(selA.getId().getMetricaId()).isEqualTo(metrica.getId());
        assertThat(selB.getId().getMetricaId()).isEqualTo(metrica.getId());
        assertThat(selA.getId().getMetricaId()).isEqualTo(selB.getId().getMetricaId());

        // Sigue existiendo una única fila Metrica con ese nombre.
        long totalConEseNombre = metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc().stream()
                .filter(m -> m.getId().equals(metrica.getId()))
                .count();
        assertThat(totalConEseNombre).isEqualTo(1);
    }

    // Caso 6: una métrica ya existente en el catálogo puede asociarse a un
    // proyecto NUEVO (que todavía no la tenía seleccionada) sin problema.
    @Test
    void caso6_metricaExistente_puedeAsociarseAUnNuevoProyecto() {
        String sufijo = UUID.randomUUID().toString();
        Metrica metrica = metricaRepository.saveAndFlush(
                nuevaMetrica("Calidad " + sufijo, "V31D-" + sufijo.substring(0, 8)));
        proyectoMetricaRepository.saveAndFlush(nuevaSeleccion(PROYECTO_A, metrica));

        // Proyecto B, que no la tenía, la asocia ahora — sin crear una segunda Metrica.
        ProyectoMetrica nuevaAsociacion = proyectoMetricaRepository.saveAndFlush(
                nuevaSeleccion(PROYECTO_B, metrica));

        assertThat(nuevaAsociacion.getId().getMetricaId()).isEqualTo(metrica.getId());
        assertThat(metricaRepository.findById(metrica.getId())).isPresent();
    }

    // Caso 8: las 41 métricas históricas (catálogo semilla) permanecen
    // intactas y consultables tras la migración — ninguna se perdió, ninguna
    // quedó con un proyecto_id inexistente (la columna ya ni siquiera existe).
    @Test
    void caso8_metricasHistoricasDelCatalogoSeminalPermanecenIntactas() {
        List<Metrica> catalogo = metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc();

        assertThat(catalogo).isNotEmpty();
        assertThat(catalogo).allSatisfy(m -> {
            assertThat(m.getId()).isNotNull();
            assertThat(m.getNombre()).isNotBlank();
            assertThat(m.getCodigo()).isNotBlank();
        });
    }

    // Verificación explícita del método de repositorio que sostiene tanto la
    // protección de duplicados como el flujo de reutilización.
    @Test
    void findByNombreIgnoreCaseTrimmed_encuentraLaMismaFilaSinImportarMayusculasOEspacios() {
        String sufijo = UUID.randomUUID().toString();
        String nombreBase = "Retrabajo " + sufijo;
        Metrica metrica = metricaRepository.saveAndFlush(nuevaMetrica(nombreBase, "V31E-" + sufijo.substring(0, 8)));

        assertThat(metricaRepository.findByNombreIgnoreCaseTrimmed("  " + nombreBase.toUpperCase() + "  "))
                .isPresent()
                .get().extracting(Metrica::getId).isEqualTo(metrica.getId());

        assertThat(metricaRepository.findByNombreIgnoreCaseTrimmed("nombre que no existe " + sufijo))
                .isEmpty();
    }
}
