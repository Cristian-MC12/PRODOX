// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.GuardarValoresRequest;
import com.prodox.dto.VariablesMetricaResponse;
import com.prodox.entity.*;
import com.prodox.repository.*;
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
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "test@example.com"))
            .thenReturn(Optional.empty());

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
        // FASE 13: el indicadorVariable del fixture por defecto de setUp() ("Story Points
        // Completados") es prosa, no un identificador técnico — con la nueva validación de
        // formato sería rechazado. Este test no verifica el formato del nombre (eso lo cubren
        // los tests dedicados de FASE 13 más abajo); solo verifica que la materialización
        // on-demand ocurre, así que se le da un indicador snake_case válido.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir velocidad del equipo",
                "procedimiento": "Sumar story points completados",
                "indicadorVariable": "story_points_completados",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());  // No existen

        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        
        Variable variableGuardada = crearVariable();
        when(variableRepo.save(any(Variable.class))).thenReturn(variableGuardada);
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());
        
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
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

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
            // FASE 13: el mensaje ahora es el de ParametrizacionService.validarNombreVariableIndividual()
            // (reutilizado, no duplicado) — ya no el texto propio "El campo \"Indicador y Variables\"...".
            .hasMessageContaining("excede el máximo de 120 caracteres")
            .hasMessageContaining("121");

        verify(variableRepo, never()).save(any(Variable.class));
    }

    /**
     * FASE 13 (auditoría de Fase 12): crearVariablesDesdeParametrizacion() debe rechazar
     * ahora también nombres que, sin exceder 120 caracteres, no cumplen el formato técnico
     * snake_case — reutilizando exactamente ParametrizacionService.validarNombreVariableIndividual(),
     * la misma regla ya probada en ParametrizacionService. split(",", -1) permanece intacto:
     * estos tests cubren tanto el caso legítimo de lista corta separada por coma como los
     * casos reales de frase humana / fragmento de indicadorVariable con coma encontrados en
     * la auditoría de Fase 12 (variables 62d1ef80-... y ce8c16be-..., métrica "Pulso de
     * Ánimo del Equipo").
     */
    @Test
    void debeCrearVariableConNombreCortoValidoSinComas() {
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "acat",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        VariablesMetricaResponse response =
            service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        assertThat(response.variables()).hasSize(1);
        assertThat(response.variables().get(0).nombre()).isEqualTo("acat");
        verify(variableRepo, times(1)).save(any(Variable.class));
    }

    @Test
    void debeCrearVariablesParaListaValidaSeparadaPorComas() {
        // Demuestra que split(",", -1) NO fue eliminado: un indicadorVariable de varios
        // nombres técnicos cortos válidos ("acat, acr") sigue creando una Variable por cada uno.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "acat, acr",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        VariablesMetricaResponse response =
            service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        assertThat(response.variables()).hasSize(2);
        assertThat(response.variables()).extracting("nombre").containsExactly("acat", "acr");
        verify(variableRepo, times(2)).save(any(Variable.class));
    }

    // Corrección: una frase humana en indicadorVariable ya NO se rechaza (eso dejaba
    // al Scrum Master sin ninguna forma de aprobar la parametrización desde el flujo de
    // Verificación, que nunca informa un nombreVariable técnico explícito — ver error real
    // "nombreVariable '...' no tiene formato técnico válido" en /verificacion). Ahora se
    // normaliza a snake_case reutilizando ParametrizacionService.extraerNombresVariables(),
    // la misma extracción ya usada y probada en el flujo académico — nunca se persiste sin
    // validar: el resultado normalizado igual pasa por validarNombreVariableIndividual().

    @Test
    void debeNormalizarFraseHumanaComoNombreDeVariable() {
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "Problemas reportados en el sprint",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("problemas_reportados_en_el_sprint");
    }

    @Test
    void debeNormalizarNombreConMayusculasYEspacios() {
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "Calidad del Trabajo",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("calidad_del_trabajo");
    }

    @Test
    void debeNormalizarPrimeraMitadDeUnIndicadorHistoricoFragmentado() {
        // Caso representativo del incidente real de Fase 12 (métrica "Pulso de Ánimo del
        // Equipo", variables 62d1ef80-... / ce8c16be-...): esta mitad, usada sola como
        // indicadorVariable completo (sin coma), ahora se normaliza a snake_case en vez
        // de bloquear la aprobación sin ninguna alternativa para el Scrum Master.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "Califica ánimo de cada miembro del equipo (ej: escala numérica de 1 a 5",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        String nombre = captor.getValue().getNombre();
        assertThat(nombre).matches("^[a-z][a-z0-9_]{0,119}$");
        assertThat(nombre).isEqualTo("califica_nimo_de_cada_miembro_del_equipo_ej_escala_numrica_de_1_a_5");
    }

    @Test
    void debeNormalizarSegundaMitadDeUnIndicadorHistoricoFragmentado() {
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "donde 1 es muy bajo y 5 es muy alto).",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("donde_1_es_muy_bajo_y_5_es_muy_alto");
    }

    @Test
    void debeNormalizarElCasoRealReportadoEnVerificacion() {
        // Texto exacto reportado por el usuario en /verificacion: "nombreVariable
        // 'Número de defectos únicos registrados durante el sprint' no tiene formato
        // técnico válido." Ya no debe lanzar excepción.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "Número de defectos únicos registrados durante el sprint",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).matches("^[a-z][a-z0-9_]{0,119}$");
    }

    @Test
    void debePriorizarNombreVariableExplicitoDelSnapshotSobreElFallback() {
        // Si el snapshot de aprobación académica sí guardó un nombreVariable técnico
        // explícito, debe usarse tal cual (igual que ParametrizacionService al aprobar)
        // en vez de re-derivar uno distinto desde indicadorVariable.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Procedimiento de prueba",
                "indicadorVariable": "Defectos encontrados durante el sprint",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "por_sprint",
                "nombreVariable": "defectos_sprint"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("defectos_sprint");
    }

    @Test
    void noDebeAlterarOtrosCamposDeVariableAlValidarNombre() {
        // No-regresión: la nueva validación solo debe decidir si se persiste o no la
        // Variable — el resto de los campos que crearVariablesDesdeParametrizacion() ya
        // asignaba (descripcion, tipoAlcance, tipoDato, frecuenciaCaptura, parametrizacionId/
        // Version) deben seguir asignándose exactamente igual que antes de este cambio.
        parametrizacion.setConfiguracionAprobadaJson("""
            {
                "objetivo": "Medir algo",
                "procedimiento": "Sumar story points completados",
                "indicadorVariable": "acat",
                "escala": "Numérica 0-100 puntos",
                "frecuenciaCaptura": "semanal"
            }
            """);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(any(), any(), any()))
            .thenReturn(Optional.empty());

        service.obtenerVariables(metricaId, proyectoId, sprintId, "test@example.com");

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepo, times(1)).save(captor.capture());
        Variable guardada = captor.getValue();

        assertThat(guardada.getNombre()).isEqualTo("acat");
        assertThat(guardada.getDescripcion()).isEqualTo("Sumar story points completados");
        assertThat(guardada.getTipoAlcance()).isEqualTo("grupal");
        assertThat(guardada.getTipoDato()).isEqualTo("numerico");
        assertThat(guardada.getFrecuenciaCaptura()).isEqualTo("semanal");
        assertThat(guardada.getActiva()).isTrue();
        assertThat(guardada.getProyectoId()).isEqualTo(proyectoId);
        assertThat(guardada.getMetrica()).isEqualTo(metrica);
        assertThat(guardada.getParametrizacionId()).isEqualTo(parametrizacionId);
        assertThat(guardada.getParametrizacionVersion()).isEqualTo(1);
    }

    // ════════════════════════════════════════════════════════════════════
    // 16. Corrección del manejo de escalas — camino B (VariableDinamicaService,
    // usado por MetricRankingService.verificar(), sin snapshot JSON): copia la
    // escala estructurada directamente desde las columnas de MetricParametrizacion,
    // nunca por regex sobre el texto libre `escala`.
    // ════════════════════════════════════════════════════════════════════

    @Test
    void materializarVariables_sinSnapshot_copiaEscalaEstructuradaDesdeColumnas() {
        parametrizacion.setConfiguracionAprobadaJson(null); // flujo de Verificación: sin snapshot
        parametrizacion.setIndicadorVariable("defectos_encontrados");
        parametrizacion.setProcedimiento("Contar defectos");
        parametrizacion.setEscalaTipo("NUMERICA_ENTERA");
        parametrizacion.setEscalaMin(BigDecimal.ZERO);
        parametrizacion.setEscalaMax(null);
        parametrizacion.setEscalaPaso(BigDecimal.ONE);
        parametrizacion.setEscalaSinLimite(true);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Variable> resultado = service.materializarVariables(parametrizacion);

        assertThat(resultado).hasSize(1);
        Variable v = resultado.get(0);
        assertThat(v.getEscalaTipo()).isEqualTo("NUMERICA_ENTERA");
        assertThat(v.getEscalaMin()).isEqualByComparingTo("0");
        assertThat(v.getEscalaMax()).isNull();
        assertThat(v.getEscalaPaso()).isEqualByComparingTo("1");
        assertThat(v.getEscalaSinLimite()).isTrue();
    }

    // 17. Ambos caminos (A: ParametrizacionService, B: VariableDinamicaService) deben
    // copiar la MISMA estructura para la misma parametrización — verificado
    // comparando el resultado de este test con
    // ParametrizacionServiceEscalaTest.aprobarParametrizacion_copiaEscalaAVariable():
    // ambos parten de escalaTipo=NUMERICA_ENTERA/min=0/max=10/paso=1/sinLimite=false
    // y ambos producen esos mismos cinco valores en la Variable creada.
    @Test
    void materializarVariables_conEscala0a10_produceLaMismaEstructuraQueElCaminoAcademico() {
        parametrizacion.setConfiguracionAprobadaJson(null);
        parametrizacion.setIndicadorVariable("calidad_trabajo");
        parametrizacion.setProcedimiento("Evaluar calidad");
        parametrizacion.setEscalaTipo("NUMERICA_ENTERA");
        parametrizacion.setEscalaMin(BigDecimal.ZERO);
        parametrizacion.setEscalaMax(BigDecimal.TEN);
        parametrizacion.setEscalaPaso(BigDecimal.ONE);
        parametrizacion.setEscalaSinLimite(false);

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        Variable v = service.materializarVariables(parametrizacion).get(0);

        assertThat(v.getEscalaTipo()).isEqualTo("NUMERICA_ENTERA");
        assertThat(v.getEscalaMin()).isEqualByComparingTo("0");
        assertThat(v.getEscalaMax()).isEqualByComparingTo("10");
        assertThat(v.getEscalaPaso()).isEqualByComparingTo("1");
        assertThat(v.getEscalaSinLimite()).isFalse();
    }

    // ════════════════════════════════════════════════════════════════════
    // Revisión de captura por parametrización — camino B (VariableDinamicaService,
    // usado por MetricRankingService.verificar() sin snapshot JSON): antes,
    // crearVariablesDesdeParametrizacion() fijaba tipoAlcance="grupal" para
    // TODAS las variables, sin importar el alcance/responsable elegido en la
    // parametrización. Ahora lee MetricParametrizacion.responsableCaptura
    // (columna plana, no requiere snapshot) y lo traduce correctamente.
    // ════════════════════════════════════════════════════════════════════

    @Test
    void materializarVariables_responsableCapturaEquipo_creaVariableConTipoAlcanceIndividual() {
        parametrizacion.setConfiguracionAprobadaJson(null); // flujo de Verificación: sin snapshot
        parametrizacion.setIndicadorVariable("estado_animo");
        parametrizacion.setProcedimiento("Cada integrante registra su estado de ánimo");
        parametrizacion.setResponsableCaptura("EQUIPO");

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        Variable v = service.materializarVariables(parametrizacion).get(0);

        assertThat(v.getTipoAlcance()).isEqualTo("individual");
    }

    @Test
    void materializarVariables_responsableCapturaScrumMaster_creaVariableConTipoAlcanceGrupal() {
        parametrizacion.setConfiguracionAprobadaJson(null);
        parametrizacion.setIndicadorVariable("defectos_registrados");
        parametrizacion.setProcedimiento("El Scrum Master registra los defectos");
        parametrizacion.setResponsableCaptura("SCRUM_MASTER");

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(variableRepo.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        Variable v = service.materializarVariables(parametrizacion).get(0);

        assertThat(v.getTipoAlcance()).isEqualTo("grupal");
    }

    // ════════════════════════════════════════════════════════════════════
    // Revisión de seguridad — autorización delegada en EjecucionService
    // (obtenerVariables: solo membresía; guardarValores: solo Scrum Master).
    // ════════════════════════════════════════════════════════════════════

    @Test
    void obtenerVariables_delegaLaValidacionDeAccesoEnEjecucionService() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        doThrow(new SecurityException("No tienes acceso a este proyecto"))
            .when(ejecucionService).validarAcceso("user-externo", proyectoId);

        assertThatThrownBy(() ->
            service.obtenerVariables(metricaId, proyectoId, sprintId, "user-externo"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("No tienes acceso a este proyecto");

        verify(ejecucionService).validarAcceso("user-externo", proyectoId);
        verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    void obtenerVariables_usuarioAutorizado_siguePermitido() {
        // Reconfirma que agregar la validación de acceso NO rompe el camino feliz
        // ya cubierto por debeObtenerVariablesDeParametrizacionAprobada(): con
        // EjecucionService mockeado (validarAcceso no lanza por defecto), el
        // resto del flujo sigue funcionando exactamente igual.
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        Variable variable = crearVariable();
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of(variable));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "user-miembro"))
            .thenReturn(Optional.empty());

        VariablesMetricaResponse response =
            service.obtenerVariables(metricaId, proyectoId, sprintId, "user-miembro");

        assertThat(response.variables()).hasSize(1);
        verify(ejecucionService).validarAcceso("user-miembro", proyectoId);
    }

    // ════════════════════════════════════════════════════════════════════
    // Corrección de captura por usuario (bug reportado: el usuario B veía el
    // valor "22" ya registrado por A, sin haber registrado nada él mismo).
    // construirVariableConValor() usaba findBySprintIdAndVariable_Id(sprintId,
    // variableId) — sin userId y sin ORDER BY — devolviendo el registro de
    // CUALQUIER miembro. Ahora usa findFirstBySprintIdAndVariable_IdAndUserId
    // OrderByRegistradoAtDesc(sprintId, variableId, userId), el mismo método
    // que ya usa la escritura (EjecucionService.guardarOActualizarValor).
    // ════════════════════════════════════════════════════════════════════

    @Test
    void obtenerVariables_usuarioSinRegistroPropio_noVeElValorDeOtroMiembro() {
        // TEST 1 (adaptado a este nivel): A ya registró 22; B —que todavía no
        // registró nada— NO debe ver ese 22 al pedir sus variables.
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        Variable variable = crearVariable();
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of(variable));
        // A SÍ tiene un registro vigente (22) — pero esta consulta es por B.
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "userB"))
            .thenReturn(Optional.empty());

        VariablesMetricaResponse response = service.obtenerVariables(metricaId, proyectoId, sprintId, "userB");

        assertThat(response.variables().get(0).valorNum()).isNull();
        // Nunca se consulta "cualquier registro de la variable+sprint" para decidir
        // qué mostrarle a B — solo el suyo propio.
        verify(registroRepo, never()).findBySprintIdAndVariable_Id(any(), any());
    }

    @Test
    void obtenerVariables_usuarioConRegistroPropio_veSuPropioValor() {
        // TEST 2/3 (adaptado): B ya registró 15 — debe ver 15, no el valor de A ni de nadie más.
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        Variable variable = crearVariable();
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of(variable));

        RegistroValor registroDeB = new RegistroValor();
        registroDeB.setVariable(variable);
        registroDeB.setSprintId(sprintId);
        registroDeB.setUserId("userB");
        registroDeB.setValorNum(new BigDecimal("15"));
        registroDeB.setRegistradoAt(Instant.parse("2026-08-30T00:00:00Z"));

        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "userB"))
            .thenReturn(Optional.of(registroDeB));

        VariablesMetricaResponse response = service.obtenerVariables(metricaId, proyectoId, sprintId, "userB");

        assertThat(response.variables().get(0).valorNum()).isEqualByComparingTo("15");
    }

    @Test
    void obtenerVariables_dosUsuariosDistintos_cadaUnoVeSoloSuPropioRegistro() {
        // Aislamiento explícito: la misma llamada a obtenerVariables(), para el
        // MISMO variableId+sprintId, debe devolver un valor distinto según quién
        // pregunta — nunca el mismo "último registro global" para ambos.
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        Variable variable = crearVariable();
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of(variable));

        RegistroValor registroDeA = new RegistroValor();
        registroDeA.setVariable(variable);
        registroDeA.setSprintId(sprintId);
        registroDeA.setUserId("userA");
        registroDeA.setValorNum(new BigDecimal("22"));
        registroDeA.setRegistradoAt(Instant.parse("2026-08-29T00:00:00Z"));

        RegistroValor registroDeSM = new RegistroValor();
        registroDeSM.setVariable(variable);
        registroDeSM.setSprintId(sprintId);
        registroDeSM.setUserId("userSM");
        registroDeSM.setValorNum(new BigDecimal("8"));
        registroDeSM.setRegistradoAt(Instant.parse("2026-08-31T00:00:00Z")); // el más reciente de TODOS

        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "userA"))
            .thenReturn(Optional.of(registroDeA));
        when(registroRepo.findFirstBySprintIdAndVariable_IdAndUserIdOrderByRegistradoAtDesc(
                sprintId, variable.getId(), "userSM"))
            .thenReturn(Optional.of(registroDeSM));

        VariablesMetricaResponse respuestaParaA = service.obtenerVariables(metricaId, proyectoId, sprintId, "userA");
        VariablesMetricaResponse respuestaParaSM = service.obtenerVariables(metricaId, proyectoId, sprintId, "userSM");

        // A ve 22 (el suyo) aunque el registro globalmente más reciente sea el de SM (8).
        assertThat(respuestaParaA.variables().get(0).valorNum()).isEqualByComparingTo("22");
        assertThat(respuestaParaSM.variables().get(0).valorNum()).isEqualByComparingTo("8");
    }

    @Test
    void guardarValores_delegaLaValidacionDePermisoEnEjecucionServicePorVariable() {
        // Revisión de captura individual: la validación de quién puede
        // registrar ya no es un único chequeo por request (validarScrumMaster) —
        // se delega a ejecucionService.validarPuedeRegistrar(userId, variable)
        // POR CADA variable, porque un mismo guardado puede mezclar variables
        // individuales (cualquier miembro) y grupales (solo Scrum Master).
        Variable variable = crearVariable(); // tipoAlcance por defecto = 'grupal'
        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(), new BigDecimal("42"), null, null, null, null, null);
        GuardarValoresRequest request = new GuardarValoresRequest(proyectoId, sprintId, List.of(valor));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));
        doThrow(new SecurityException("Solo el Scrum Master del proyecto puede registrar valores"))
            .when(ejecucionService).validarPuedeRegistrar("user-miembro", variable);

        assertThatThrownBy(() -> service.guardarValores(metricaId, request, "user-miembro"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Solo el Scrum Master");

        verify(ejecucionService).validarPuedeRegistrar("user-miembro", variable);
        verify(ejecucionService, never()).guardarOActualizarValor(
            any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void guardarValores_variableIndividual_scrumMemberPuedeRegistrarSuPropioValor() {
        Variable variable = crearVariable();
        variable.setTipoAlcance("individual");
        GuardarValoresRequest.ValorVariable valor = new GuardarValoresRequest.ValorVariable(
            variable.getId(), new BigDecimal("80"), null, null, null, null, null);
        GuardarValoresRequest request = new GuardarValoresRequest(proyectoId, sprintId, List.of(valor));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findById(variable.getId())).thenReturn(Optional.of(variable));
        // validarPuedeRegistrar no lanza nada para 'individual' — cualquier miembro puede.

        service.guardarValores(metricaId, request, "user-miembro");

        verify(ejecucionService).validarPuedeRegistrar("user-miembro", variable);
        verify(ejecucionService).guardarOActualizarValor(
            eq(variable), eq(sprintId), eq("user-miembro"), any(), any(), any(), any(), any(), any());
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
