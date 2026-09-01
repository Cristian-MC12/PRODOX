// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ActualizarFormulaRequest;
import com.prodox.dto.CrearVariableRequest;
import com.prodox.dto.VariableDto;
import com.prodox.entity.Metrica;
import com.prodox.entity.MetricaCategoria;
import com.prodox.entity.Variable;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Revisión de seguridad — Variables: VariableController/VariableService no
 * validaban membresía al proyecto en ningún endpoint, y desactivar()/
 * actualizarFormula() ignoraban por completo el proyectoId recibido, sin
 * comprobar que la variable perteneciera realmente a ese proyecto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VariableService — pruebas unitarias (autorización)")
class VariableServiceTest {

    @Mock private VariableRepository variableRepo;
    @Mock private MetricaRepository metricaRepo;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private VariableService service;

    private UUID proyectoIdA;
    private UUID proyectoIdB;
    private UUID variableId;
    private UUID metricaId;

    @BeforeEach
    void setUp() {
        service = new VariableService(variableRepo, metricaRepo, projectMemberRepo);
        proyectoIdA = UUID.randomUUID();
        proyectoIdB = UUID.randomUUID();
        variableId = UUID.randomUUID();
        metricaId = UUID.randomUUID();
    }

    private Variable variableDelProyecto(UUID proyectoId) {
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Significado");

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        metrica.setNombre("Métrica test");
        metrica.setCategoria(categoria);

        Variable v = new Variable();
        v.setId(variableId);
        v.setProyectoId(proyectoId);
        v.setMetrica(metrica);
        v.setNombre("variable_test");
        v.setActiva(true);
        return v;
    }

    // ── listar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: usuario externo lanza SecurityException")
    void listar_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-externo")).thenReturn(false);

        assertThatThrownBy(() -> service.listar("user-externo", proyectoIdA))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verifyNoInteractions(variableRepo);
    }

    @Test
    @DisplayName("listar: miembro del proyecto permitido")
    void listar_miembroDelProyecto_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-a")).thenReturn(true);
        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoIdA)).thenReturn(java.util.List.of());

        assertThat(service.listar("user-a", proyectoIdA)).isEmpty();
    }

    // ── crear ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: usuario externo lanza SecurityException")
    void crear_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-externo")).thenReturn(false);
        CrearVariableRequest req = new CrearVariableRequest(metricaId, "nombre", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.crear("user-externo", proyectoIdA, req))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(metricaRepo);
        verify(variableRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: miembro del proyecto permitido")
    void crear_miembroDelProyecto_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-a")).thenReturn(true);
        when(variableRepo.existsByProyectoIdAndMetrica_Id(proyectoIdA, metricaId)).thenReturn(false);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Significado");
        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        metrica.setNombre("Métrica test");
        metrica.setCategoria(categoria);
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        CrearVariableRequest req = new CrearVariableRequest(metricaId, "nombre", null, null, null, null, null, null, null, null);
        VariableDto dto = service.crear("user-a", proyectoIdA, req);

        assertThat(dto.proyectoId()).isEqualTo(proyectoIdA);
        assertThat(dto.nombre()).isEqualTo("nombre");
    }

    // ── desactivar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("desactivar: usuario externo lanza SecurityException")
    void desactivar_usuarioExterno_lanzaSecurityException() {
        Variable v = variableDelProyecto(proyectoIdA);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(v));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-externo")).thenReturn(false);

        assertThatThrownBy(() -> service.desactivar("user-externo", proyectoIdA, variableId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(variableRepo, never()).save(any());
    }

    @Test
    @DisplayName("desactivar: miembro del proyecto permitido")
    void desactivar_miembroDelProyecto_permitido() {
        Variable v = variableDelProyecto(proyectoIdA);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(v));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-a")).thenReturn(true);
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.desactivar("user-a", proyectoIdA, variableId);

        assertThat(v.getActiva()).isFalse();
        verify(variableRepo, times(1)).save(v);
    }

    @Test
    @DisplayName("desactivar: variable de otro proyecto enviada con un proyectoId distinto se rechaza (400), sin consultar membresía")
    void desactivar_variableDeOtroProyecto_seRechaza() {
        Variable vDeB = variableDelProyecto(proyectoIdB); // variable real del Proyecto B
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(vDeB));

        // El atacante envía proyectoId=A en la URL, pero la variable es de B.
        assertThatThrownBy(() -> service.desactivar("user-a", proyectoIdA, variableId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece a este proyecto");

        verify(variableRepo, never()).save(any());
        // El chequeo de consistencia ocurre ANTES que el de membresía.
        verifyNoInteractions(projectMemberRepo);
    }

    @Test
    @DisplayName("desactivar: variable inexistente lanza IllegalArgumentException")
    void desactivar_variableInexistente_lanzaExcepcion() {
        when(variableRepo.findById(variableId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar("user-a", proyectoIdA, variableId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no encontrada");

        verifyNoInteractions(projectMemberRepo);
    }

    // ── actualizarFormula ─────────────────────────────────────────────────

    @Test
    @DisplayName("actualizarFormula: usuario externo lanza SecurityException")
    void actualizarFormula_usuarioExterno_lanzaSecurityException() {
        Variable v = variableDelProyecto(proyectoIdA);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(v));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-externo")).thenReturn(false);

        ActualizarFormulaRequest req = new ActualizarFormulaRequest("texto", null, null);

        assertThatThrownBy(() -> service.actualizarFormula("user-externo", proyectoIdA, variableId, req))
                .isInstanceOf(SecurityException.class);

        verify(variableRepo, never()).save(any());
    }

    @Test
    @DisplayName("actualizarFormula: miembro del proyecto permitido")
    void actualizarFormula_miembroDelProyecto_permitido() {
        Variable v = variableDelProyecto(proyectoIdA);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(v));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "user-a")).thenReturn(true);
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarFormulaRequest req = new ActualizarFormulaRequest("ISE = Crítico×5", null, "semanal");
        VariableDto dto = service.actualizarFormula("user-a", proyectoIdA, variableId, req);

        assertThat(dto.formulaTexto()).isEqualTo("ISE = Crítico×5");
        assertThat(v.getFrecuenciaCaptura()).isEqualTo("semanal");
    }

    @Test
    @DisplayName("actualizarFormula: variable de otro proyecto (Proyecto A intentando variable de B) se rechaza")
    void actualizarFormula_variableDeOtroProyecto_seRechaza() {
        Variable vDeB = variableDelProyecto(proyectoIdB);
        when(variableRepo.findById(variableId)).thenReturn(Optional.of(vDeB));

        ActualizarFormulaRequest req = new ActualizarFormulaRequest("texto", null, null);

        assertThatThrownBy(() -> service.actualizarFormula("user-a", proyectoIdA, variableId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece a este proyecto");

        verify(variableRepo, never()).save(any());
        verifyNoInteractions(projectMemberRepo);
    }

    // ── Scrum Master de un proyecto no obtiene privilegios en otro ─────────
    // (no aplica una restricción de rol distinta — ambas operaciones son de
    // solo-membresía — pero confirma que la membresía se evalúa siempre
    // contra el proyectoId correcto, nunca de forma global.)

    @Test
    @DisplayName("Miembro del Proyecto A no tiene acceso al Proyecto B aunque sea Scrum Master de A")
    void miembroDeA_noTieneAccesoAB() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdA, "sm-a")).thenReturn(true);
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoIdB, "sm-a")).thenReturn(false);

        when(variableRepo.findByProyectoIdAndActivaTrue(proyectoIdA)).thenReturn(java.util.List.of());
        assertThat(service.listar("sm-a", proyectoIdA)).isEmpty();

        assertThatThrownBy(() -> service.listar("sm-a", proyectoIdB))
                .isInstanceOf(SecurityException.class);
    }
}
