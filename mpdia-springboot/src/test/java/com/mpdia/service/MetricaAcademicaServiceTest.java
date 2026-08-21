// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.1-A: Tests unitarios para MetricaAcademicaService
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.*;
import com.mpdia.entity.*;
import com.mpdia.formula.FormulaEvaluator;
import com.mpdia.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para MetricaAcademicaService.
 * 
 * Cubre:
 * - Propuesta de parametrización con Gemini
 * - Guardar propuesta académica
 * - Ejecución de métrica SIG-SC-02
 * - Validaciones
 * - Cálculo determinista
 * - Persistencia y reproducibilidad
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricaAcademicaServiceTest {
    
    @Mock
    private GeminiService geminiService;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private MetricaRepository metricaRepo;
    
    @Mock
    private ProyectoRepository proyectoRepo;
    
    @Mock
    private SprintRepository sprintRepo;
    
    @Mock
    private MetricParametrizacionRepository parametrizacionRepo;
    
    @Mock
    private VariableRepository variableRepo;

    @Mock
    private ResultadoMetricaRepository resultadoRepo;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private EjecucionService ejecucionService;

    @InjectMocks
    private MetricaAcademicaService service;
    
    private UUID metricaId;
    private UUID proyectoId;
    private UUID sprintId;
    private UUID userId;
    private String userEmail;
    
    @BeforeEach
    void setUp() {
        metricaId = UUID.randomUUID();
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        userId = UUID.randomUUID();
        userEmail = "test@test.com";
        
        // Mock Security Context
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(auth.getName()).thenReturn(userEmail);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.setContext(securityContext);
        
        // ObjectMapper y FormulaEvaluator reales para estas pruebas (motor determinista, sin mock)
        service = new MetricaAcademicaService(
            geminiService,
            new ObjectMapper(),
            metricaRepo,
            proyectoRepo,
            sprintRepo,
            parametrizacionRepo,
            variableRepo,
            resultadoRepo,
            projectMemberRepository,
            ejecucionService,
            new FormulaEvaluator()
        );
    }
    
    // ========================================
    // Tests: Propuesta de Parametrización
    // ========================================
    
    @Test
    void generarPropuestaAcademica_conDatosValidos_retornaPropuesta() {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados por el cliente",
            "Total de problemas reportados por el cliente durante el sprint",
            "Guerrero-Calvache & Hernández (2024), p. 13, Tabla 9",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        String geminiResponse = """
            [{
                "titulo": "Parametrización académica",
                "objetivo": "Medir problemas reportados",
                "procedimiento": "Contar problemas",
                "indicadorVariable": "problemas_reportados",
                "escala": "INTEGER >= 0",
                "justificacion": "Basado en fuente académica"
            }]
            """;
        
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);
        
        // Act
        PropuestaParametrizacionDto propuesta = service.generarPropuestaAcademica(request);
        
        // Assert
        assertNotNull(propuesta);
        assertEquals("Parametrización académica", propuesta.titulo());
        verify(geminiService, times(1)).generate(anyString());
    }
    
    @Test
    void generarPropuestaAcademica_cuandoGeminiFalla_retornaFallback() {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados por el cliente",
            "Definición",
            "Fuente académica",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        when(geminiService.generate(anyString())).thenThrow(new RuntimeException("Gemini error"));
        
        // Act
        PropuestaParametrizacionDto propuesta = service.generarPropuestaAcademica(request);
        
        // Assert
        assertNotNull(propuesta);
        assertTrue(propuesta.titulo().contains("Problemas reportados por el cliente"));
    }
    
    @Test
    void guardarPropuestaAcademica_estadoInicial_esPropuesta() {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados",
            "Definición",
            "Fuente académica",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        PropuestaParametrizacionDto propuesta = new PropuestaParametrizacionDto(
            "Título",
            "Objetivo",
            "Procedimiento",
            "Variable",
            "Escala",
            "por_sprint",
            "Fuente académica",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "Justificación",
            "problemas_reportados"
        );

        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.empty());
        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        MetricParametrizacion resultado = service.guardarPropuestaAcademica(request, propuesta);
        
        // Assert
        assertNotNull(resultado);
        assertEquals("propuesta", resultado.getStatus());
        assertEquals(1, resultado.getVersion());
        assertEquals("Fuente académica", resultado.getFuenteAcademica());
        assertEquals("Σ problemas_reportados", resultado.getFormulaAcademica());
        assertEquals("SUMA", resultado.getTipoOperacion());
        assertEquals("problemas", resultado.getUnidadResultado());
    }
    
    @Test
    void guardarPropuestaAcademica_versionado_incrementaVersion() {
        // Arrange
        MetricaAcademicaRequest request = new MetricaAcademicaRequest(
            proyectoId,
            metricaId,
            "SIG-SC-02",
            "Problemas reportados",
            "Definición",
            "Fuente académica",
            "Σ problemas_reportados",
            "SUMA",
            "problemas",
            "por_sprint"
        );
        
        PropuestaParametrizacionDto propuesta = new PropuestaParametrizacionDto(
            "Título", "Objetivo", "Procedimiento", "Variable", "Escala", "por_sprint",
            "Fuente académica", "Σ problemas_reportados", "SUMA", "problemas", "Justificación",
            "problemas_reportados"
        );
        
        MetricParametrizacion existente = new MetricParametrizacion();
        existente.setVersion(1);
        
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(existente));
        when(parametrizacionRepo.save(any(MetricParametrizacion.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        MetricParametrizacion resultado = service.guardarPropuestaAcademica(request, propuesta);
        
        // Assert
        assertEquals(2, resultado.getVersion());
    }
    
    // ========================================
    // Tests: Ejecución de Métrica
    // ========================================
    
    @Test
    void ejecutarMetricaAcademica_SIG_SC_02_valor7_retorna7() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId,
            sprintId,
            valores
        );
        
        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("7")));
        assertEquals("problemas", resultado.unidad());
        assertEquals("suma", resultado.tipoCalculo());
        assertEquals("calculado", resultado.estado());
    }
    
    @Test
    void ejecutarMetricaAcademica_sinParametrizacionAprobada_lanzaExcepcion() {
        // Arrange
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();
        
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.empty());
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act & Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        
        assertTrue(ex.getMessage().contains("No existe parametrización aprobada"));
    }
    
    @Test
    void ejecutarMetricaAcademica_usuarioSinPermisos_lanzaExcepcion() {
        // Arrange
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();
        
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(false);
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act & Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        
        assertTrue(ex.getMessage().contains("no pertenece al proyecto"));
    }
    
    @Test
    void ejecutarMetricaAcademica_sprintDeOtroProyecto_lanzaExcepcion() {
        // Arrange
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();
        sprint.setProyectoId(UUID.randomUUID()); // Diferente proyecto
        
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        
        assertTrue(ex.getMessage().contains("no pertenece al proyecto"));
    }
    
    // ========================================
    // Tests: Validaciones de Variables
    // ========================================
    
    @Test
    void ejecutarMetricaAcademica_variableAusente_lanzaExcepcion() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("otra_variable", 7); // Variable incorrecta
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        
        assertTrue(ex.getMessage().contains("Falta el valor"));
    }
    
    @Test
    void ejecutarMetricaAcademica_parametrizacionSinVariables_lanzaExcepcionSinAdivinar() {
        // Arrange: parametrización aprobada pero SIN variables asociadas
        // (la ejecución NO debe crear ni adivinar variables por código de métrica)
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();
        MetricParametrizacion parametrizacion = crearParametrizacionAprobada();

        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(any(), anyInt()))
            .thenReturn(List.of());

        Map<String, Object> valores = Map.of("cualquier_variable", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act & Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );

        assertTrue(ex.getMessage().contains("no tiene variables configuradas"));
        // Nunca debe intentar crear/guardar una variable adivinada
        verify(variableRepo, never()).save(any());
    }

    @Test
    void ejecutarMetricaAcademica_valorNegativo_lanzaExcepcion() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("problemas_reportados", -5);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        
        assertTrue(ex.getMessage().contains("no puede ser negativo"));
    }
    
    @Test
    void ejecutarMetricaAcademica_valorCero_esValido() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("problemas_reportados", 0);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);
        
        // Assert
        assertEquals(0, resultado.resultado().compareTo(BigDecimal.ZERO));
    }
    
    // ========================================
    // Tests: Persistencia y Reproducibilidad
    // ========================================
    
    @Test
    void ejecutarMetricaAcademica_guardaParametrizacionId() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);
        
        // Assert
        assertNotNull(resultado.parametrizacionId());
        assertNotNull(resultado.parametrizacionVersion());
        assertEquals(1, resultado.parametrizacionVersion());
    }
    
    @Test
    void ejecutarMetricaAcademica_guardaExpresionUtilizada() {
        // Arrange
        setupEjecucionExitosa();
        
        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );
        
        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);
        
        // Assert
        assertEquals("Σ problemas_reportados", resultado.expresion());
    }
    
    // ========================================
    // Tests: Cálculo FORMULA (FASE 2 — Fórmulas académicas de las 3 métricas
    // pendientes: FAT, Deuda técnica, y validación genérica A/B*100)
    // ========================================

    @Test
    void ejecutarMetricaAcademica_formulaFAT_ACATsobreACRpor100_calculaCorrectamente() {
        // Arrange: FAT = (ACAT / ACR) * 100 — decisión de proyecto autorizada,
        // ver docs/FASE16_8_7_ESPECIFICACION_METODOLOGICA.md
        Variable acat = crearVariableConNombre("ACAT");
        Variable acr = crearVariableConNombre("ACR");
        setupEjecucionFormula("(ACAT / ACR) * 100", List.of(acat, acr));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("ACAT", 8);
        valores.put("ACR", 10);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: (8/10)*100 = 80.0000
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("80.0000")));
        assertEquals("formula", resultado.tipoCalculo());
        assertEquals("calculado", resultado.estado());
    }

    @Test
    void ejecutarMetricaAcademica_formulaDeudaTecnica_gestionadaSobreIdentificadaPor100_calculaCorrectamente() {
        // Arrange: Deuda técnica = (deuda_gestionada / deuda_identificada) * 100
        // — adaptación MPDIA explícita, NO atribuida a Hernández (no tiene fórmula
        // fuente en el estudio académico).
        Variable gestionada = crearVariableConNombre("deuda_gestionada");
        Variable identificada = crearVariableConNombre("deuda_identificada");
        setupEjecucionFormula(
            "(deuda_gestionada / deuda_identificada) * 100",
            List.of(gestionada, identificada)
        );

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("deuda_gestionada", 3);
        valores.put("deuda_identificada", 12);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: (3/12)*100 = 25.0000
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("25.0000")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaGenericaAsobreBpor100_calculaCorrectamente() {
        // Arrange: patrón simple A/B*100 usado como caso de control del motor,
        // independiente de las métricas académicas concretas.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A / B * 100", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 4);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: (1/4)*100 = 25.0000
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("25.0000")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaConVariableNoReconocida_lanzaExcepcionControlada() {
        // Arrange: la fórmula académica referencia "C", que no existe entre las
        // variables de la parametrización (solo A y B) — debe rechazarse de forma
        // controlada, nunca dejar que llegue sin traducir al tokenizer.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A / C * 100", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 4);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act & Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        assertTrue(ex.getMessage().contains("no reconocida"));
        assertTrue(ex.getMessage().contains("'C'"));
    }

    @Test
    void ejecutarMetricaAcademica_formulaSinDefinir_lanzaExcepcionControlada() {
        // Arrange: tipoOperacion FORMULA pero formulaAcademica nula/vacía.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula(null, List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 4);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act & Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        assertTrue(ex.getMessage().contains("fórmula académica"));
    }

    @Test
    void ejecutarMetricaAcademica_formulaConValorExplicitoNull_lanzaExcepcionControladaNoNPE() {
        // Arrange: uno de los valores llega explícitamente como null en el mapa
        // capturado (no ausente del request, sino null) — ASTNode.VariableNode.evaluate()
        // ya distingue este caso y lanza IllegalArgumentException; este test prueba
        // que el flujo completo de MetricaAcademicaService no lo convierte en NPE.
        // Como convertirABigDecimal() rechaza null antes incluso de llegar al
        // evaluador, se verifica aquí en el punto exacto donde puede ocurrir un
        // valor null real: dentro del propio motor de fórmulas.
        FormulaEvaluator evaluator = new FormulaEvaluator();
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        Map<UUID, BigDecimal> valoresConNull = new HashMap<>();
        valoresConNull.put(a.getId(), new BigDecimal("1"));
        valoresConNull.put(b.getId(), null); // valor explícitamente null

        String expresion = "${" + a.getId() + "} / ${" + b.getId() + "} * 100";

        // Act & Assert: excepción controlada, nunca NullPointerException
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> evaluator.evaluarFormula(expresion, valoresConNull)
        );
        assertTrue(ex.getMessage().contains("no encontrada o sin valor")
            || ex.getMessage().contains("Error evaluando fórmula"));
    }

    @Test
    void ejecutarMetricaAcademica_formulaDivisionEntreCero_lanzaArithmeticException() {
        // Arrange
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A / B * 100", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 5);
        valores.put("B", 0);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act & Assert
        assertThrows(
            ArithmeticException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
    }

    @Test
    void ejecutarMetricaAcademica_formulaResultadoNegativo_seCalculaSinRechazo() {
        // Arrange: a diferencia de SUMA, FORMULA no tiene (ni se le agrega en esta
        // fase) una regla de "no negativos" — se documenta el comportamiento
        // existente, sin inventar una restricción nueva no solicitada.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A - B", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 2);
        valores.put("B", 5);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: 2 - 5 = -3.0000
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("-3.0000")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaEscalaResultado_seRedondeaA4Decimales() {
        // Arrange: preserva la escala/precisión ya establecida por FormulaEvaluator
        // (SCALE=4, HALF_UP), sin introducir un nuevo esquema de redondeo.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A / B", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 3);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: 1/3 = 0.3333 (HALF_UP a 4 decimales)
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("0.3333")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaPreservaFormulaAcademicaComoTrazabilidad() {
        // Arrange: expresionUtilizada debe conservar el texto humano original,
        // no la expresión traducida a ${uuid}.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("(A / B) * 100", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 2);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert
        assertEquals("(A / B) * 100", resultado.expresion());
    }

    // ========================================
    // Tests: Matching case-insensitive de nombres + normalización "×"→"*"
    // (FASE 3 — necesarios para FAT: fórmula académica "(ACAT / ACR) × 100"
    // con variables técnicas "acat"/"acr", ambas en minúscula por convención)
    // ========================================

    @Test
    void ejecutarMetricaAcademica_formulaFAT_conMayusculasYSignoDeMultiplicacion_calculaCorrectamente() {
        // Arrange: fórmula académica EXACTA tal como fue autorizada, con "×" y
        // variables en mayúsculas (ACAT, ACR), mientras las variables técnicas
        // de la parametrización están en minúsculas (acat, acr).
        Variable acat = crearVariableConNombre("acat");
        Variable acr = crearVariableConNombre("acr");
        setupEjecucionFormula("(ACAT / ACR) × 100", List.of(acat, acr));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("acat", 8);
        valores.put("acr", 10);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        // Act
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        // Assert: (8/10)*100 = 80.0000
        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("80.0000")));
        assertEquals("formula", resultado.tipoCalculo());
    }

    @Test
    void ejecutarMetricaAcademica_formulaConACATmayuscula_resuelveContraVariableAcatMinuscula() {
        // Arrange: aísla específicamente el caso ACAT(fórmula) -> acat(variable).
        Variable acat = crearVariableConNombre("acat");
        Variable acr = crearVariableConNombre("acr");
        setupEjecucionFormula("ACAT / acr * 100", List.of(acat, acr)); // ACR ya en minúscula aquí

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("acat", 5);
        valores.put("acr", 20);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("25.0000")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaConACRmayuscula_resuelveContraVariableAcrMinuscula() {
        // Arrange: aísla específicamente el caso ACR(fórmula) -> acr(variable).
        Variable acat = crearVariableConNombre("acat");
        Variable acr = crearVariableConNombre("acr");
        setupEjecucionFormula("acat / ACR * 100", List.of(acat, acr)); // ACAT ya en minúscula aquí

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("acat", 3);
        valores.put("acr", 12);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("25.0000")));
    }

    @Test
    void ejecutarMetricaAcademica_formulaFAT_preservaTextoOriginalConSignoDeMultiplicacion() {
        // Arrange: expresionUtilizada debe conservar "×" íntegro — la
        // normalización a "*" es solo interna/temporal para el tokenizer,
        // nunca se escribe de vuelta sobre el texto académico.
        Variable acat = crearVariableConNombre("acat");
        Variable acr = crearVariableConNombre("acr");
        setupEjecucionFormula("(ACAT / ACR) × 100", List.of(acat, acr));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("acat", 1);
        valores.put("acr", 2);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals("(ACAT / ACR) × 100", resultado.expresion());
    }

    @Test
    void ejecutarMetricaAcademica_formulaFAT_variableDesconocida_siguendoLanzandoExcepcionControlada() {
        // Arrange: la normalización de caso/signo no debe volver permisivo el
        // rechazo de variables realmente inexistentes en la parametrización.
        Variable acat = crearVariableConNombre("acat");
        Variable acr = crearVariableConNombre("acr");
        setupEjecucionFormula("(ACAT / ACX) × 100", List.of(acat, acr)); // "ACX" no existe

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("acat", 1);
        valores.put("acr", 2);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> service.ejecutarMetricaAcademica(metricaId, request)
        );
        assertTrue(ex.getMessage().contains("no reconocida"));
    }

    @Test
    void ejecutarMetricaAcademica_formulaConAsteriscoAscii_siguendoFuncionandoIgual() {
        // Arrange: fórmulas que ya usan "*" (ASCII) en vez de "×" deben seguir
        // funcionando exactamente igual tras agregar la normalización.
        Variable a = crearVariableConNombre("A");
        Variable b = crearVariableConNombre("B");
        setupEjecucionFormula("A / B * 100", List.of(a, b));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("A", 1);
        valores.put("B", 4);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("25.0000")));
        assertEquals("A / B * 100", resultado.expresion());
    }

    // ========================================
    // Tests: Regresión SUMA/PROMEDIO/DIRECTO (deben seguir funcionando igual
    // tras agregar el case FORMULA)
    // ========================================

    @Test
    void ejecutarMetricaAcademica_regresionSuma_siguendoFuncionandoIgual() {
        setupEjecucionExitosa();

        Map<String, Object> valores = Map.of("problemas_reportados", 7);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("7")));
        assertEquals("suma", resultado.tipoCalculo());
    }

    @Test
    void ejecutarMetricaAcademica_regresionPromedio_siguendoFuncionandoIgual() {
        Variable v1 = crearVariableConNombre("valor_a");
        Variable v2 = crearVariableConNombre("valor_b");
        setupEjecucionGenerica("PROMEDIO", null, List.of(v1, v2));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("valor_a", 4);
        valores.put("valor_b", 8);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("6.0000")));
        assertEquals("promedio", resultado.tipoCalculo());
    }

    @Test
    void ejecutarMetricaAcademica_regresionDirecto_siguendoFuncionandoIgual() {
        Variable v1 = crearVariableConNombre("valor_directo");
        setupEjecucionGenerica("DIRECTO", null, List.of(v1));

        Map<String, Object> valores = Map.of("valor_directo", 42);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("42")));
        assertEquals("directo", resultado.tipoCalculo());
    }

    @Test
    void ejecutarMetricaAcademica_defectosViaSuma_calculaCorrectamente() {
        // Arrange: Defectos = SUMA(defectos_totales) — decisión de proyecto autorizada.
        Variable v1 = crearVariableConNombre("defectos_totales");
        setupEjecucionGenerica("SUMA", "Σ defectos_totales", List.of(v1));

        Map<String, Object> valores = Map.of("defectos_totales", 3);
        EjecutarMetricaAcademicaRequest request = new EjecutarMetricaAcademicaRequest(
            proyectoId, sprintId, valores
        );

        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);

        assertEquals(0, resultado.resultado().compareTo(new BigDecimal("3")));
        assertEquals("suma", resultado.tipoCalculo());
    }

    // ========================================
    // Tests: Histórico
    // ========================================
    
    @Test
    void obtenerHistorico_retornaListaOrdenadaPorFecha() {
        // Arrange
        ResultadoMetrica r1 = crearResultado(new BigDecimal("5"), Instant.now().minusSeconds(100));
        ResultadoMetrica r2 = crearResultado(new BigDecimal("7"), Instant.now());
        
        when(resultadoRepo.findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(metricaId, proyectoId))
            .thenReturn(List.of(r2, r1));
        
        // Act
        List<ResultadoMetricaDto> historico = service.obtenerHistorico(metricaId, proyectoId);
        
        // Assert
        assertEquals(2, historico.size());
        assertEquals(0, historico.get(0).resultado().compareTo(new BigDecimal("7")));
        assertEquals(0, historico.get(1).resultado().compareTo(new BigDecimal("5")));
    }
    
    // ========================================
    // Tests: Interpretación IA
    // ========================================
    
    @Test
    void solicitarInterpretacionIA_conResultadoValido_retornaInterpretacion() {
        // Arrange
        UUID resultadoId = UUID.randomUUID();
        ResultadoMetrica resultado = crearResultado(new BigDecimal("7"), Instant.now());
        
        when(resultadoRepo.findById(resultadoId)).thenReturn(Optional.of(resultado));
        when(resultadoRepo.findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(any(), any()))
            .thenReturn(List.of(resultado));
        when(parametrizacionRepo.findById(any())).thenReturn(Optional.empty());
        when(geminiService.generate(anyString()))
            .thenReturn("El equipo registró 7 problemas reportados...");
        
        // Act
        InterpretacionIADto interpretacion = service.solicitarInterpretacionIA(resultadoId);
        
        // Assert
        assertNotNull(interpretacion);
        assertEquals(resultadoId, interpretacion.resultadoId());
        assertNotNull(interpretacion.interpretacion());
        assertTrue(interpretacion.interpretacion().contains("7"));
    }
    
    @Test
    void solicitarInterpretacionIA_cuandoGeminiFalla_retornaMensajeError() {
        // Arrange
        UUID resultadoId = UUID.randomUUID();
        ResultadoMetrica resultado = crearResultado(new BigDecimal("7"), Instant.now());
        
        when(resultadoRepo.findById(resultadoId)).thenReturn(Optional.of(resultado));
        when(resultadoRepo.findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(any(), any()))
            .thenReturn(List.of(resultado));
        when(parametrizacionRepo.findById(any())).thenReturn(Optional.empty());
        when(geminiService.generate(anyString())).thenThrow(new RuntimeException("Error"));
        
        // Act
        InterpretacionIADto interpretacion = service.solicitarInterpretacionIA(resultadoId);
        
        // Assert
        assertNotNull(interpretacion);
        assertTrue(interpretacion.interpretacion().contains("No se pudo generar"));
    }
    
    // ========================================
    // Métodos Auxiliares
    // ========================================
    
    private void setupEjecucionExitosa() {
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();
        MetricParametrizacion parametrizacion = crearParametrizacionAprobada();
        Variable variable = crearVariable();
        
        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(any(), anyInt()))
            .thenReturn(List.of(variable));
        when(ejecucionService.guardarOActualizarValor(
                any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new RegistroValor());
        when(resultadoRepo.save(any(ResultadoMetrica.class)))
            .thenAnswer(inv -> {
                ResultadoMetrica r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
    }
    
    private Metrica crearMetrica() {
        Metrica metrica = new Metrica();
        metrica.setId(metricaId);
        metrica.setCodigo("SIG-SC-02");
        metrica.setNombre("Problemas reportados por el cliente");
        return metrica;
    }
    
    private Proyecto crearProyecto() {
        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto Test");
        return proyecto;
    }
    
    private Sprint crearSprint() {
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        sprint.setSprintGoal("Sprint Goal Test");
        return sprint;
    }
    
    private MetricParametrizacion crearParametrizacionAprobada() {
        MetricParametrizacion param = new MetricParametrizacion();
        param.setId(UUID.randomUUID());
        param.setVersion(1);
        param.setMetricaId(metricaId);
        param.setProyectoId(proyectoId);
        param.setStatus("aprobada");
        param.setFuenteAcademica("Guerrero-Calvache & Hernández (2024)");
        param.setFormulaAcademica("Σ problemas_reportados");
        param.setTipoOperacion("SUMA");
        param.setUnidadResultado("problemas");
        return param;
    }
    
    private Variable crearVariable() {
        Variable variable = new Variable();
        variable.setId(UUID.randomUUID());
        variable.setNombre("problemas_reportados");
        variable.setTipoDato("numerico");
        variable.setMetrica(crearMetrica());
        variable.setProyectoId(proyectoId);
        return variable;
    }

    private Variable crearVariableConNombre(String nombre) {
        Variable variable = new Variable();
        variable.setId(UUID.randomUUID());
        variable.setNombre(nombre);
        variable.setTipoDato("numerico");
        variable.setMetrica(crearMetrica());
        variable.setProyectoId(proyectoId);
        return variable;
    }

    /**
     * Arma el mocking común para ejecutar una parametrización FORMULA con N variables.
     */
    private void setupEjecucionFormula(String formulaAcademica, List<Variable> variables) {
        setupEjecucionGenerica("FORMULA", formulaAcademica, variables);
    }

    /**
     * Arma el mocking común para ejecutar cualquier tipoOperacion con N variables,
     * reutilizable por FORMULA, SUMA, PROMEDIO y DIRECTO.
     */
    private void setupEjecucionGenerica(String tipoOperacion, String formulaAcademica, List<Variable> variables) {
        Metrica metrica = crearMetrica();
        Proyecto proyecto = crearProyecto();
        Sprint sprint = crearSprint();

        MetricParametrizacion parametrizacion = new MetricParametrizacion();
        parametrizacion.setId(UUID.randomUUID());
        parametrizacion.setVersion(1);
        parametrizacion.setMetricaId(metricaId);
        parametrizacion.setProyectoId(proyectoId);
        parametrizacion.setStatus("aprobada");
        parametrizacion.setFuenteAcademica("Fuente académica de prueba");
        parametrizacion.setFormulaAcademica(formulaAcademica);
        parametrizacion.setTipoOperacion(tipoOperacion);
        parametrizacion.setUnidadResultado("porcentaje");

        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(metrica));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userEmail))
            .thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
            .thenReturn(Optional.of(parametrizacion));
        when(variableRepo.findByParametrizacionIdAndParametrizacionVersion(any(), anyInt()))
            .thenReturn(variables);
        when(ejecucionService.guardarOActualizarValor(
                any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new RegistroValor());
        when(resultadoRepo.save(any(ResultadoMetrica.class)))
            .thenAnswer(inv -> {
                ResultadoMetrica r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });
    }
    
    private ResultadoMetrica crearResultado(BigDecimal valor, Instant fecha) {
        ResultadoMetrica resultado = new ResultadoMetrica();
        resultado.setId(UUID.randomUUID());
        resultado.setMetrica(crearMetrica());
        resultado.setProyectoId(proyectoId);
        resultado.setSprintId(sprintId);
        resultado.setParametrizacionId(UUID.randomUUID());
        resultado.setParametrizacionVersion(1);
        resultado.setTipoCalculo("suma");
        resultado.setValoresUtilizados("{}");
        resultado.setResultado(valor);
        resultado.setUnidad("problemas");
        resultado.setEstado("calculado");
        resultado.setCalculadoPor(userEmail);
        resultado.setCalculadoAt(fecha);
        return resultado;
    }
}
