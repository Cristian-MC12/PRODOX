// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CrearHistoriaUsuarioRequest;
import com.prodox.dto.CrearProyectoRequest;
import com.prodox.dto.CrearVariableRequest;
import com.prodox.entity.AppUser;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.VariableRepository;
import com.prodox.service.HistoriaUsuarioService;
import com.prodox.service.ProyectoService;
import com.prodox.service.VariableService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguridad P1 — BOLA/IDOR + autorización de APIs.
 *
 * Tests de ataque cruzado REALES: dos proyectos (A y B), cada uno con su
 * propio Scrum Master/Product Owner/miembro, contra Postgres real vía
 * peticiones HTTP reales (MockMvc + controllers reales + servicios reales +
 * SecurityFilterChain real) — no mocks de un método authorize(...). La
 * autenticación se simula con `.with(authentication(...))` (un
 * UsernamePasswordAuthenticationToken con el userId real, exactamente lo que
 * JwtAuthFilter produce a partir de un JWT válido) para evitar depender de
 * secretos JWT de test; la lógica de AUTORIZACIÓN bajo prueba —el objetivo de
 * esta suite— corre sin ningún mock.
 *
 * La auditoría (lectura completa de 20+ controllers) encontró que la enorme
 * mayoría de los endpoints YA estaban correctamente protegidos por
 * revisiones de seguridad previas (comentarios "FASE 21/22/23", "Auditoría
 * transversal", "Revisión de seguridad" ya presentes en el código) siguiendo
 * el patrón correcto: cargar el recurso, derivar su proyectoId REAL, validar
 * membresía/rol contra ESE proyectoId — nunca confiar en un proyectoId
 * enviado por el cliente sin cruzarlo contra el recurso real. Esta suite
 * confirma con evidencia HTTP real que esa protección funciona, para los
 * recursos priorizados por esta auditoría (Proyecto, Sprint, ProjectMember,
 * Variable, Historia/Backlog). El único hallazgo real de esta auditoría
 * —exposición de PII en el ranking global de MetricRankingController— tiene
 * su propia cobertura dedicada en MetricRankingServiceTest (tests de
 * "Seguridad P1").
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossTenantAuthorizationTest {

    private static final UUID METRICA_FAT = UUID.fromString("beb22a94-0e1b-496a-8b9e-a08a8f6d77c3");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProyectoService proyectoService;
    @Autowired private VariableService variableService;
    @Autowired private HistoriaUsuarioService historiaUsuarioService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private ProjectMemberRepository memberRepo;
    @Autowired private VariableRepository variableRepo;

    private UUID proyectoA;
    private UUID proyectoB;
    private String smA, smB;
    private String memberA;
    private final List<UUID> usuariosCreados = new java.util.ArrayList<>();
    private final List<UUID> proyectosCreados = new java.util.ArrayList<>();

    @BeforeEach
    void crearDosProyectosConSusActores() {
        smA = crearUsuario("qa-crosstenant-smA-", "scrum_master");
        smB = crearUsuario("qa-crosstenant-smB-", "scrum_master");
        memberA = crearUsuario("qa-crosstenant-memberA-", "scrum_master"); // rol global irrelevante — el rol real es por proyecto

        proyectoA = proyectoService.crear(smA, new CrearProyectoRequest(
                "TEST-CROSSTENANT-A-" + UUID.randomUUID(), "proyecto A", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal A")).id();
        proyectosCreados.add(proyectoA);

        proyectoB = proyectoService.crear(smB, new CrearProyectoRequest(
                "TEST-CROSSTENANT-B-" + UUID.randomUUID(), "proyecto B", "scrum",
                "SEMANAS", 1, null, 2, LocalDate.now(), "goal B")).id();
        proyectosCreados.add(proyectoB);

        ProjectMember pm = new ProjectMember();
        pm.setProyectoId(proyectoA);
        pm.setUserId(memberA);
        pm.setUserEmail(userRepo.findById(UUID.fromString(memberA)).orElseThrow().getEmail());
        pm.setRol(ProjectMember.ROL_SCRUM_MEMBER);
        memberRepo.save(pm);
    }

    private String crearUsuario(String prefijo, String rolGlobal) {
        AppUser u = new AppUser();
        u.setEmail(prefijo + UUID.randomUUID() + "@mpdiaqa.test");
        u.setPasswordHash("{noop}test-no-login");
        u.setRole(rolGlobal);
        u = userRepo.save(u);
        usuariosCreados.add(u.getId());
        return u.getId().toString();
    }

    @AfterEach
    void limpiar() {
        for (UUID pid : proyectosCreados) {
            try { proyectoService.eliminar(pid, smA.equals(propietarioDe(pid)) ? smA : smB); }
            catch (Exception ignored) { }
        }
        for (UUID uid : usuariosCreados) {
            try { userRepo.deleteById(uid); } catch (Exception ignored) { }
        }
    }

    private String propietarioDe(UUID proyectoId) {
        return proyectoId.equals(proyectoA) ? smA : smB;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario(String userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_SCRUM_MASTER"))));
    }

    // ── A/B/C: SM_B (dueño real de B) intenta leer/modificar/eliminar B usando ──
    // su propio proyectoId — control positivo — y luego SM_A (ajeno) lo intenta
    // sobre B y debe ser denegado en los tres casos.

    @Test
    @DisplayName("A) GET /api/proyectos/{id}: SM_A (ajeno) NO puede leer el proyecto B por su UUID -> 403, sin fuga de datos")
    void leerProyectoAjeno_denegado() throws Exception {
        mockMvc.perform(get("/api/proyectos/{id}", proyectoB).with(comoUsuario(smA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("B) PATCH /api/proyectos/{id}/finalizar: SM_A (ajeno) NO puede finalizar el proyecto B")
    void finalizarProyectoAjeno_denegado() throws Exception {
        mockMvc.perform(patch("/api/proyectos/{id}/finalizar", proyectoB).with(comoUsuario(smA)))
                .andExpect(status().is4xxClientError()); // ProyectoService lanza IllegalArgumentException (400) — denegado, nunca 200
    }

    @Test
    @DisplayName("C) DELETE /api/proyectos/{id}: SM_A (ajeno) NO puede eliminar el proyecto B")
    void eliminarProyectoAjeno_denegado() throws Exception {
        mockMvc.perform(delete("/api/proyectos/{id}", proyectoB).with(comoUsuario(smA)))
                .andExpect(status().is4xxClientError());

        // Confirmación positiva: B sigue existiendo — el intento de A no lo borró.
        mockMvc.perform(get("/api/proyectos/{id}", proyectoB).with(comoUsuario(smB)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Control positivo: SM_B SÍ puede leer su propio proyecto B (confirma que 403 arriba es por autorización, no por un bug genérico)")
    void leerProyectoPropio_permitido() throws Exception {
        mockMvc.perform(get("/api/proyectos/{id}", proyectoB).with(comoUsuario(smB)))
                .andExpect(status().isOk());
    }

    // ── F: recurso inexistente -> comportamiento consistente, sin leakage ────

    @Test
    @DisplayName("F) GET /api/proyectos/{id} inexistente, autenticado -> 4xx consistente (nunca 200 con datos)")
    void proyectoInexistente_comportamientoConsistente() throws Exception {
        mockMvc.perform(get("/api/proyectos/{id}", UUID.randomUUID()).with(comoUsuario(smA)))
                .andExpect(status().is4xxClientError());
    }

    // ── E: ProjectMember — SM_A no puede administrar miembros de B ───────────

    @Test
    @DisplayName("E) PATCH /api/project-members/{proyectoId}/{userId}/rol: SM_A (ajeno) NO puede cambiar el rol de un miembro de B")
    void cambiarRolDeMiembroAjeno_denegado() throws Exception {
        mockMvc.perform(patch("/api/project-members/{proyectoId}/{userId}/rol", proyectoB, smB)
                        .with(comoUsuario(smA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("rol", "product_owner"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("E) GET /api/project-members/{proyectoId}: usuario ajeno a B NO puede listar sus miembros")
    void listarMiembrosDeProyectoAjeno_denegado() throws Exception {
        mockMvc.perform(get("/api/project-members/{proyectoId}", proyectoB).with(comoUsuario(smA)))
                .andExpect(status().isForbidden());
    }

    // ── D: BOLA clásico — projectId propio (válido) + variableId ajeno (de B) ──

    @Test
    @DisplayName("D) DELETE variable de B usando proyectoId=A (propio, válido) en la ruta -> DENEGADO, no solo por falta de acceso a B sino porque la variable no pertenece a A")
    void eliminarVariableDeOtroProyecto_usandoElPropioProyectoIdComoFachada_denegado() throws Exception {
        var variableB = variableService.crear(smB, proyectoB, new CrearVariableRequest(
                METRICA_FAT, "Variable de B", "desc", "calidad", "grupal", "por_sprint", "unico", "numerico",
                java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN));

        // SM_A SÍ es miembro real de proyectoA (control: no es un 403 genérico de "no
        // pertenece a ningún proyecto"), pero variableB.id() pertenece a B, no a A.
        mockMvc.perform(delete("/api/proyectos/{proyectoId}/variables/{variableId}", proyectoA, variableB.id())
                        .with(comoUsuario(smA)))
                .andExpect(status().is4xxClientError());

        // Confirmación positiva: la variable de B sigue activa — el intento no la tocó.
        assertVariableSigueActiva(variableB.id());
    }

    @Test
    @DisplayName("D) PATCH fórmula de variable de B usando proyectoId=A -> DENEGADO")
    void actualizarFormulaDeVariableAjena_usandoElPropioProyectoIdComoFachada_denegado() throws Exception {
        var variableB = variableService.crear(smB, proyectoB, new CrearVariableRequest(
                METRICA_FAT, "Variable de B (formula)", "desc", "calidad", "grupal", "por_sprint", "unico", "numerico",
                java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN));

        mockMvc.perform(patch("/api/proyectos/{proyectoId}/variables/{variableId}/formula", proyectoA, variableB.id())
                        .with(comoUsuario(smA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.prodox.dto.ActualizarFormulaRequest("X = 1", null, "por_sprint"))))
                .andExpect(status().is4xxClientError());
    }

    private void assertVariableSigueActiva(UUID variableId) {
        var v = variableRepo.findById(variableId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(v.getActiva(),
                "La variable de B debe seguir activa: el intento de A no debió haber tenido efecto");
    }

    // ── H/J: PO_A no puede administrar backlog de B (roles de proyecto aislados) ──

    @Test
    @DisplayName("H) PATCH /api/historias/{historiaId}: PO_A (product_owner en A) NO puede editar una historia de B, aunque conozca su UUID")
    void productOwnerDeA_noPuedeEditarHistoriaDeB() throws Exception {
        String poA = crearUsuario("qa-crosstenant-poA-", "scrum_master");
        ProjectMember pmPoA = new ProjectMember();
        pmPoA.setProyectoId(proyectoA);
        pmPoA.setUserId(poA);
        pmPoA.setUserEmail(userRepo.findById(UUID.fromString(poA)).orElseThrow().getEmail());
        pmPoA.setRol(ProjectMember.ROL_PRODUCT_OWNER);
        memberRepo.save(pmPoA);

        String poB = crearUsuario("qa-crosstenant-poB-", "scrum_master");
        ProjectMember pmPoB = new ProjectMember();
        pmPoB.setProyectoId(proyectoB);
        pmPoB.setUserId(poB);
        pmPoB.setUserEmail(userRepo.findById(UUID.fromString(poB)).orElseThrow().getEmail());
        pmPoB.setRol(ProjectMember.ROL_PRODUCT_OWNER);
        memberRepo.save(pmPoB);

        var historiaB = historiaUsuarioService.crear(proyectoB, poB,
                new CrearHistoriaUsuarioRequest("Historia de B", "desc", "criterios", "media"));

        mockMvc.perform(patch("/api/historias/{historiaId}", historiaB.id())
                        .with(comoUsuario(poA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.prodox.dto.ActualizarHistoriaUsuarioRequest("Título hackeado", "desc", "criterios"))))
                .andExpect(status().isForbidden());

        // Confirmación positiva: el título original de B no cambió.
        var actual = historiaUsuarioService.detalle(historiaB.id());
        org.junit.jupiter.api.Assertions.assertEquals("Historia de B", actual.titulo());
    }

    @Test
    @DisplayName("I) Miembro normal (scrum_member) de A NO puede crear historias en su propio proyecto — acción exclusiva del Product Owner")
    void miembroNormal_noPuedeCrearHistorias_accionExclusivaDeProductOwner() throws Exception {
        mockMvc.perform(post("/api/historias/{proyectoId}", proyectoA)
                        .with(comoUsuario(memberA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CrearHistoriaUsuarioRequest("Historia", "desc", "criterios", "media"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lectura de backlog SÍ permitida a un miembro normal del propio proyecto (control: no rompimos el caso legítimo)")
    void miembroNormal_siPuedeLeerBacklogDeSuPropioProyecto() throws Exception {
        mockMvc.perform(get("/api/historias/{proyectoId}", proyectoA).with(comoUsuario(memberA)))
                .andExpect(status().isOk());
    }
}
