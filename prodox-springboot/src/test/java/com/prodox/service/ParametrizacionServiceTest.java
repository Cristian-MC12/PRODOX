package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.AprobarParametrizacionRequest;
import com.prodox.dto.GuardarPropuestaRequest;
import com.prodox.dto.ParametrizacionRequest;
import com.prodox.dto.PropuestaParametrizacionDto;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.Metrica;
import com.prodox.entity.MetricaCategoria;
import com.prodox.entity.Variable;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.VariableRepository;
import com.prodox.service.TipoOperacionInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests para ParametrizacionService
 * 
 * FASE 16.5: Generación de propuestas con IA
 * FASE 16.6: Aprobación formal y versionado
 */
@ExtendWith(MockitoExtension.class)
class ParametrizacionServiceTest {

    @Mock
    private GeminiService geminiService;
    
    @Mock
    private MetricParametrizacionRepository parametrizacionRepository;
    
    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private VariableRepository variableRepository;

    @Mock
    private MetricaRepository metricaRepository;

    @InjectMocks
    private ParametrizacionService parametrizacionService;

    private ObjectMapper objectMapper;
    private ParametrizacionRequest request;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Registrar módulo para manejar java.time.Instant
        objectMapper.findAndRegisterModules();
        
        parametrizacionService = new ParametrizacionService(
            geminiService,
            objectMapper,
            parametrizacionRepository,
            projectMemberRepository,
            variableRepository,
            metricaRepository
        );
        
        request = new ParametrizacionRequest(
            "Productividad",
            "Interno",
            "Velocidad",
            "Puntos de historia completados por sprint"
        );
    }
    
    private void mockAuthentication() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "test@example.com", null, List.of()
        );
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void generarPropuestas_debeGenerarExactamenteUnaPropuesta() {
        // Given: Gemini responde con JSON válido de 1 propuesta
        String geminiResponse = """
            [
              {
                "titulo": "Medición de puntos completados",
                "objetivo": "Medir la capacidad de entrega del equipo",
                "procedimiento": "Sumar story points de historias completadas (Done)",
                "indicadorVariable": "Story Points Completados (suma de puntos Done)",
                "escala": "Escala numérica de 0 a 100 puntos",
                "justificacion": "PROPUESTA basada en prácticas ágiles. Requiere validación del equipo."
              }
            ]
            """;
        
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        // When
        List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);

        // Then: Debe haber EXACTAMENTE 1 propuesta
        assertThat(propuestas).isNotNull();
        assertThat(propuestas).hasSize(1);
        
        PropuestaParametrizacionDto propuesta = propuestas.get(0);
        assertThat(propuesta.titulo()).isEqualTo("Medición de puntos completados");
        assertThat(propuesta.objetivo()).isNotNull();
        assertThat(propuesta.procedimiento()).isNotNull();
        assertThat(propuesta.indicadorVariable()).isNotNull();
        assertThat(propuesta.escala()).isNotNull();
        assertThat(propuesta.justificacion()).contains("PROPUESTA");
        assertThat(propuesta.justificacion()).contains("validación");
    }

    @Test
    void generarPropuestas_cuandoGeminiFalla_debeRetornarPropuestaGenerica() {
        // Given: Gemini lanza excepción
        when(geminiService.generate(anyString())).thenThrow(new RuntimeException("API error"));

        // When
        List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);

        // Then: Debe haber EXACTAMENTE 1 propuesta de fallback
        assertThat(propuestas).isNotNull();
        assertThat(propuestas).hasSize(1);
        
        PropuestaParametrizacionDto propuesta = propuestas.get(0);
        assertThat(propuesta.titulo()).contains("Medición directa");
        assertThat(propuesta.objetivo()).isNotNull();
        assertThat(propuesta.justificacion()).contains("PROPUESTA");
    }

    @Test
    void generarPropuestas_cuandoGeminiDevuelveJSONInvalido_debeRetornarPropuestaGenerica() {
        // Given: Gemini devuelve texto no válido
        when(geminiService.generate(anyString())).thenReturn("texto inválido sin JSON");

        // When
        List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);

        // Then: Debe haber EXACTAMENTE 1 propuesta de fallback
        assertThat(propuestas).isNotNull();
        assertThat(propuestas).hasSize(1);
        assertThat(propuestas.get(0).titulo()).isNotNull();
    }

    @Test
    void generarPropuestas_propuestaDebeContenerTodosLosCamposObligatorios() {
        // Given
        String geminiResponse = """
            [
              {
                "titulo": "Test título",
                "objetivo": "Test objetivo",
                "procedimiento": "Test procedimiento",
                "indicadorVariable": "Test indicador",
                "escala": "Test escala",
                "justificacion": "Test justificación con PROPUESTA"
              }
            ]
            """;
        
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        // When
        List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);

        // Then
        assertThat(propuestas).hasSize(1);
        PropuestaParametrizacionDto p = propuestas.get(0);
        assertThat(p.titulo()).isNotEmpty();
        assertThat(p.objetivo()).isNotEmpty();
        assertThat(p.procedimiento()).isNotEmpty();
        assertThat(p.indicadorVariable()).isNotEmpty();
        assertThat(p.escala()).isNotEmpty();
        assertThat(p.justificacion()).isNotEmpty();
    }

    @Test
    void generarPropuestas_fallbackDebeContenerAdvertenciaDeValidacion() {
        // Given: Forzar fallback
        when(geminiService.generate(anyString())).thenThrow(new RuntimeException());

        // When
        List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);

        // Then: La propuesta de fallback debe advertir que requiere validación
        assertThat(propuestas).hasSize(1);
        PropuestaParametrizacionDto propuesta = propuestas.get(0);
        assertThat(propuesta.justificacion())
            .containsAnyOf("PROPUESTA", "validación", "ajuste", "requiere");
    }
    
    // ========================================
    // TESTS FASE 16.6: APROBACIÓN Y VERSIONADO
    // ========================================
    
    @Test
    void guardarPropuesta_debeCrearParametrizacionConEstadoPropuesta() {
        // Given
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId,
            proyectoId,
            "Objetivo test",
            "Procedimiento test",
            "Indicador test",
            "Escala test",
            "por_sprint",
            "Fuente académica test",
            "Σ x",
            "SUMA",
            "unidades",
            "{\"titulo\": \"Test\"}",
            null
        , null, null, null, null, null, null);
        
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of());
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        // Then
        assertThat(result.getStatus()).isEqualTo("propuesta"); // NO aprobada automáticamente
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getObjetivo()).isEqualTo("Objetivo test");
        assertThat(result.getPropuestaIAJson()).isEqualTo("{\"titulo\": \"Test\"}");
    }

    // ========================================
    // TESTS Corrección de auditoría (parte A): protección backend contra
    // doble clic/petición repetida en guardarPropuesta(). Caso real detectado
    // (proyecto "Creación de un avatar Xabi"): dos parametrizaciones casi
    // idénticas de la misma métrica, creadas con segundos de diferencia.
    // ========================================

    @Test
    @DisplayName("Corrección de auditoría (parte A): dos llamadas consecutivas idénticas a guardarPropuesta() -> una sola parametrización persistida")
    void guardarPropuesta_dosLlamadasConsecutivasIdenticas_creaUnaSolaParametrizacion() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Objetivo", "Procedimiento", "Indicador", "Escala",
            "por_sprint", "Fuente", "Σ x", "SUMA", "unidad",
            "{\"titulo\":\"Test\"}", "var_x",
            "NUMERICA_ENTERA", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN,
            java.math.BigDecimal.ONE, false, "descripcion"
        );

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId)).thenReturn(List.of());

        MetricParametrizacion primera = parametrizacionService.guardarPropuesta(req);
        assertThat(primera.getVersion()).isEqualTo(1);

        // La segunda petición (doble clic o reintento de red) llega cuando el
        // historial YA tiene la propuesta recién creada por la primera.
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(primera));

        MetricParametrizacion segunda = parametrizacionService.guardarPropuesta(req);

        assertThat(segunda).isSameAs(primera);
        assertThat(segunda.getVersion()).isEqualTo(1);
        // Solo UNA fila insertada en total — la protección no crea versión 2.
        verify(parametrizacionRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Corrección de auditoría (parte A): dos parametrizaciones con configuración realmente diferente -> ambas se permiten")
    void guardarPropuesta_configuracionesRealmenteDiferentes_ambasSePermiten() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        GuardarPropuestaRequest req1 = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Objetivo A", "Procedimiento", "Indicador", "Escala",
            "por_sprint", "Fuente", "Σ x", "SUMA", "unidad", null, null
        , null, null, null, null, null, null);

        // Difiere en el campo sustantivo objetivo (no es un doble clic: es una
        // propuesta realmente distinta) — debe crear una versión nueva.
        GuardarPropuestaRequest req2 = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Objetivo B, completamente distinto", "Procedimiento", "Indicador", "Escala",
            "por_sprint", "Fuente", "Σ x", "SUMA", "unidad", null, null
        , null, null, null, null, null, null);

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId)).thenReturn(List.of());

        MetricParametrizacion primera = parametrizacionService.guardarPropuesta(req1);

        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(primera));

        MetricParametrizacion segunda = parametrizacionService.guardarPropuesta(req2);

        assertThat(segunda).isNotSameAs(primera);
        assertThat(segunda.getVersion()).isEqualTo(2);
        assertThat(segunda.getObjetivo()).isEqualTo("Objetivo B, completamente distinto");
        verify(parametrizacionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Corrección de auditoría (parte A): misma configuración pero fuera de la ventana anti-duplicado -> se permite como parametrización nueva y legítima")
    void guardarPropuesta_mismaConfiguracionFueraDeLaVentana_sePermiteComoNuevaVersion() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Objetivo", "Procedimiento", "Indicador", "Escala",
            "por_sprint", "Fuente", "Σ x", "SUMA", "unidad", null, null
        , null, null, null, null, null, null);

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId)).thenReturn(List.of());

        MetricParametrizacion primera = parametrizacionService.guardarPropuesta(req);
        // Simula que la primera quedó creada hace mucho más que la ventana
        // anti-duplicado (ej. el usuario la recrea intencionalmente meses
        // después de un rechazo) — no debe tratarse como duplicado.
        primera.setCreatedAt(Instant.now().minusSeconds(3600));

        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(primera));

        MetricParametrizacion segunda = parametrizacionService.guardarPropuesta(req);

        assertThat(segunda).isNotSameAs(primera);
        assertThat(segunda.getVersion()).isEqualTo(2);
        verify(parametrizacionRepository, times(2)).save(any());
    }

    // ========================================
    // TESTS FASE 16.10-D: CATÁLOGO OFICIAL DE tipoOperacion
    // (SUMA | PROMEDIO | DIRECTO | FORMULA — comentario V24 / FASE16_8_7)
    // ========================================

    private GuardarPropuestaRequest requestConTipoOperacion(UUID metricaId, UUID proyectoId, String tipoOperacion) {
        return new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Σ x", tipoOperacion, "unidad", null, null
        , null, null, null, null, null, null);
    }

    private void mockGuardarPropuestaHappyPath(UUID metricaId, UUID proyectoId) {
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of());
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void guardarPropuesta_conTipoOperacionSUMA_esAceptado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "SUMA"));

        assertThat(result.getTipoOperacion()).isEqualTo("SUMA");
    }

    @Test
    void guardarPropuesta_conTipoOperacionPROMEDIO_esAceptado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "PROMEDIO"));

        assertThat(result.getTipoOperacion()).isEqualTo("PROMEDIO");
    }

    @Test
    void guardarPropuesta_conTipoOperacionDIRECTO_esAceptado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "DIRECTO"));

        assertThat(result.getTipoOperacion()).isEqualTo("DIRECTO");
    }

    @Test
    void guardarPropuesta_conTipoOperacionFORMULA_esAceptado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "FORMULA"));

        assertThat(result.getTipoOperacion()).isEqualTo("FORMULA");
    }

    @Test
    void guardarPropuesta_sinTipoOperacion_esAceptado() {
        // Campo académico opcional: null debe seguir siendo válido
        // (parametrizaciones no académicas no lo usan)
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, null));

        assertThat(result.getTipoOperacion()).isNull();
    }

    @Test
    void guardarPropuesta_conTipoOperacionCONTEO_esRechazado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(
                requestConTipoOperacion(metricaId, proyectoId, "CONTEO")))
            .isInstanceOf(TipoOperacionInvalidoException.class)
            .hasMessageContaining("CONTEO");
    }

    @Test
    void guardarPropuesta_conTipoOperacionPORCENTAJE_esRechazado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(
                requestConTipoOperacion(metricaId, proyectoId, "PORCENTAJE")))
            .isInstanceOf(TipoOperacionInvalidoException.class);
    }

    @Test
    void guardarPropuesta_conTipoOperacionRATIO_esRechazado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(
                requestConTipoOperacion(metricaId, proyectoId, "RATIO")))
            .isInstanceOf(TipoOperacionInvalidoException.class);
    }

    @Test
    void guardarPropuesta_conTipoOperacionOTRO_esRechazado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(
                requestConTipoOperacion(metricaId, proyectoId, "OTRO")))
            .isInstanceOf(TipoOperacionInvalidoException.class);
    }

    @Test
    void guardarPropuesta_conTipoOperacionDesconocido_esRechazado() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication();
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(
                requestConTipoOperacion(metricaId, proyectoId, "XYZ123")))
            .isInstanceOf(TipoOperacionInvalidoException.class)
            .hasMessageContaining("XYZ123");
    }

    @Test
    void aprobarParametrizacion_conTipoOperacionCONTEO_esRechazado() {
        // Confirma que la validación también está conectada en aprobarParametrizacion(),
        // no solo en guardarPropuesta(). Debe fallar ANTES de crear variables.
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "CONTEO", "unidad", null
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(TipoOperacionInvalidoException.class)
            .hasMessageContaining("CONTEO");

        // No debe haber intentado crear variables (falló antes de llegar ahí)
        verify(metricaRepository, never()).findById(any());
        verify(variableRepository, never()).save(any());
    }

    @Test
    void aprobarParametrizacion_conTipoOperacionNull_esRechazado() {
        // FASE 16.10-D: no permitir que una parametrización académica calculable
        // (indicadorVariable definido, va a generar variables ejecutables) quede
        // aprobada silenciosamente con tipoOperacion=NULL — el motor de cálculo
        // (MetricaAcademicaService.calcularSegunTipo) lanzaría NullPointerException
        // en el switch al intentar ejecutarla más adelante. Debe rechazarse aquí,
        // en el momento de aprobar, con un error claro.
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", null, "unidad", null
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(TipoOperacionInvalidoException.class)
            .hasMessageContaining("tipoOperacion");

        // No debe haber intentado crear variables ni persistir la aprobación
        verify(parametrizacionRepository, never()).save(any());
        verify(metricaRepository, never()).findById(any());
        verify(variableRepository, never()).save(any());
    }

    @Test
    void aprobarParametrizacion_conTipoOperacionVacio_esRechazado() {
        // Mismo caso que el anterior pero con string vacío en lugar de null —
        // es exactamente lo que produce hoy `this.form.tipoOperacion || null`
        // en el frontend cuando el campo nunca se llenó (queda como '' antes
        // de la coerción, o como null después de ella según el punto del flujo).
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "", "unidad", null
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(TipoOperacionInvalidoException.class);

        verify(parametrizacionRepository, never()).save(any());
    }

    @Test
    void aprobarParametrizacion_snapshotConservaExactamenteLosValoresAcademicosDelRequest() {
        // FASE 16.10-D (requisito F): configuracionAprobadaJson debe reflejar
        // EXACTAMENTE los valores académicos que llegaron en el request de
        // aprobación (que ahora, en el frontend corregido, provienen de la
        // propuesta persistida y no de this.form desincronizado).
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo real", "Procedimiento real",
            "El indicador principal es el número de impedimentos que bloquearon al equipo...",
            "Escala real", "por_sprint", "Guerrero-Calvache & Hernández (2024)", "Σ(I_sprint)",
            "SUMA", "impedimentos", "impedimentos_registrados"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, req);

        assertThat(result.getTipoOperacion()).isEqualTo("SUMA");
        assertThat(result.getFormulaAcademica()).isEqualTo("Σ(I_sprint)");
        assertThat(result.getUnidadResultado()).isEqualTo("impedimentos");

        assertThat(result.getConfiguracionAprobadaJson())
            .contains("\"tipoOperacion\":\"SUMA\"")
            .contains("\"formulaAcademica\":\"Σ(I_sprint)\"")
            .contains("\"unidadResultado\":\"impedimentos\"")
            .contains("\"nombreVariable\":\"impedimentos_registrados\"");
    }

    @Test
    void generarPropuestas_promptSoloContieneCatalogoOficial() {
        // El prompt enviado a Gemini debe restringirse a SUMA | PROMEDIO | DIRECTO | FORMULA
        when(geminiService.generate(anyString())).thenReturn("texto inválido sin JSON");

        parametrizacionService.generarPropuestas(request);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(geminiService).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        // La lista de valores OFRECIDOS a la IA (regla + schema JSON) debe ser
        // exactamente el catálogo oficial. CONTEO/PORCENTAJE/RATIO/OTRO pueden
        // aparecer únicamente dentro de la instrucción explícita de "NO uses esto".
        assertThat(prompt).contains("EXACTAMENTE uno de estos 4 valores, sin excepción: SUMA, PROMEDIO, DIRECTO, FORMULA");
        assertThat(prompt).contains("\"tipoOperacion\": \"SUMA | PROMEDIO | DIRECTO | FORMULA\"");
        assertThat(prompt).doesNotContain("PORCENTAJE | CONTEO | RATIO | OTRO");
        assertThat(prompt).doesNotContain("SUMA, PROMEDIO, PORCENTAJE, CONTEO, RATIO");
    }

    @Test
    void guardarPropuesta_usuarioNoMiembro_debeRechazar() {
        // Given
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", null, null
        , null, null, null, null, null, null);
        
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> parametrizacionService.guardarPropuesta(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no pertenece al proyecto");
    }
    
    @Test
    void aprobarParametrizacion_debeCrearSnapshot_y_cambiarEstadoAprobada() {
        // Given
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();
        
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);
        
        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo final",
            "Procedimiento final",
            "Indicador final",
            "Escala final",
            "por_sprint",
            "Fuente académica",
            "Σ x",
            "SUMA",
            "unidades",
            null
        , null, null, null, null, null, null);
        
        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        // When
        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, req);

        // Then
        assertThat(result.getStatus()).isEqualTo("aprobada");
        assertThat(result.getObjetivo()).isEqualTo("Objetivo final");
        assertThat(result.getRevisadoPor()).isEqualTo("test@example.com");
        assertThat(result.getRevisadoAt()).isNotNull();
        assertThat(result.getConfiguracionAprobadaJson()).isNotNull();
        assertThat(result.getConfiguracionAprobadaJson()).contains("\"version\":1");
        verify(variableRepository, times(1)).save(any());
    }

    // ════════════════════════════════════════════════════════════════════
    // Revisión de captura por parametrización: el alcance/responsable de
    // captura (EQUIPO/SCRUM_MASTER) que el Scrum Master elige explícitamente
    // en la parametrización debe materializarse en Variable.tipoAlcance —
    // antes, crearVariablesDesdeParametrizacion() lo fijaba siempre en
    // "grupal" sin importar la parametrización, dejando todas las métricas
    // como si fueran SCRUM_MASTER.
    // ════════════════════════════════════════════════════════════════════

    private AprobarParametrizacionRequest requestAprobacion(String responsableCaptura) {
        return new AprobarParametrizacionRequest(
            "Objetivo", "Procedimiento", "indicador_variable", "Escala", "por_sprint",
            "Fuente académica", "Σ x", "SUMA", "unidades",
            responsableCaptura, // <- este es el campo que se está probando
            "indicador_variable", // nombreVariable (snake_case válido)
            null, null, null, null, null, null
        );
    }

    private Metrica metricaDeCategoria(UUID metricaId, String categoria) {
        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria cat = new MetricaCategoria();
        cat.setNombre(categoria);
        metrica.setCategoria(cat);
        return metrica;
    }

    private MetricParametrizacion parametrizacionPropuesta(UUID id, UUID proyectoId, UUID metricaId) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(id);
        p.setProyectoId(proyectoId);
        p.setMetricaId(metricaId);
        p.setStatus("propuesta");
        p.setVersion(1);
        return p;
    }

    @Test
    void aprobarParametrizacion_conResponsableCapturaEquipo_materializaVariableIndividual() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        when(parametrizacionRepository.findById(id))
            .thenReturn(Optional.of(parametrizacionPropuesta(id, proyectoId, metricaId)));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metricaDeCategoria(metricaId, "Calidad")));

        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, requestAprobacion("EQUIPO"));

        assertThat(result.getResponsableCaptura()).isEqualTo("EQUIPO");
        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTipoAlcance()).isEqualTo("individual");
    }

    @Test
    void aprobarParametrizacion_conResponsableCapturaScrumMaster_materializaVariableGrupal() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        when(parametrizacionRepository.findById(id))
            .thenReturn(Optional.of(parametrizacionPropuesta(id, proyectoId, metricaId)));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metricaDeCategoria(metricaId, "Calidad")));

        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, requestAprobacion("SCRUM_MASTER"));

        assertThat(result.getResponsableCaptura()).isEqualTo("SCRUM_MASTER");
        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTipoAlcance()).isEqualTo("grupal");
    }

    @Test
    void aprobarParametrizacion_sinResponsableCapturaExplicito_defaultScrumMaster_materializaVariableGrupal() {
        // No convertir silenciosamente a EQUIPO: quien no elige explícitamente
        // (responsableCaptura=null) debe conservar el comportamiento previo a
        // esta revisión (todas las métricas quedaban "grupal").
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        when(parametrizacionRepository.findById(id))
            .thenReturn(Optional.of(parametrizacionPropuesta(id, proyectoId, metricaId)));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metricaDeCategoria(metricaId, "Calidad")));

        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, requestAprobacion(null));

        assertThat(result.getResponsableCaptura()).isEqualTo("SCRUM_MASTER");
        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        verify(variableRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTipoAlcance()).isEqualTo("grupal");
    }

    @Test
    void aprobarParametrizacion_responsableCapturaInvalido_esRechazado() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        when(parametrizacionRepository.findById(id))
            .thenReturn(Optional.of(parametrizacionPropuesta(id, proyectoId, metricaId)));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, requestAprobacion("EL_QUE_QUIERA")))
            .isInstanceOf(ResponsableCapturaInvalidoException.class)
            .hasMessageContaining("EL_QUE_QUIERA");

        verify(parametrizacionRepository, never()).save(any());
        verify(variableRepository, never()).save(any());
    }

    @Test
    void guardarPropuesta_conResponsableCapturaEquipo_sePersisteEnLaEntidad() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad",
            "EQUIPO", // <- responsableCaptura, el campo que se está probando
            null, // propuestaIAJson
            null, // nombreVariable
            null, null, null, null, null, null // escala estructurada (6 campos)
        );

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        assertThat(result.getResponsableCaptura()).isEqualTo("EQUIPO");
    }

    @Test
    void guardarPropuesta_sinResponsableCapturaExplicito_defaultScrumMaster() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        // Constructor de compatibilidad (sin responsableCaptura) — simula un
        // llamador previo a esta revisión.
        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad",
            null, null, null, null, null, null, null, null
        );

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        assertThat(result.getResponsableCaptura()).isEqualTo("SCRUM_MASTER");
    }

    @Test
    void aprobarParametrizacion_siEsV2_debeMarcarV1ComoInactiva() {
        // Given
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();
        
        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setMetricaId(metricaId);
        v1.setProyectoId(proyectoId);
        v1.setStatus("aprobada");
        v1.setVersion(1);
        
        MetricParametrizacion v2 = new MetricParametrizacion();
        v2.setId(id);
        v2.setProyectoId(proyectoId);
        v2.setMetricaId(metricaId);
        v2.setStatus("propuesta");
        v2.setVersion(2);
        
        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj v2", "Proc v2", "Ind v2", "Esc v2", "por_sprint",
            "Fuente v2", "Formula v2", "SUMA", "unidad v2", null
        , null, null, null, null, null, null);
        
        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(v2));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(v1));
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        // When
        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(id, req);
        
        // Then
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo("aprobada");
        assertThat(v1.getStatus()).isEqualTo("inactiva"); // v1 marcada como inactiva
        verify(parametrizacionRepository, times(2)).save(any()); // v1 y v2 guardadas
    }

    // ========================================
    // TESTS FASE 16.10-G: aprobarParametrizacion() debe desactivar la última
    // versión con status='aprobada' (findUltimaVersionAprobada()), NO version-1.
    // Caso real encontrado en el E2E de SIG-VEL-02: v1 inactiva, v2 aprobada,
    // v3 propuesta huérfana (version-1 de v4), v4 propuesta a aprobar. Con la
    // lógica previa (buscar version-1), v3 nunca coincide con status='aprobada'
    // y v2 queda sin desactivar.
    // ========================================

    @Test
    void aprobarParametrizacion_conPropuestaHuerfanaIntermedia_desactivaUltimaAprobadaNoVersionMenosUno() {
        // Given
        mockAuthentication();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();
        UUID v4Id = UUID.randomUUID();

        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setMetricaId(metricaId);
        v1.setProyectoId(proyectoId);
        v1.setStatus("inactiva");
        v1.setVersion(1);

        MetricParametrizacion v2 = new MetricParametrizacion();
        v2.setMetricaId(metricaId);
        v2.setProyectoId(proyectoId);
        v2.setStatus("aprobada");
        v2.setVersion(2);

        MetricParametrizacion v3 = new MetricParametrizacion();
        v3.setMetricaId(metricaId);
        v3.setProyectoId(proyectoId);
        v3.setStatus("propuesta");
        v3.setVersion(3);

        MetricParametrizacion v4 = new MetricParametrizacion();
        v4.setId(v4Id);
        v4.setMetricaId(metricaId);
        v4.setProyectoId(proyectoId);
        v4.setStatus("propuesta");
        v4.setVersion(4);
        v4.setIndicadorVariable("Indicador v4");

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj v4", "Proc v4", "Ind v4", "Esc v4", "por_sprint",
            "Fuente v4", "Formula v4", "SUMA", "unidad v4", "nombre_v4"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(v4Id)).thenReturn(Optional.of(v4));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(true);
        // La corrección busca la última APROBADA (v2) — nunca por version-1 (v3).
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(v2));
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        // When
        MetricParametrizacion result = parametrizacionService.aprobarParametrizacion(v4Id, req);

        // Then
        assertThat(result.getVersion()).isEqualTo(4);
        assertThat(result.getStatus()).isEqualTo("aprobada");
        assertThat(v2.getStatus()).isEqualTo("inactiva"); // última aprobada real, desactivada
        assertThat(v1.getStatus()).isEqualTo("inactiva"); // v1 no fue tocada, sigue igual
        assertThat(v3.getStatus()).isEqualTo("propuesta"); // v3 (version-1 de v4) NO fue tocada
        verify(parametrizacionRepository, never())
            .findByMetricaIdAndProyectoIdAndVersion(any(), any(), anyInt());
        verify(parametrizacionRepository, times(2)).save(any()); // v2 (anterior) y v4 (actual)

        // Snapshot generado correctamente para v4
        assertThat(result.getConfiguracionAprobadaJson()).isNotNull();
        assertThat(result.getConfiguracionAprobadaJson()).contains("\"version\":4");

        // Variable creada correctamente para v4
        verify(variableRepository, times(1)).save(any());
    }


    @Test
    void aprobarParametrizacion_estadoNoPropuesta_debeRechazar() {
        // Given
        mockAuthentication();
        UUID id = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(UUID.randomUUID());
        parametrizacion.setStatus("aprobada"); // Ya aprobada
        
        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", null
        , null, null, null, null, null, null);
        
        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(any(), anyString()))
            .thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Solo se pueden aprobar parametrizaciones en estado 'propuesta'");
    }
    
    @Test
    void aprobarParametrizacion_usuarioNoMiembro_debeRechazar() {
        // Given
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        
        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", null
        , null, null, null, null, null, null);
        
        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString()))
            .thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no pertenece al proyecto");
    }
    
    @Test
    void obtenerUltimaVersionAprobada_existeAprobada_debeRetornar() {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion aprobada = new MetricParametrizacion();
        aprobada.setMetricaId(metricaId);
        aprobada.setProyectoId(proyectoId);
        aprobada.setStatus("aprobada");
        aprobada.setVersion(2);
        
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(aprobada));
        
        // When
        MetricParametrizacion result = parametrizacionService.obtenerUltimaVersionAprobada(metricaId, proyectoId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo("aprobada");
    }
    
    @Test
    void obtenerUltimaVersionAprobada_noExiste_debeLanzarExcepcion() {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> parametrizacionService.obtenerUltimaVersionAprobada(metricaId, proyectoId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No existe parametrización aprobada");
    }
    
    @Test
    void obtenerHistorialVersiones_debeRetornarTodasLasVersiones() {
        // Given
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        
        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setVersion(1);
        v1.setStatus("inactiva");
        
        MetricParametrizacion v2 = new MetricParametrizacion();
        v2.setVersion(2);
        v2.setStatus("aprobada");
        
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(v2, v1)); // Orden DESC por version
        
        // When
        List<MetricParametrizacion> historial = parametrizacionService.obtenerHistorialVersiones(metricaId, proyectoId);
        
        // Then
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getVersion()).isEqualTo(2);
        assertThat(historial.get(1).getVersion()).isEqualTo(1);
    }

    // ========================================
    // TESTS FASE 16.10-F: cálculo de versión en guardarPropuesta()
    // (usa MAX(version) real, no solo la última aprobada — evita colisionar
    // con V25 cuando ya existe una propuesta huérfana sin aprobar)
    // ========================================

    // 1) Caso real encontrado en el E2E de SIG-VEL-02.
    @Test
    void guardarPropuesta_conV1InactivaV2AprobadaV3Propuesta_calculaVersion4SinColisionar() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setVersion(1);
        v1.setStatus("inactiva");
        MetricParametrizacion v2 = new MetricParametrizacion();
        v2.setVersion(2);
        v2.setStatus("aprobada");
        MetricParametrizacion v3 = new MetricParametrizacion();
        v3.setVersion(3);
        v3.setStatus("propuesta");

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        // findHistorialVersiones ya ordena DESC por version — v3 (la máxima) primero.
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(v3, v2, v1));
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "SUMA"));

        assertThat(result.getVersion()).isEqualTo(4);

        // 5) El registro anterior (v3) no fue modificado.
        assertThat(v3.getVersion()).isEqualTo(3);
        assertThat(v3.getStatus()).isEqualTo("propuesta");
    }

    // 2) Primera propuesta: sin historial → version=1.
    @Test
    void guardarPropuesta_sinHistorial_calculaVersion1() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId); // ya stubea historial vacío

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "SUMA"));

        assertThat(result.getVersion()).isEqualTo(1);
    }

    // 3) Caso normal: solo v1 aprobada → version=2.
    @Test
    void guardarPropuesta_soloV1Aprobada_calculaVersion2() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setVersion(1);
        v1.setStatus("aprobada");

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(v1));
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "SUMA"));

        assertThat(result.getVersion()).isEqualTo(2);
    }

    // 4) Múltiples propuestas huérfanas: v1 aprobada, v2 inactiva, v3 propuesta,
    // v4 propuesta → version=5 (el máximo real, no el último aprobado + 1).
    @Test
    void guardarPropuesta_conMultiplesPropuestasHuerfanas_calculaVersionMaximaMasUno() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        MetricParametrizacion v1 = new MetricParametrizacion();
        v1.setVersion(1);
        v1.setStatus("aprobada");
        MetricParametrizacion v2 = new MetricParametrizacion();
        v2.setVersion(2);
        v2.setStatus("inactiva");
        MetricParametrizacion v3 = new MetricParametrizacion();
        v3.setVersion(3);
        v3.setStatus("propuesta");
        MetricParametrizacion v4 = new MetricParametrizacion();
        v4.setVersion(4);
        v4.setStatus("propuesta");

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(v4, v3, v2, v1)); // DESC
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(
            requestConTipoOperacion(metricaId, proyectoId, "SUMA"));

        assertThat(result.getVersion()).isEqualTo(5);
        assertThat(v3.getVersion()).isEqualTo(3);
        assertThat(v4.getVersion()).isEqualTo(4);
    }

    // 6) La nueva propuesta conserva TODOS los campos enviados, con la versión
    // ya calculada sobre el máximo real (no sobre la última aprobada).
    @Test
    void guardarPropuesta_conVersionCalculadaSobreMaximo_conservaTodosLosCamposEnviados() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        MetricParametrizacion v3 = new MetricParametrizacion();
        v3.setVersion(3);
        v3.setStatus("propuesta");

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId))
            .thenReturn(List.of(v3));
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId,
            "Objetivo real", "Procedimiento real", "Indicador real", "Escala real",
            "por_sprint", "Fuente real", "Σ(x)", "SUMA", "unidad real",
            "{\"raw\":true}", "nombre_variable_real"
        , null, null, null, null, null, null);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        assertThat(result.getVersion()).isEqualTo(4);
        assertThat(result.getObjetivo()).isEqualTo("Objetivo real");
        assertThat(result.getProcedimiento()).isEqualTo("Procedimiento real");
        assertThat(result.getIndicadorVariable()).isEqualTo("Indicador real");
        assertThat(result.getEscala()).isEqualTo("Escala real");
        assertThat(result.getFrecuenciaCaptura()).isEqualTo("por_sprint");
        assertThat(result.getFuenteAcademica()).isEqualTo("Fuente real");
        assertThat(result.getFormulaAcademica()).isEqualTo("Σ(x)");
        assertThat(result.getTipoOperacion()).isEqualTo("SUMA");
        assertThat(result.getUnidadResultado()).isEqualTo("unidad real");
        assertThat(result.getNombreVariable()).isEqualTo("nombre_variable_real");
    }

    // ========================================
    // TESTS FASE 16.10-E: nombreVariable (identificador técnico de Variable)
    // ========================================

    private static final String INDICADOR_LARGO_REAL_SIG_VEL_02 =
        "El indicador principal es el 'número de impedimentos que bloquearon al equipo'. " +
        "La variable a contar es cada ocurrencia única de un impedimento que bloqueó el " +
        "progreso del equipo durante el sprint.";

    // A) El prompt expone nombreVariable como campo estructurado del schema.
    @Test
    void generarPropuestas_promptContieneNombreVariableComoCampoEstructurado() {
        when(geminiService.generate(anyString())).thenReturn("texto inválido sin JSON");

        parametrizacionService.generarPropuestas(request);

        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(geminiService).generate(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("\"nombreVariable\"");
        assertThat(prompt).contains("nombreVariable debe ser un identificador técnico");
        assertThat(prompt).contains("snake_case");
    }

    // C) guardarPropuesta() conserva nombreVariable en la respuesta (fuente de verdad
    // para el frontend al aprobar, vía campo transient — ver MetricParametrizacion.java).
    @Test
    void guardarPropuesta_conNombreVariable_loConservaEnLaRespuesta() {
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Σ x", "SUMA", "unidad", null, "impedimentos_registrados"
        , null, null, null, null, null, null);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        assertThat(result.getNombreVariable()).isEqualTo("impedimentos_registrados");
    }

    @Test
    void guardarPropuesta_sinNombreVariable_esAceptado() {
        // Campo opcional: ausente debe seguir siendo válido (compatibilidad).
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        mockGuardarPropuestaHappyPath(metricaId, proyectoId);

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Σ x", "SUMA", "unidad", null, null
        , null, null, null, null, null, null);

        MetricParametrizacion result = parametrizacionService.guardarPropuesta(req);

        assertThat(result.getNombreVariable()).isNull();
    }

    // D + E) Al aprobar con nombreVariable explícito y válido, Variable.nombre debe ser
    // EXACTAMENTE ese valor — indicadorVariable (prosa larga, caso real SIG-VEL-02) se
    // ignora por completo como fuente de nombre técnico.
    @Test
    void aprobarParametrizacion_conNombreVariableExplicito_creaVariableConEseNombreIgnorandoIndicadorVariable() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo", "Procedimiento", INDICADOR_LARGO_REAL_SIG_VEL_02, "Escala", "por_sprint",
            "Fuente", "Σ(Impedimento_Bloqueante_i)", "SUMA", "impedimentos", "impedimentos_registrados"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);

        parametrizacionService.aprobarParametrizacion(id, req);

        verify(variableRepository).save(variableCaptor.capture());
        assertThat(variableCaptor.getValue().getNombre()).isEqualTo("impedimentos_registrados");
    }

    // F) nombreVariable de 121+ caracteres se rechaza (nunca debe llegar a un INSERT
    // que falle con DataException/500 por overflow de variables.nombre VARCHAR(120)).
    @Test
    void aprobarParametrizacion_conNombreVariableDe121Caracteres_esRechazado() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        String nombre121 = "a".repeat(121);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", nombre121
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(NombreVariableInvalidoException.class)
            .hasMessageContaining("120");

        verify(variableRepository, never()).save(any());
    }

    // G) nombreVariable con espacios o prosa libre se rechaza (formato inválido).
    @Test
    void aprobarParametrizacion_conNombreVariableConEspacios_esRechazado() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", "nombre con espacios"
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(NombreVariableInvalidoException.class);

        verify(variableRepository, never()).save(any());
    }

    @Test
    void aprobarParametrizacion_conNombreVariableComoTextoLibre_esRechazado() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", INDICADOR_LARGO_REAL_SIG_VEL_02
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(NombreVariableInvalidoException.class);

        verify(variableRepository, never()).save(any());
    }

    // H) Compatibilidad: sin nombreVariable, un indicadorVariable corto que sí permite
    // extracción limpia debe seguir funcionando exactamente igual que antes.
    @Test
    void aprobarParametrizacion_sinNombreVariable_indicadorCortoPermiteExtraccion_siguefuncionando() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "problemas_reportados", "Esc", "por_sprint",
            "Fuente", "Σ x", "SUMA", "unidad", null
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        parametrizacionService.aprobarParametrizacion(id, req);
        verify(variableRepository).save(variableCaptor.capture());
        assertThat(variableCaptor.getValue().getNombre()).isEqualTo("problemas_reportados");
    }

    // I) Fallback peligroso: sin nombreVariable y con el indicadorVariable largo real de
    // SIG-VEL-02 (sin snake_case), debe rechazarse de forma CONTROLADA — nunca debe volver
    // a producir un INSERT que falle con DataException/500 por overflow.
    @Test
    void aprobarParametrizacion_sinNombreVariable_indicadorLargoSinSnakeCase_esRechazadoControladamente() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", INDICADOR_LARGO_REAL_SIG_VEL_02, "Esc", "por_sprint",
            "Fuente", "Formula", "SUMA", "unidad", null
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(NombreVariableInvalidoException.class);

        // Nunca debe intentar guardar la variable: se rechaza ANTES del INSERT.
        verify(variableRepository, never()).save(any());
    }

    // J) Regresión: el caso real de SIG-SC-02 (indicadorVariable con snake_case embebido,
    // aprobado antes de este incremento, sin nombreVariable) sigue extrayéndose igual.
    @Test
    void aprobarParametrizacion_regresionSIGSC02_indicadorConSnakeCaseSigueExtrayendoseIgual() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo", "Procedimiento",
            "Problemas_Reportados_Cliente_Sprint (variable: 'problema_reportado_individual')",
            "Escala", "por_sprint", "Fuente", "Σ(problemas_reportados_cliente)", "SUMA", "problemas",
            null
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        parametrizacionService.aprobarParametrizacion(id, req);
        verify(variableRepository).save(variableCaptor.capture());
        assertThat(variableCaptor.getValue().getNombre()).isEqualTo("problema_reportado_individual");
    }

    // L) Flujo completo de servicio: guardar propuesta → aprobar → crear variable.
    // nombreVariable debe llegar intacto desde el request de guardar hasta Variable.nombre.
    @Test
    void flujoCompleto_guardarPropuesta_aprobar_crearVariable_nombreVariableLlegaHastaVariableNombre() {
        mockAuthentication();
        UUID metricaId = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.findHistorialVersiones(metricaId, proyectoId)).thenReturn(List.of());
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GuardarPropuestaRequest guardarReq = new GuardarPropuestaRequest(
            metricaId, proyectoId, "Objetivo", "Procedimiento", INDICADOR_LARGO_REAL_SIG_VEL_02,
            "Escala", "por_sprint", "Fuente", "Σ(I_sprint)", "SUMA", "impedimentos",
            "{}", "impedimentos_registrados"
        , null, null, null, null, null, null);

        // Paso 1: guardar propuesta (como "Guardar como nueva propuesta" en la UI)
        MetricParametrizacion guardada = parametrizacionService.guardarPropuesta(guardarReq);
        assertThat(guardada.getNombreVariable()).isEqualTo("impedimentos_registrados");

        // Paso 2: el frontend arma el request de aprobar a partir de la respuesta de
        // guardar (propuestaPendiente) — incluido nombreVariable (FASE 16.10-D/E).
        guardada.setId(UUID.randomUUID());
        guardada.setMetricaId(metricaId);
        when(parametrizacionRepository.findById(guardada.getId())).thenReturn(Optional.of(guardada));

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Calidad");
        metrica.setCategoria(categoria);
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        AprobarParametrizacionRequest aprobarReq = new AprobarParametrizacionRequest(
            guardada.getObjetivo(), guardada.getProcedimiento(), guardada.getIndicadorVariable(),
            guardada.getEscala(), guardada.getFrecuenciaCaptura(), guardada.getFuenteAcademica(),
            guardada.getFormulaAcademica(), guardada.getTipoOperacion(), guardada.getUnidadResultado(),
            guardada.getNombreVariable()
        , null, null, null, null, null, null);

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);
        parametrizacionService.aprobarParametrizacion(guardada.getId(), aprobarReq);
        verify(variableRepository).save(variableCaptor.capture());

        assertThat(variableCaptor.getValue().getNombre()).isEqualTo("impedimentos_registrados");
    }

    // ========================================
    // TESTS FASE 3: nombreVariable con lista separada por comas (múltiples
    // variables explícitas, necesario para FAT y Deuda técnica gestionada:
    // cada una requiere 2 variables técnicas, no 1).
    // ========================================

    @Test
    void aprobarParametrizacion_conDosNombresVariableSeparadosPorComa_creaLasDosVariablesVinculadas() {
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo FAT", "Procedimiento FAT", "ACAT y ACR", "Escala FAT", "por_sprint",
            "FASE16_8_7_ESPECIFICACION_METODOLOGICA", "(ACAT / ACR) × 100", "FORMULA", "%",
            "acat,acr"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Flexibilidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);

        parametrizacionService.aprobarParametrizacion(id, req);

        verify(variableRepository, times(2)).save(variableCaptor.capture());
        List<Variable> creadas = variableCaptor.getAllValues();
        assertThat(creadas).extracting(Variable::getNombre).containsExactlyInAnyOrder("acat", "acr");
        // Ambas vinculadas a la MISMA parametrización y versión.
        assertThat(creadas).allSatisfy(v -> {
            assertThat(v.getParametrizacionId()).isEqualTo(id);
            assertThat(v.getParametrizacionVersion()).isEqualTo(1);
            assertThat(v.getProyectoId()).isEqualTo(proyectoId);
        });
    }

    @Test
    void aprobarParametrizacion_conNombresVariableConEspaciosAlrededorDeLaComa_seNormalizanCorrectamente() {
        // "deuda_gestionada, deuda_identificada" (espacio tras la coma, como
        // suele escribirse a mano) debe funcionar igual que sin espacios.
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo", "Procedimiento", "Indicador", "Escala", "por_sprint",
            "Adaptación PRODOX", "(deuda_gestionada / deuda_identificada) × 100", "FORMULA", "%",
            "deuda_gestionada, deuda_identificada"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Flexibilidad");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);

        parametrizacionService.aprobarParametrizacion(id, req);

        verify(variableRepository, times(2)).save(variableCaptor.capture());
        assertThat(variableCaptor.getAllValues())
            .extracting(Variable::getNombre)
            .containsExactlyInAnyOrder("deuda_gestionada", "deuda_identificada");
    }

    @Test
    void aprobarParametrizacion_conListaDeNombresConUnElementoInvalido_rechazaTodoAntesDeCrearNinguna() {
        // Si UNO de los nombres de la lista es inválido, no debe crearse
        // ninguna variable (todo-o-nada) — nunca datos parciales.
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Obj", "Proc", "Ind", "Esc", "por_sprint",
            "Fuente", "Formula", "FORMULA", "%", "acat,ACR INVALIDO"
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);

        assertThatThrownBy(() -> parametrizacionService.aprobarParametrizacion(id, req))
            .isInstanceOf(NombreVariableInvalidoException.class);

        verify(parametrizacionRepository, never()).save(any());
        verify(variableRepository, never()).save(any());
    }

    @Test
    void aprobarParametrizacion_conUnSoloNombreVariable_sinComa_siguefuncionandoIgualQueAntes() {
        // Regresión explícita: el caso de siempre (1 sola variable, sin coma)
        // no debe verse afectado por el nuevo soporte de listas.
        mockAuthentication();
        UUID id = UUID.randomUUID();
        UUID proyectoId = UUID.randomUUID();
        UUID metricaId = UUID.randomUUID();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(id);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setStatus("propuesta");
        parametrizacion.setVersion(1);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo Defectos", "Procedimiento", "defectos_totales", "Escala", "por_sprint",
            "Decisión de proyecto", "SUMA(defectos_totales)", "SUMA", "defectos", "defectos_totales"
        , null, null, null, null, null, null);

        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Significado");
        metrica.setCategoria(categoria);

        when(parametrizacionRepository.findById(id)).thenReturn(Optional.of(parametrizacion));
        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica));

        org.mockito.ArgumentCaptor<Variable> variableCaptor = org.mockito.ArgumentCaptor.forClass(Variable.class);

        parametrizacionService.aprobarParametrizacion(id, req);

        verify(variableRepository, times(1)).save(variableCaptor.capture());
        assertThat(variableCaptor.getValue().getNombre()).isEqualTo("defectos_totales");
    }

    @Test
    void aprobarParametrizacion_dosParametrizacionesDistintasConVariablesDeDosNombres_noMezclanVariables() {
        // FAT (acat, acr) y Deuda técnica (deuda_gestionada, deuda_identificada)
        // aprobadas en secuencia no deben mezclar sus variables entre sí: cada
        // Variable debe quedar vinculada exclusivamente a SU parametrización.
        mockAuthentication();
        UUID proyectoId = UUID.randomUUID();

        UUID idFat = UUID.randomUUID();
        UUID metricaFatId = UUID.randomUUID();
        MetricParametrizacion fat = new MetricParametrizacion();
        fat.setId(idFat);
        fat.setProyectoId(proyectoId);
        fat.setMetricaId(metricaFatId);
        fat.setStatus("propuesta");
        fat.setVersion(1);

        UUID idDeuda = UUID.randomUUID();
        UUID metricaDeudaId = UUID.randomUUID();
        MetricParametrizacion deuda = new MetricParametrizacion();
        deuda.setId(idDeuda);
        deuda.setProyectoId(proyectoId);
        deuda.setMetricaId(metricaDeudaId);
        deuda.setStatus("propuesta");
        deuda.setVersion(1);

        MetricaCategoria categoria = new MetricaCategoria();
        categoria.setNombre("Flexibilidad");
        Metrica metricaFat = new Metrica();
        metricaFat.setId(metricaFatId);
        metricaFat.setCategoria(categoria);
        Metrica metricaDeuda = new Metrica();
        metricaDeuda.setId(metricaDeudaId);
        metricaDeuda.setCategoria(categoria);

        when(projectMemberRepository.existsByProyectoIdAndUserId(eq(proyectoId), anyString())).thenReturn(true);
        when(parametrizacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(parametrizacionRepository.findById(idFat)).thenReturn(Optional.of(fat));
        when(metricaRepository.findById(metricaFatId)).thenReturn(Optional.of(metricaFat));
        AprobarParametrizacionRequest reqFat = new AprobarParametrizacionRequest(
            "Obj FAT", "Proc FAT", "Ind FAT", "Esc FAT", "por_sprint",
            "Fuente FAT", "(ACAT / ACR) × 100", "FORMULA", "%", "acat,acr"
        , null, null, null, null, null, null);

        when(parametrizacionRepository.findById(idDeuda)).thenReturn(Optional.of(deuda));
        when(metricaRepository.findById(metricaDeudaId)).thenReturn(Optional.of(metricaDeuda));
        AprobarParametrizacionRequest reqDeuda = new AprobarParametrizacionRequest(
            "Obj Deuda", "Proc Deuda", "Ind Deuda", "Esc Deuda", "por_sprint",
            "Fuente Deuda", "(deuda_gestionada / deuda_identificada) × 100", "FORMULA", "%",
            "deuda_gestionada,deuda_identificada"
        , null, null, null, null, null, null);

        org.mockito.ArgumentCaptor<Variable> captor = org.mockito.ArgumentCaptor.forClass(Variable.class);

        parametrizacionService.aprobarParametrizacion(idFat, reqFat);
        parametrizacionService.aprobarParametrizacion(idDeuda, reqDeuda);

        verify(variableRepository, times(4)).save(captor.capture());
        List<Variable> todas = captor.getAllValues();

        List<Variable> deFat = todas.stream().filter(v -> v.getParametrizacionId().equals(idFat)).toList();
        List<Variable> deDeuda = todas.stream().filter(v -> v.getParametrizacionId().equals(idDeuda)).toList();

        assertThat(deFat).hasSize(2);
        assertThat(deFat).extracting(Variable::getNombre).containsExactlyInAnyOrder("acat", "acr");
        assertThat(deDeuda).hasSize(2);
        assertThat(deDeuda).extracting(Variable::getNombre)
            .containsExactlyInAnyOrder("deuda_gestionada", "deuda_identificada");
    }
}
