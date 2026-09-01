// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CrearMetricaIARequest;
import com.prodox.dto.MetricaIACreadaDto;
import com.prodox.dto.MetricaIAPropuestaDto;
import com.prodox.entity.Metrica;
import com.prodox.entity.MetricaCategoria;
import com.prodox.repository.MetricaCategoriaRepository;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FASE 15 — Tests de MetricaIAService.
 *
 * Cubre la lista obligatoria de la autorización de implementación:
 * la propuesta de IA nunca persiste nada, cancelar no persiste nada
 * (equivalente a nunca llamar a crearDesdeConfirmacion), la edición humana
 * persiste exactamente lo editado, el código IA es único y seguro ante
 * concurrencia, la métrica creada queda asociada al proyecto correcto,
 * un proyecto no ve la selección de otro, no se modifica ninguna métrica
 * existente y no se crean variables antes de aprobar la parametrización.
 */
@ExtendWith(MockitoExtension.class)
class MetricaIAServiceTest {

    @Mock private GeminiService geminiService;
    @Mock private MetricaRepository metricaRepository;
    @Mock private MetricaCategoriaRepository metricaCategoriaRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private PlaneacionService planeacionService;

    private MetricaIAService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // MetricaSimilitudService se instancia real (no mock) sobre el mismo mock de
        // metricaRepository: sin stub explícito, findAllByOrderByCategoriaIdAscNombreAsc()
        // devuelve una lista vacía por defecto (Mockito), así que la búsqueda de
        // posibles duplicados conceptuales no encuentra nada salvo que un test la
        // stubbee explícitamente — los tests existentes de esta clase no se ven afectados.
        MetricaSimilitudService metricaSimilitudService = new MetricaSimilitudService(metricaRepository);
        service = new MetricaIAService(
                geminiService, objectMapper, metricaRepository,
                metricaCategoriaRepository, projectMemberRepository, planeacionService,
                metricaSimilitudService
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(String userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    // ── generarPropuesta: nunca persiste nada ──────────────────────────────

    @Test
    void generarPropuesta_conRespuestaValidaDeGemini_devuelveDtoParseado() {
        String json = """
            {
              "nombre": "Estado de ánimo del equipo",
              "descripcion": "Mide el clima emocional del equipo durante el sprint",
              "objetivo": "Detectar señales tempranas de desgaste",
              "queMide": "Percepción subjetiva promedio del equipo",
              "variablesSugeridas": "animo_promedio",
              "tipoOperacionSugerido": "PROMEDIO",
              "formulaSugerida": "PROMEDIO(animo_promedio)",
              "unidadResultado": "escala 1-5",
              "fuenteSugerida": "No determinado"
            }
            """;
        when(geminiService.generate(anyString())).thenReturn(json);

        MetricaIAPropuestaDto propuesta = service.generarPropuesta("Quiero medir el estado de ánimo del equipo");

        assertThat(propuesta.nombre()).isEqualTo("Estado de ánimo del equipo");
        assertThat(propuesta.tipoOperacionSugerido()).isEqualTo("PROMEDIO");
        assertThat(propuesta.fuenteSugerida()).isEqualTo("No determinado");
    }

    // ── FASE 19: un fallo real de Gemini nunca se disfraza de propuesta válida ──
    // Antes, cualquier excepción de geminiService.generate() (o de parsePropuesta)
    // se convertía en fallbackPropuesta(): una MetricaIAPropuestaDto "exitosa" con
    // todos los campos en "No determinado", indistinguible para el Scrum Master
    // de una respuesta legítima de la IA. Ahora se lanza PropuestaIANoDisponibleException,
    // con un mensaje amigable, y el detalle técnico original queda como cause().

    @Test
    void generarPropuesta_cuandoGeminiResponde503_lanzaPropuestaIANoDisponibleException() {
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 503 SERVICE_UNAVAILABLE: {\"error\":{\"message\":\"high demand\"}}"));

        assertThatThrownBy(() -> service.generarPropuesta("Quiero medir el estado de ánimo del equipo"))
                .isInstanceOf(PropuestaIANoDisponibleException.class)
                .hasMessageContaining("no pudo generar una propuesta")
                .hasMessageNotContaining("503")
                .hasMessageNotContaining("SERVICE_UNAVAILABLE");
    }

    @Test
    void generarPropuesta_cuandoGeminiResponde429_lanzaPropuestaIANoDisponibleException() {
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS: quotaValue=20"));

        assertThatThrownBy(() -> service.generarPropuesta("Quiero medir el estado de ánimo del equipo"))
                .isInstanceOf(PropuestaIANoDisponibleException.class)
                .hasMessageContaining("no pudo generar una propuesta");
    }

    @Test
    void generarPropuesta_conErrorInesperadoDeGemini_lanzaPropuestaIANoDisponibleException() {
        // Ej: timeout de red, o JSON no parseable devuelto por parsePropuesta() —
        // cualquier fallo no anticipado debe tratarse igual, nunca como éxito.
        when(geminiService.generate(anyString())).thenReturn("esto no es JSON válido");

        assertThatThrownBy(() -> service.generarPropuesta("necesidad de prueba"))
                .isInstanceOf(PropuestaIANoDisponibleException.class);
    }

    @Test
    void generarPropuesta_siGeminiFalla_noSePersisteNiSeAsociaNada() {
        when(geminiService.generate(anyString())).thenThrow(new RuntimeException("Gemini error 503: caído"));

        assertThatThrownBy(() -> service.generarPropuesta("necesidad de prueba"))
                .isInstanceOf(PropuestaIANoDisponibleException.class);

        // 0 métricas, 0 ProyectoMetrica (planeacionService.seleccionar/aprobar),
        // 0 parametrizaciones: este servicio no depende de MetricParametrizacionRepository,
        // así que la ausencia total de interacción con estos mocks ya demuestra que
        // ni una Metrica ni una selección de proyecto llegan a crearse.
        verifyNoInteractions(metricaRepository, metricaCategoriaRepository,
                projectMemberRepository, planeacionService);
    }

    @Test
    void generarPropuesta_trasUnFalloElUsuarioPuedeReintentarConExito() {
        String jsonValido = """
            {
              "nombre": "Dependencias externas por sprint",
              "descripcion": "Mide cuántas veces el equipo pidió ayuda externa",
              "objetivo": "Detectar bloqueos por dependencias externas",
              "queMide": "Solicitudes de ayuda a otros equipos",
              "variablesSugeridas": "solicitudes_ayuda_externa",
              "tipoOperacionSugerido": "SUMA",
              "formulaSugerida": "SUMA(solicitudes_ayuda_externa)",
              "unidadResultado": "solicitudes",
              "fuenteSugerida": "No determinado"
            }
            """;
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 503: caído"))
                .thenReturn(jsonValido);

        // Primer intento: Gemini caído.
        assertThatThrownBy(() -> service.generarPropuesta("necesidad de prueba"))
                .isInstanceOf(PropuestaIANoDisponibleException.class);

        // Reintento inmediato: Gemini ya respondió — debe funcionar normalmente,
        // sin ningún estado colgado del intento anterior.
        MetricaIAPropuestaDto propuesta = service.generarPropuesta("necesidad de prueba");
        assertThat(propuesta.nombre()).isEqualTo("Dependencias externas por sprint");
    }

    @Test
    void generarPropuesta_nuncaPersisteNadaNiCreaProyectoMetrica() {
        when(geminiService.generate(anyString())).thenReturn("{\"nombre\":\"X\"}");

        service.generarPropuesta("necesidad de prueba");

        verifyNoInteractions(metricaRepository, metricaCategoriaRepository,
                projectMemberRepository, planeacionService);
    }

    // ── crearDesdeConfirmacion ──────────────────────────────────────────────

    private CrearMetricaIARequest request(UUID proyectoId, String nombre, String descripcion) {
        return new CrearMetricaIARequest(proyectoId, (short) 4, nombre, descripcion, null, null, null, null);
    }

    private MetricaCategoria categoriaSociohumano() {
        MetricaCategoria cat = new MetricaCategoria();
        cat.setId((short) 4);
        cat.setNombre("Socio-Humano FSH");
        return cat;
    }

    @Test
    void crearDesdeConfirmacion_usuarioNoMiembroDelProyecto_lanzaExcepcionYNoPersisteNada() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("intruso@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "intruso@example.com"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(
                request(proyectoId, "Estado de ánimo", "descripción")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(metricaCategoriaRepository, metricaRepository, planeacionService);
    }

    @Test
    void crearDesdeConfirmacion_persisteExactamenteLosDatosEditadosPorElScrumMaster() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(
                request(proyectoId, "Nombre editado por el SM", "Descripción editada por el SM"));

        ArgumentCaptor<Metrica> captor = ArgumentCaptor.forClass(Metrica.class);
        verify(metricaRepository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Nombre editado por el SM");
        assertThat(captor.getValue().getDescripcion()).isEqualTo("Descripción editada por el SM");
        assertThat(resultado.nombre()).isEqualTo("Nombre editado por el SM");
        assertThat(resultado.proyectoId()).isEqualTo(proyectoId);
    }

    @Test
    void crearDesdeConfirmacion_generaCodigoConPrefijoIA() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(7L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(
                request(proyectoId, "Nombre", "Descripción"));

        assertThat(resultado.codigo()).isEqualTo("IA-007");
    }

    @Test
    void crearDesdeConfirmacion_siCodigoYaExiste_reintentaConSiguienteValor() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L, 2L);
        when(metricaRepository.existsByCodigo("IA-001")).thenReturn(true);
        when(metricaRepository.existsByCodigo("IA-002")).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(
                request(proyectoId, "Nombre", "Descripción"));

        assertThat(resultado.codigo()).isEqualTo("IA-002");
        verify(metricaRepository, times(2)).siguienteValorSecuenciaCodigoIA();
    }

    @Test
    void crearDesdeConfirmacion_siSaveFallaPorColisionConcurrente_reintentaConNuevoCodigo() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L, 2L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class)))
                .thenThrow(new DataIntegrityViolationException("codigo duplicado (colisión concurrente)"))
                .thenAnswer(inv -> {
                    Metrica m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(
                request(proyectoId, "Nombre", "Descripción"));

        assertThat(resultado.codigo()).isEqualTo("IA-002");
        verify(metricaRepository, times(2)).save(any(Metrica.class));
    }

    @Test
    void crearDesdeConfirmacion_asociaLaMetricaAlProyectoMedianteFlujoExistente() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        UUID metricaIdGenerada = UUID.randomUUID();
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(metricaIdGenerada);
            return m;
        });

        service.crearDesdeConfirmacion(request(proyectoId, "Nombre", "Descripción"));

        verify(planeacionService).seleccionar(proyectoId, metricaIdGenerada);
        // No se aprueba ni se generan variables: eso solo ocurre a través del
        // flujo existente de parametrización → verificación → aprobación.
        verifyNoMoreInteractions(planeacionService);
    }

    @Test
    void crearDesdeConfirmacion_aislamientoEntreProyectos_noValidaMembresiaDeOtroProyecto() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        // El usuario es miembro de A pero NO de B.
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoA, "sm@example.com")).thenReturn(true);
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoB, "sm@example.com")).thenReturn(false);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Proyecto A: permitido.
        service.crearDesdeConfirmacion(request(proyectoA, "Nombre A", "Descripción A"));
        verify(planeacionService).seleccionar(eq(proyectoA), any());

        // Proyecto B: rechazado, la métrica de A nunca se selecciona para B.
        assertThatThrownBy(() -> service.crearDesdeConfirmacion(request(proyectoB, "Nombre B", "Descripción B")))
                .isInstanceOf(IllegalStateException.class);
        verify(planeacionService, never()).seleccionar(eq(proyectoB), any());
    }

    @Test
    void crearDesdeConfirmacion_categoriaInexistente_lanzaExcepcionYNoGuardaNada() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(
                new CrearMetricaIARequest(proyectoId, (short) 99, "Nombre", "Descripción", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(metricaRepository, planeacionService);
    }

    // ── Metrica = catálogo GLOBAL (corrección definitiva, revierte V30) ────
    // Ya no existe Metrica.proyectoId: el chequeo de duplicado es contra TODO
    // el catálogo, y si ya existe no se crea una fila nueva — se informa cuál
    // es la existente para que el frontend ofrezca reutilizarla.

    private Metrica metricaExistenteEnCatalogo(String nombre) {
        Metrica m = new Metrica();
        m.setId(UUID.randomUUID());
        m.setCategoria(categoriaSociohumano());
        m.setCodigo("SIG-XX-01");
        m.setNombre(nombre);
        m.setDescripcion("Descripción existente");
        return m;
    }

    // Caso: no existe todavía en el catálogo → se crea y se asocia al proyecto.
    @Test
    void crearDesdeConfirmacion_nombreNoExisteEnElCatalogo_creaYAsociaAlProyecto() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("Velocidad")).thenReturn(Optional.empty());
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(request(proyectoId, "Velocidad", "desc"));

        assertThat(resultado.nombre()).isEqualTo("Velocidad");
        verify(metricaRepository).save(any(Metrica.class));
        verify(planeacionService).seleccionar(eq(proyectoId), any());
    }

    // Caso 5 (obligatorio): crear una métrica IA que YA EXISTE en el catálogo
    // global NO crea otra fila — lanza MetricaDuplicadaEnCatalogoException con
    // la métrica existente, y nunca llama a save() ni a seleccionar().
    @Test
    void crearDesdeConfirmacion_caso5_nombreYaExisteEnElCatalogoGlobal_noCreaOtraFila() {
        UUID proyectoId = UUID.randomUUID();
        Metrica existente = metricaExistenteEnCatalogo("Velocidad");
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("velocidad")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(request(proyectoId, "velocidad", "desc")))
                .isInstanceOf(MetricaDuplicadaEnCatalogoException.class)
                .hasMessageContaining("Ya existe una métrica en el catálogo");

        verify(metricaRepository, never()).save(any(Metrica.class));
        verifyNoInteractions(planeacionService);
    }

    // La excepción de duplicado transporta la métrica existente completa
    // (id/codigo/nombre/descripcion/categoria) para que el frontend pueda
    // ofrecer "usar esta" sin otra consulta.
    @Test
    void crearDesdeConfirmacion_nombreDuplicado_laExcepcionTransportaLaMetricaExistente() {
        UUID proyectoId = UUID.randomUUID();
        Metrica existente = metricaExistenteEnCatalogo("Estado de ánimo");
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed(" Estado de ánimo ")).thenReturn(Optional.of(existente));

        try {
            service.crearDesdeConfirmacion(request(proyectoId, " Estado de ánimo ", "desc"));
            org.assertj.core.api.Assertions.fail("Debía lanzar MetricaDuplicadaEnCatalogoException");
        } catch (MetricaDuplicadaEnCatalogoException e) {
            assertThat(e.getMetricaExistente().id()).isEqualTo(existente.getId());
            assertThat(e.getMetricaExistente().nombre()).isEqualTo("Estado de ánimo");
            assertThat(e.getMetricaExistente().codigo()).isEqualTo("SIG-XX-01");
        }
    }

    // Condición de carrera real (regla obligatoria #8 de la tarea): si el
    // chequeo previo no detectó nada (dos proyectos confirmando en paralelo)
    // pero el INSERT viola el índice único de nombre, se trata igual que un
    // duplicado detectado a tiempo — nunca se reintenta con otro código, y se
    // informa la fila que ganó la carrera.
    @Test
    void crearDesdeConfirmacion_condicionDeCarreraEnElNombre_seDetectaYNoCreaDuplicado() {
        UUID proyectoId = UUID.randomUUID();
        Metrica ganadora = metricaExistenteEnCatalogo("Velocidad");
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        // El chequeo previo no encuentra nada (todavía no existía en ese instante)...
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("Velocidad"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganadora)); // ...pero para cuando se guarda, ya existe.
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        org.springframework.dao.DataIntegrityViolationException violacionDeNombre =
                new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"ux_metricas_nombre_global\"");
        when(metricaRepository.save(any(Metrica.class))).thenThrow(violacionDeNombre);

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(request(proyectoId, "Velocidad", "desc")))
                .isInstanceOf(MetricaDuplicadaEnCatalogoException.class);

        verify(metricaRepository, times(1)).save(any(Metrica.class)); // nunca reintenta con otro código
        verifyNoInteractions(planeacionService);
    }

    // ── FASE 23: posibles duplicados CONCEPTUALES (no nombre exacto) ───────
    // Alternativa a ocultar métricas históricas con un flag: en vez de eso, se
    // avisa ANTES de crear una nueva variante del mismo concepto. Nunca
    // reemplaza el chequeo de nombre exacto de arriba — es una capa adicional
    // que solo entra en juego cuando el nombre NO coincide exactamente.

    private Metrica metricaEstadoDeAnimo() {
        Metrica m = new Metrica();
        m.setId(UUID.randomUUID());
        m.setCategoria(categoriaSociohumano());
        m.setCodigo("IA-007");
        m.setNombre("Estado de ánimo del equipo");
        m.setDescripcion("Refleja la percepción colectiva del bienestar y satisfacción de los miembros del equipo.");
        return m;
    }

    @Test
    void crearDesdeConfirmacion_conceptoSimilarSinNombreExacto_lanzaPosibleDuplicadaYNoCreaNada() {
        UUID proyectoId = UUID.randomUUID();
        Metrica existente = metricaEstadoDeAnimo();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("Clima emocional del equipo")).thenReturn(Optional.empty());
        when(metricaRepository.findAllByOrderByCategoriaIdAscNombreAsc()).thenReturn(List.of(existente));

        CrearMetricaIARequest request = new CrearMetricaIARequest(
                proyectoId, (short) 4, "Clima emocional del equipo",
                "Mide el bienestar y la satisfacción del equipo respecto a su clima emocional.",
                null, null, null, null);

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(request))
                .isInstanceOf(MetricaPosibleDuplicadaException.class)
                .satisfies(e -> {
                    List<com.prodox.dto.PosibleDuplicadoDto> candidatos =
                            ((MetricaPosibleDuplicadaException) e).getCandidatos();
                    assertThat(candidatos).isNotEmpty();
                    assertThat(candidatos.get(0).metrica().id()).isEqualTo(existente.getId());
                    assertThat(candidatos.get(0).razones()).isNotEmpty();
                });

        // No se crea otra fila, no se asocia al proyecto, y la métrica existente
        // nunca se toca: solo se lee para comparar, jamás se guarda ni se borra.
        verify(metricaRepository, never()).save(any(Metrica.class));
        verify(metricaRepository, never()).delete(any());
        verifyNoInteractions(planeacionService);
    }

    @Test
    void crearDesdeConfirmacion_confirmarCreacionDiferenteTrue_omiteAvisoYCreaNormalmente() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("Clima emocional del equipo")).thenReturn(Optional.empty());
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // El Scrum Master ya vio el aviso y decidió explícitamente crearla como
        // distinta: no se debe ni siquiera consultar el catálogo para similitud.
        CrearMetricaIARequest request = new CrearMetricaIARequest(
                proyectoId, (short) 4, "Clima emocional del equipo",
                "Mide el bienestar y la satisfacción del equipo respecto a su clima emocional.",
                null, null, null, true);

        MetricaIACreadaDto resultado = service.crearDesdeConfirmacion(request);

        assertThat(resultado.nombre()).isEqualTo("Clima emocional del equipo");
        verify(metricaRepository, never()).findAllByOrderByCategoriaIdAscNombreAsc();
        verify(metricaRepository).save(any(Metrica.class));
        verify(planeacionService).seleccionar(eq(proyectoId), any());
    }

    @Test
    void crearDesdeConfirmacion_nombreExactoTienePrioridadSobreElChequeoConceptual() {
        UUID proyectoId = UUID.randomUUID();
        Metrica existente = metricaExistenteEnCatalogo("Velocidad");
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.findByNombreIgnoreCaseTrimmed("velocidad")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.crearDesdeConfirmacion(request(proyectoId, "velocidad", "desc")))
                .isInstanceOf(MetricaDuplicadaEnCatalogoException.class);

        // El chequeo de nombre exacto corta el flujo antes de siquiera consultar
        // el catálogo completo para similitud conceptual.
        verify(metricaRepository, never()).findAllByOrderByCategoriaIdAscNombreAsc();
    }

    @Test
    void crearDesdeConfirmacion_soloCreaUnaFilaMetricaNueva_nuncaModificaUnaExistente() {
        UUID proyectoId = UUID.randomUUID();
        mockAuthentication("sm@example.com");
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(metricaCategoriaRepository.findById((short) 4)).thenReturn(Optional.of(categoriaSociohumano()));
        when(metricaRepository.siguienteValorSecuenciaCodigoIA()).thenReturn(1L);
        when(metricaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(metricaRepository.save(any(Metrica.class))).thenAnswer(inv -> {
            Metrica m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        service.crearDesdeConfirmacion(request(proyectoId, "Nombre", "Descripción"));

        // Nunca se busca ni se actualiza una Metrica existente: solo se crea una nueva.
        verify(metricaRepository, never()).findById(any());
        verify(metricaRepository, times(1)).save(any(Metrica.class));
    }
}
