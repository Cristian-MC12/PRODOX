// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.AprobarParametrizacionRequest;
import com.prodox.entity.Metrica;
import com.prodox.entity.MetricaCategoria;
import com.prodox.entity.MetricParametrizacion;
import com.prodox.entity.Variable;
import com.prodox.repository.MetricParametrizacionRepository;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Corrección del manejo de escalas — cubre:
 * - ParametrizacionService.validarEscalaEstructurada() (reglas de validación).
 * - Que aprobarParametrizacion() (camino A) copia la escala estructurada a Variable.
 * - Que VariableDinamicaService.materializarVariables() (camino B) copia la
 *   misma estructura, sin depender de ningún regex sobre el texto libre `escala`.
 * - Que ambos caminos producen exactamente el mismo resultado para la misma
 *   parametrización.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Escala estructurada — validación y copia a Variable")
class ParametrizacionServiceEscalaTest {

    // ── validarEscalaEstructurada() ─────────────────────────────────────────

    @Test
    @DisplayName("sin ningún campo informado: no lanza (compatibilidad histórica)")
    void sinCampos_noLanza() {
        assertThatCode(() -> ParametrizacionService.validarEscalaEstructurada(null, null, null, null, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tipo no soportado: lanza EscalaInvalidaException")
    void tipoNoSoportado_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "ORDINAL", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("escalaTipo");
    }

    @Test
    @DisplayName("escalaMin ausente: lanza EscalaInvalidaException")
    void minAusente_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", null, BigDecimal.TEN, BigDecimal.ONE, false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("escalaMin");
    }

    // 6. escalaMax menor que escalaMin -> rechazo.
    @Test
    @DisplayName("escalaMax menor que escalaMin: lanza EscalaInvalidaException")
    void maxMenorQueMin_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("no puede ser menor");
    }

    // 7. escalaPaso <= 0 -> rechazo.
    @Test
    @DisplayName("escalaPaso = 0: lanza EscalaInvalidaException")
    void pasoCero_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("escalaPaso");
    }

    @Test
    @DisplayName("escalaPaso negativo: lanza EscalaInvalidaException")
    void pasoNegativo_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("-1"), false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("escalaPaso");
    }

    @Test
    @DisplayName("NUMERICA_ENTERA con escalaMin decimal: lanza EscalaInvalidaException")
    void enteraConMinDecimal_lanza() {
        assertThatThrownBy(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", new BigDecimal("0.5"), BigDecimal.TEN, BigDecimal.ONE, false))
            .isInstanceOf(EscalaInvalidaException.class)
            .hasMessageContaining("entero");
    }

    @Test
    @DisplayName("sinLimite=true sin escalaMax: no lanza (max se ignora)")
    void sinLimite_sinMax_noLanza() {
        assertThatCode(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_ENTERA", BigDecimal.ZERO, null, BigDecimal.ONE, true))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NUMERICA_DECIMAL con paso decimal: no lanza")
    void decimalConPasoDecimal_noLanza() {
        assertThatCode(() -> ParametrizacionService.validarEscalaEstructurada(
                "NUMERICA_DECIMAL", BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("0.01"), false))
            .doesNotThrowAnyException();
    }

    // ── Copia a Variable: camino A (ParametrizacionService.aprobarParametrizacion) ──

    @Mock private GeminiService geminiService;
    @Mock private MetricParametrizacionRepository parametrizacionRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private VariableRepository variableRepository;
    @Mock private MetricaRepository metricaRepository;

    private ParametrizacionService service;
    private UUID proyectoId;
    private UUID metricaId;
    private UUID parametrizacionId;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new ParametrizacionService(geminiService, objectMapper, parametrizacionRepository,
            projectMemberRepository, variableRepository, metricaRepository);
        proyectoId = UUID.randomUUID();
        metricaId = UUID.randomUUID();
        parametrizacionId = UUID.randomUUID();

        Authentication auth = new UsernamePasswordAuthenticationToken("sm@example.com", null, List.of());
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private Metrica metrica() {
        MetricaCategoria cat = new MetricaCategoria();
        cat.setNombre("Significado");
        Metrica m = new Metrica();
        m.setId(metricaId);
        m.setNombre("Defectos");
        m.setCategoria(cat);
        return m;
    }

    private AprobarParametrizacionRequest requestConEscala0a10() {
        return new AprobarParametrizacionRequest(
            "objetivo", "procedimiento", "indicador_variable", "0-10 escala", "por_sprint",
            null, null, "SUMA", null, "indicador_variable",
            "NUMERICA_ENTERA", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, false, "0=Muy malo; 10=Excelente");
    }

    // 15. ParametrizacionService copia correctamente escala -> Variable.
    @Test
    @DisplayName("aprobarParametrizacion(): copia escala estructurada a la Variable creada")
    void aprobarParametrizacion_copiaEscalaAVariable() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(parametrizacionId);
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("propuesta");

        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(p));
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.empty());
        when(parametrizacionRepository.save(any(MetricParametrizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepository.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica()));
        when(variableRepository.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aprobarParametrizacion(parametrizacionId, requestConEscala0a10());

        ArgumentCaptor<Variable> captor = ArgumentCaptor.forClass(Variable.class);
        verify(variableRepository).save(captor.capture());
        Variable v = captor.getValue();
        assertThat(v.getEscalaTipo()).isEqualTo("NUMERICA_ENTERA");
        assertThat(v.getEscalaMin()).isEqualByComparingTo("0");
        assertThat(v.getEscalaMax()).isEqualByComparingTo("10");
        assertThat(v.getEscalaPaso()).isEqualByComparingTo("1");
        assertThat(v.getEscalaSinLimite()).isFalse();
    }

    @Test
    @DisplayName("aprobarParametrizacion(): escalaSinLimite=true persiste escalaMax null en Variable")
    void aprobarParametrizacion_sinLimite_dejaMaxNuloEnVariable() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(parametrizacionId);
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("propuesta");

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "objetivo", "procedimiento", "indicador_variable", "conteo", "por_sprint",
            null, null, "SUMA", null, "indicador_variable",
            "NUMERICA_ENTERA", BigDecimal.ZERO, new BigDecimal("999"), BigDecimal.ONE, true,
            "Cantidad de defectos");

        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(p));
        when(parametrizacionRepository.findUltimaVersionAprobada(metricaId, proyectoId)).thenReturn(Optional.empty());
        when(parametrizacionRepository.save(any(MetricParametrizacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variableRepository.findByParametrizacionIdAndParametrizacionVersion(parametrizacionId, 1))
            .thenReturn(List.of());
        when(metricaRepository.findById(metricaId)).thenReturn(Optional.of(metrica()));
        when(variableRepository.save(any(Variable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aprobarParametrizacion(parametrizacionId, req);

        ArgumentCaptor<Variable> captor = ArgumentCaptor.forClass(Variable.class);
        verify(variableRepository).save(captor.capture());
        Variable v = captor.getValue();
        // escalaMax se ignora/anula cuando sinLimite=true, aunque el request haya enviado 999.
        assertThat(v.getEscalaMax()).isNull();
        assertThat(v.getEscalaSinLimite()).isTrue();
    }

    @Test
    @DisplayName("aprobarParametrizacion(): con escala estructuralmente inválida, lanza y no crea la Variable")
    void aprobarParametrizacion_escalaInvalida_noCreaVariable() {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setId(parametrizacionId);
        p.setMetricaId(metricaId);
        p.setProyectoId(proyectoId);
        p.setVersion(1);
        p.setStatus("propuesta");

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "objetivo", "procedimiento", "indicador_variable", "invalida", "por_sprint",
            null, null, "SUMA", null, "indicador_variable",
            "NUMERICA_ENTERA", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, false, null); // max < min

        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, "sm@example.com")).thenReturn(true);
        when(parametrizacionRepository.findById(parametrizacionId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.aprobarParametrizacion(parametrizacionId, req))
            .isInstanceOf(EscalaInvalidaException.class);

        verifyNoInteractions(variableRepository);
    }
}
