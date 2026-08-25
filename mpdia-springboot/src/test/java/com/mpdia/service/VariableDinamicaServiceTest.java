// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.GuardarValoresRequest;
import com.mpdia.dto.VariablesMetricaResponse;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests para VariableDinamicaService - Fase 16.7
 */
@ExtendWith(MockitoExtension.class)
class VariableDinamicaServiceTest {

    @Mock private MetricParametrizacionRepository parametrizacionRepo;
    @Mock private VariableRepository variableRepo;
    @Mock private RegistroValorRepository registroRepo;
    @Mock private MetricaRepository metricaRepo;
    @Mock private SprintRepository sprintRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private EjecucionService ejecucionService;

    @InjectMocks
    private VariableDinamicaService service;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    private UUID proyectoId;
    private UUID sprintId;
    private UUID metricaId;
    private UUID parametrizacionId;
    private Proyecto proyecto;
    private Sprint sprint;
    private Metrica metrica;
    private MetricParametrizacion parametrizacion;

    @BeforeEach
    void setUp() throws Exception {
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        metricaId = UUID.randomUUID();
        parametrizacionId = UUID.randomUUID();
        
        // Configurar ObjectMapper en el servicio mediante reflexión
        var field = VariableDinamicaService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, objectMapper);
        
        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Test Proyecto");
        
        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        
        metrica = new Metrica();
        metrica.setId(metricaId);
        metrica.setNombre("Velocidad");
        
        String configuracionJson = """
            {
                "version": 1,
                "objetivo": "Medir velocidad del equipo",
                "procedimiento": "Sumar story points completados",
                "indicadorVariable": "Story Points Completados",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint",
                "aprobadoPor": "test@example.com",
                "aprobadoEn": "2026-08-13T00:00:00Z"
            }
            """;
        
        parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(parametrizacionId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setVersion(1);
        parametrizacion.setStatus("aprobada");
        parametrizacion.setConfiguracionAprobadaJson(configuracionJson);
    }

    @Test
    void debeObtenerVariablesDeParametrizacionAprobada() {
        // Given
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        
        Variable variable = crearVariable();
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of(variable));
        when(registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId()))
            .thenReturn(List.of());
        
        // When
        VariablesMetricaResponse response = service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");
        
        // Then
        assertThat(response.parametrizacionId()).isEqualTo(parametrizacionId);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("aprobada");
        assertThat(response.variables()).hasSize(1);
        assertThat(response.variables().get(0).nombre()).isEqualTo("Test Variable");
    }

    @Test
    void debeCrearVariablesOnDemandSiNoExisten() {
        // Given
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());  // No existen
        
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        
        Variable variableGuardada = crearVariable();
        when(variableRepo.save(any(Variable.class))).thenReturn(variableGuardada);
        when(registroRepo.findBySprintIdAndVariable_Id(any(), any())).thenReturn(List.of());
        
        // When
        VariablesMetricaResponse response = service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");
        
        // Then
        verify(variableRepo, times(1)).save(any(Variable.class));
        assertThat(response.variables()).hasSize(1);
    }

    @Test
    void debeRechazarParametrizacionNoAprobada() {
        // Given
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.empty());  // No existe aprobada
        
        // When/Then
        assertThatThrownBy(() -> service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No existe parametrización aprobada");
    }

    @Test
    void debeRechazarSprintDeOtroProyecto() {
        // Given
        sprint.setProyectoId(UUID.randomUUID());  // Diferente proyecto
        
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        
        // When/Then
        assertThatThrownBy(() -> service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no pertenece al proyecto");
    }

    @Test
    void debeGuardarValorNumerico() {
        // Given
        Variable variable = crearVariable();
        variable.setTipoDato("numerico");
        
        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(),
            new BigDecimal("42"),
            null,
            null,
            "Test observation",
            null,
            null
        );

        GuardarValoresRequest request = new GuardarValoresRequest(
            proyectoId,
            sprintId,
            List.of(valor)
        );

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));

        // When
        service.guardarValores(metricaId, request, "test@example.com");

        // Then: sin fechaCaptura en el request, se propaga null (FASE 16: la
        // sobrecarga con fecha delega en el comportamiento existente cuando es null).
        verify(ejecucionService, times(1)).guardarOActualizarValor(
            eq(variable), eq(sprintId), eq("test@example.com"),
            eq(new BigDecimal("42")), isNull(), isNull(), eq("Test observation"), isNull(), isNull());
    }

    @Test
    void debeGuardarValorConFechaCapturaExplicita() {
        // Given
        Variable variable = crearVariable();
        variable.setTipoDato("numerico");

        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(),
            new BigDecimal("7"),
            null,
            null,
            null,
            "2026-08-21T00:00:00Z",
            null
        );

        GuardarValoresRequest request = new GuardarValoresRequest(proyectoId, sprintId, List.of(valor));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));

        // When
        service.guardarValores(metricaId, request, "test@example.com");

        // Then: la fecha ISO del request se parsea y se propaga como Instant explícito.
        verify(ejecucionService, times(1)).guardarOActualizarValor(
            eq(variable), eq(sprintId), eq("test@example.com"),
            eq(new BigDecimal("7")), isNull(), isNull(), isNull(),
            eq(Instant.parse("2026-08-21T00:00:00Z")), isNull());
    }

    @Test
    void debeRechazarValorIncorrectoParaTipo() {
        // Given
        Variable variable = crearVariable();
        variable.setTipoDato("numerico");
        
        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(),
            null,  // Sin valor numérico
            null,
            null,
            null,
            null,
            null
        );
        
        GuardarValoresRequest request = new GuardarValoresRequest(
            proyectoId,
            sprintId,
            List.of(valor)
        );
        
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));
        
        // When/Then
        assertThatThrownBy(() -> service.guardarValores(metricaId, request, "test@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requiere valor numérico");
    }

    @Test
    void debeRechazarVariableDeOtraParametrizacion() {
        // Given
        Variable variable = crearVariable();
        variable.setParametrizacionId(UUID.randomUUID());  // Diferente parametrización
        
        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(),
            new BigDecimal("42"),
            null,
            null,
            null,
            null,
            null
        );

        GuardarValoresRequest request = new GuardarValoresRequest(
            proyectoId,
            sprintId,
            List.of(valor)
        );

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));

        // When/Then
        assertThatThrownBy(() -> service.guardarValores(metricaId, request, "test@example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no pertenece a la parametrización aprobada");
    }

    /**
     * FASE 17 (corrección del defecto documentado): crearVariablesDesdeParametrizacion()
     * debe validar la longitud del indicadorVariable ANTES de persistir, igual que
     * ParametrizacionService ya hace para su propio flujo. Estos tests cubren el
     * límite exacto (120 = válido, 121 = rechazado) y confirman que ningún
     * variableRepo.save(...) se ejecuta cuando el indicador es inválido.
     */
    @Test
    void debeCrearVariableConIndicadorDeExactamente120Caracteres() {
        String indicador120 = "a".repeat(120);
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "%s",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """.formatted(indicador120));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findBySprintIdAndVariable_Id(any(), any())).thenReturn(List.of());

        VariablesMetricaResponse response =
            service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        assertThat(response.variables()).hasSize(1);
        assertThat(response.variables().get(0).nombre()).hasSize(120);
        verify(variableRepo, times(1)).save(any(Variable.class));
    }

    @Test
    void debeRechazarIndicadorDe121CaracteresConMensajeClaro() {
        String indicador121 = "a".repeat(121);
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "%s",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """.formatted(indicador121));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));

        assertThatThrownBy(() ->
            service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com"))
            .isInstanceOf(NombreVariableInvalidoException.class)
            .hasMessageContaining("Indicador y Variables")
            .hasMessageContaining("120")
            .hasMessageContaining("121");

        verify(variableRepo, never()).save(any(Variable.class));
    }

    private Variable crearVariable() {
        Variable v = new Variable();
        v.setId(UUID.randomUUID());
        v.setProyectoId(proyectoId);
        v.setMetrica(metrica);
        v.setNombre("Test Variable");
        v.setDescripcion("Test Description");
        v.setTipoDato("numerico");
        v.setActiva(true);
        v.setParametrizacionId(parametrizacionId);
        v.setParametrizacionVersion(1);
        v.setCreatedAt(Instant.now());
        return v;
    }
}
