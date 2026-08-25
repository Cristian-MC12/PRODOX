package com.mpdia.validation;

import com.mpdia.dto.AprobarParametrizacionRequest;
import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.GuardarPropuestaRequest;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.ProjectMember;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.service.MetricRankingService;
import com.mpdia.service.ParametrizacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FASE 16.10 — Validación de V25 (ux_parametrizacion_proyecto_metrica_version).
 *
 * Usa datos reales existentes (SIG-VEL-01, Prueba 1, Trabajo 1) para no
 * necesitar fixtures nuevas, pero NUNCA toca la métrica/proyecto/versiones
 * reales del piloto SIG-SC-02. Toda la clase corre dentro de una
 * transacción que se revierte automáticamente al final de cada test
 * (@Transactional de Spring Test), así que no persiste ningún dato.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParametrizacionUnicidadV25Test {

    // Prueba 1
    private static final UUID PROYECTO_1 = UUID.fromString("5eaa3d8b-979b-4fc7-861f-d6b6e0bfdd26");
    // Trabajo 1
    private static final UUID PROYECTO_2 = UUID.fromString("fce0340c-74f2-4219-a727-5bae4d842496");
    // SIG-VEL-01 (Velocidad) — distinta de SIG-SC-02, para no tocar los datos del piloto
    private static final UUID METRICA_VEL = UUID.fromString("d0006325-a144-489f-b09c-e51b3e87dfa1");

    @Autowired
    private MetricParametrizacionRepository parametrizacionRepo;

    @Autowired
    private MetricRankingService rankingService;

    @Autowired
    private ParametrizacionService parametrizacionService;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private MetricParametrizacion nuevaFila(UUID proyectoId, UUID metricaId, Integer version, String userId) {
        MetricParametrizacion p = new MetricParametrizacion();
        p.setProyectoId(proyectoId);
        p.setMetricaId(metricaId);
        p.setVersion(version);
        p.setUserId(userId);
        p.setUserEmail(userId + "@test.mpdia.com");
        p.setObjetivo("Objetivo de prueba V25");
        p.setProcedimiento("Procedimiento de prueba V25");
        p.setIndicadorVariable("indicador_prueba_v25");
        p.setEscala("Numérica");
        p.setStatus("propuesta");
        p.setCreatedAt(Instant.now());
        return p;
    }

    @Test
    void A_mismoUsuarioMismoProyecto_dosVersionesCoexisten() {
        String userId = "user-a-" + UUID.randomUUID();
        parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_1, METRICA_VEL, 900, userId));
        parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_1, METRICA_VEL, 901, userId));

        long count = parametrizacionRepo.findHistorialVersiones(METRICA_VEL, PROYECTO_1).stream()
            .filter(p -> p.getVersion() == 900 || p.getVersion() == 901)
            .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void B_mismoUsuarioMismaMetrica_proyectoDiferente_v1Independiente() {
        String userId = "user-b-" + UUID.randomUUID();
        // Misma version (900) en dos proyectos distintos: no debe colisionar,
        // porque la clave incluye proyecto_id.
        MetricParametrizacion enProyecto1 = parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_1, METRICA_VEL, 900, userId));
        MetricParametrizacion enProyecto2 = parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_2, METRICA_VEL, 900, userId));

        assertThat(enProyecto1.getId()).isNotEqualTo(enProyecto2.getId());
        assertThat(parametrizacionRepo.findByMetricaIdAndProyectoIdAndVersion(METRICA_VEL, PROYECTO_1, 900)).isPresent();
        assertThat(parametrizacionRepo.findByMetricaIdAndProyectoIdAndVersion(METRICA_VEL, PROYECTO_2, 900)).isPresent();
    }

    @Test
    void C_mismaCombinacionProyectoMetricaVersion_rechazaDuplicado() {
        String userId1 = "user-c1-" + UUID.randomUUID();
        String userId2 = "user-c2-" + UUID.randomUUID();
        parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_1, METRICA_VEL, 902, userId1));

        // Un segundo usuario intentando insertar la MISMA (proyecto, metrica, version)
        // debe ser rechazado por el índice único nuevo.
        assertThatThrownBy(() ->
            parametrizacionRepo.saveAndFlush(nuevaFila(PROYECTO_1, METRICA_VEL, 902, userId2))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void D_filaConProyectoIdNull_sigueSiendoConsultable() {
        MetricParametrizacion sinProyecto = nuevaFila(null, METRICA_VEL, 1, "user-d-" + UUID.randomUUID());
        MetricParametrizacion guardada = parametrizacionRepo.saveAndFlush(sinProyecto);

        assertThat(parametrizacionRepo.findById(guardada.getId())).isPresent();
        assertThat(parametrizacionRepo.findById(guardada.getId()).get().getProyectoId()).isNull();
    }

    @Test
    void E_ranking_sigueFuncionandoSinIndiceViejo() {
        String userId = "user-e-" + UUID.randomUUID();
        GuardarParametrizacionRequest req = new GuardarParametrizacionRequest(
            null, // factorId
            "Objetivo ranking prueba V25",
            "Procedimiento ranking prueba V25",
            "indicador_ranking_v25",
            "Numérica",
            null, // metricaBaseId
            PROYECTO_1,
            METRICA_VEL,
            "SUMA", "SUMA(indicador_ranking_v25)", "unidades", "fuente test", null
        );

        var dto = rankingService.guardar(req, userId, userId + "@test.mpdia.com");
        assertThat(dto).isNotNull();
        assertThat(dto.status()).isEqualTo("pendiente");

        // FASE 10: guardar() ya NO reutiliza/actualiza la fila existente del mismo
        // usuario+métrica — ese patrón "find-or-update" era exactamente el bug crítico
        // confirmado en FASE 9 (bloque 1): degradaba una parametrización ya aprobada
        // de vuelta a "pendiente" al reenviar, y podía pisar una fila de otro proyecto.
        // Un envío con contenido REALMENTE distinto crea una versión NUEVA para
        // metricaId+proyectoId, igual que ParametrizacionService.guardarPropuesta()
        // (índice único ux_parametrizacion_proyecto_metrica_version sigue protegiendo
        // la unicidad por versión, no por "una fila por usuario").
        //
        // FASE 20: reenviar el MISMO req (contenido idéntico) ya NO crea una segunda
        // fila — ese era exactamente el defecto de "envío duplicado al Scrum Master"
        // (doble clic / recarga durante un envío en curso). Por eso este test usa un
        // segundo request con un objetivo distinto: sigue verificando que múltiples
        // versiones legítimas conviven correctamente para efectos del ranking, sin
        // depender del comportamiento ya corregido.
        GuardarParametrizacionRequest req2 = new GuardarParametrizacionRequest(
            null, "Objetivo ranking prueba V25 (segunda versión, editado)",
            req.procedimiento(), req.indicadorVariable(), req.escala(), null,
            req.proyectoId(), req.metricaId(),
            req.tipoOperacion(), req.formulaAcademica(), req.unidadResultado(), req.fuenteAcademica(),
            req.frecuenciaCaptura()
        );
        var dto2 = rankingService.guardar(req2, userId, userId + "@test.mpdia.com");
        assertThat(dto2.id()).isNotEqualTo(dto.id());
        assertThat(dto2.version()).isEqualTo(dto.version() + 1);

        var top3 = rankingService.getTop3ByMetricaId(METRICA_VEL);
        assertThat(top3).isNotNull(); // no debe lanzar excepción
    }

    // ========================================
    // FASE 16.10-F: guardarPropuesta() no debe colisionar con una propuesta
    // huérfana existente (caso real encontrado en el E2E de SIG-VEL-02).
    // ========================================

    @Test
    void F_guardarPropuesta_conPropuestaHuerfanaExistente_calculaVersionSinColisionar() {
        // Reproduce contra Postgres real el estado exacto encontrado en producción:
        // v1 inactiva, v2 aprobada, v3 propuesta (huérfana, nunca aprobada).
        String userEmail = "user-f-" + UUID.randomUUID() + "@test.mpdia.com";

        ProjectMember member = new ProjectMember();
        member.setProyectoId(PROYECTO_1);
        member.setUserId(userEmail); // ParametrizacionService usa Authentication.getName() como userId
        member.setUserEmail(userEmail);
        member.setRol("scrum_master");
        projectMemberRepository.saveAndFlush(member);

        MetricParametrizacion v1 = nuevaFila(PROYECTO_1, METRICA_VEL, 970, userEmail);
        v1.setStatus("inactiva");
        parametrizacionRepo.saveAndFlush(v1);

        MetricParametrizacion v2 = nuevaFila(PROYECTO_1, METRICA_VEL, 971, userEmail);
        v2.setStatus("aprobada");
        parametrizacionRepo.saveAndFlush(v2);

        MetricParametrizacion v3 = nuevaFila(PROYECTO_1, METRICA_VEL, 972, userEmail);
        v3.setStatus("propuesta"); // huérfana — como la v3 real de SIG-VEL-02
        parametrizacionRepo.saveAndFlush(v3);

        Authentication auth = new UsernamePasswordAuthenticationToken(userEmail, null, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(ctx);

        GuardarPropuestaRequest req = new GuardarPropuestaRequest(
            METRICA_VEL, PROYECTO_1,
            "Objetivo v4", "Procedimiento v4", "Indicador v4", "Escala v4", "por_sprint",
            "Fuente v4", "Σ x", "SUMA", "unidad v4", "{}", "nombre_v4"
        );

        // Antes de esta corrección, esto lanzaba DataIntegrityViolationException
        // por colisionar con (proyecto_id, metrica_id, version)=(..., 972) al
        // intentar reutilizar version=3 relativo a la última APROBADA (v2).
        MetricParametrizacion resultado = parametrizacionService.guardarPropuesta(req);

        assertThat(resultado.getVersion()).isEqualTo(973);
        assertThat(resultado.getStatus()).isEqualTo("propuesta");
        assertThat(resultado.getNombreVariable()).isEqualTo("nombre_v4");

        // v3 no fue modificada ni reutilizada.
        MetricParametrizacion v3Recargada = parametrizacionRepo.findById(v3.getId()).orElseThrow();
        assertThat(v3Recargada.getVersion()).isEqualTo(972);
        assertThat(v3Recargada.getStatus()).isEqualTo("propuesta");
    }

    // ========================================
    // FASE 16.10-G: aprobarParametrizacion() debe desactivar la última versión
    // con status='aprobada' (no version-1) — caso real encontrado en el E2E de
    // SIG-VEL-02 al aprobar v4 con v3 (huérfana) como version-1.
    // ========================================

    @Test
    void G_aprobarParametrizacion_conPropuestaHuerfanaIntermedia_desactivaUltimaAprobadaReal() {
        // Reproduce contra Postgres real el estado exacto encontrado en producción:
        // v1 inactiva, v2 aprobada, v3 propuesta (huérfana), v4 propuesta a aprobar.
        String userEmail = "user-g-" + UUID.randomUUID() + "@test.mpdia.com";

        ProjectMember member = new ProjectMember();
        member.setProyectoId(PROYECTO_1);
        member.setUserId(userEmail);
        member.setUserEmail(userEmail);
        member.setRol("scrum_master");
        projectMemberRepository.saveAndFlush(member);

        MetricParametrizacion v1 = nuevaFila(PROYECTO_1, METRICA_VEL, 980, userEmail);
        v1.setStatus("inactiva");
        parametrizacionRepo.saveAndFlush(v1);

        MetricParametrizacion v2 = nuevaFila(PROYECTO_1, METRICA_VEL, 981, userEmail);
        v2.setStatus("aprobada");
        parametrizacionRepo.saveAndFlush(v2);

        MetricParametrizacion v3 = nuevaFila(PROYECTO_1, METRICA_VEL, 982, userEmail);
        v3.setStatus("propuesta"); // huérfana — version-1 de v4, nunca aprobada
        parametrizacionRepo.saveAndFlush(v3);

        MetricParametrizacion v4 = nuevaFila(PROYECTO_1, METRICA_VEL, 983, userEmail);
        v4.setStatus("propuesta");
        parametrizacionRepo.saveAndFlush(v4);

        Authentication auth = new UsernamePasswordAuthenticationToken(userEmail, null, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(ctx);

        AprobarParametrizacionRequest req = new AprobarParametrizacionRequest(
            "Objetivo v4", "Procedimiento v4", "Indicador v4", "Escala v4", "por_sprint",
            "Fuente v4", "Σ x", "SUMA", "unidad v4", "nombre_v4_g"
        );

        // Antes de esta corrección, aprobarParametrizacion() buscaba version-1
        // (v3, status='propuesta') en vez de la última aprobada real (v2), y
        // dejaba a v2 sin desactivar.
        MetricParametrizacion resultado = parametrizacionService.aprobarParametrizacion(v4.getId(), req);

        assertThat(resultado.getVersion()).isEqualTo(983);
        assertThat(resultado.getStatus()).isEqualTo("aprobada");

        MetricParametrizacion v1Recargada = parametrizacionRepo.findById(v1.getId()).orElseThrow();
        MetricParametrizacion v2Recargada = parametrizacionRepo.findById(v2.getId()).orElseThrow();
        MetricParametrizacion v3Recargada = parametrizacionRepo.findById(v3.getId()).orElseThrow();

        assertThat(v1Recargada.getStatus()).isEqualTo("inactiva"); // sin cambios
        assertThat(v2Recargada.getStatus()).isEqualTo("inactiva"); // última aprobada real, ahora desactivada
        assertThat(v3Recargada.getStatus()).isEqualTo("propuesta"); // NO tocada
        assertThat(v3Recargada.getVersion()).isEqualTo(982);
    }
}
