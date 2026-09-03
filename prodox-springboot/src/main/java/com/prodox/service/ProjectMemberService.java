// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.InvitacionEstadoDto;
import com.prodox.dto.InvitarProyectoRequest;
import com.prodox.dto.InvitarProyectoResponse;
import com.prodox.dto.ProjectMemberDto;
import com.prodox.dto.UnirseProyectoRequest;
import com.prodox.entity.ProjectInvitacion;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Proyecto;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectInvitacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository    memberRepo;
    private final ProjectInvitacionRepository invRepo;
    private final ProyectoRepository         proyectoRepo;
    private final AppUserRepository          userRepo;
    private final EmailService               emailService;

    @Value("${prodox.app.url:http://localhost:4200}")
    private String appUrl;

    @Value("${prodox.invitacion.expiration-days:7}")
    private long expirationDays;

    /** Agrega al SM como miembro al crear el proyecto */
    @Transactional
    public void agregarScrumMaster(UUID proyectoId, String userId, String userEmail) {
        if (!memberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            ProjectMember m = new ProjectMember();
            m.setProyectoId(proyectoId);
            m.setUserId(userId);
            m.setUserEmail(userEmail);
            m.setRol("scrum_master");
            memberRepo.save(m);
        }
    }

    /** Lista miembros de un proyecto — requiere ser miembro del proyecto */
    public List<ProjectMemberDto> listarMiembros(UUID proyectoId, String userId) {
        if (!memberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        return memberRepo.findByProyectoId(proyectoId).stream()
                .map(m -> new ProjectMemberDto(m.getProyectoId(), m.getUserId(),
                        m.getUserEmail(), m.getRol(), m.getJoinedAt()))
                .toList();
    }

    /** Lista proyectos a los que pertenece un usuario */
    public List<UUID> proyectosDelUsuario(String userId) {
        return memberRepo.findByUserId(userId).stream()
                .map(ProjectMember::getProyectoId)
                .toList();
    }

    // Genera código de invitación y envía email
    @Transactional
    public InvitarProyectoResponse invitar(UUID proyectoId, String scrumMasterId, InvitarProyectoRequest req) {
        Proyecto p = proyectoRepo.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        if (!p.getScrumMasterId().equals(scrumMasterId)) {
            throw new IllegalArgumentException("Solo el Scrum Master puede invitar.");
        }

        // V39: rol opcional en la invitación — si no se envía, se conserva el
        // comportamiento previo (scrum_member).
        String rolInvitacion = req.rol() != null ? req.rol() : ProjectMember.ROL_SCRUM_MEMBER;

        // Defensa en profundidad: el DTO ya bloquea "scrum_master" con
        // @Pattern (solo se aplica cuando la petición pasa por el controller
        // con @Valid), pero este método de servicio debe ser seguro de
        // invocar directamente sin depender de esa validación externa — no
        // hay más que un Scrum Master por proyecto y se asigna únicamente al
        // crear el proyecto (ver agregarScrumMaster), nunca por invitación.
        if (!ProjectMember.ROL_SCRUM_MEMBER.equals(rolInvitacion) && !ProjectMember.ROL_PRODUCT_OWNER.equals(rolInvitacion)) {
            throw new IllegalArgumentException(
                    "Rol de invitación inválido. Solo se puede invitar como scrum_member o product_owner.");
        }

        // V40 — a lo sumo un Product Owner activo por proyecto: ni ya
        // existente ni con una invitación pendiente sin aceptar todavía.
        if (ProjectMember.ROL_PRODUCT_OWNER.equals(rolInvitacion)) {
            validarNoHayProductOwnerActivo(proyectoId);
            validarNoHayInvitacionPoPendiente(proyectoId);
        }

        String codigo = generarCodigo();
        String token  = UUID.randomUUID().toString().replace("-", "");

        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail(req.email());
        inv.setToken(token);
        inv.setCodigo(codigo);
        inv.setRol(rolInvitacion);
        inv.setExpiresAt(Instant.now().plus(expirationDays, ChronoUnit.DAYS));
        invRepo.save(inv);

        // Ruta Angular dedicada a aceptar la invitación (ver InvitacionComponent):
        // antes apuntaba a /proyectos/unirse, una ruta que nunca existió — el
        // enlace del correo terminaba cayendo en el wildcard de app.routes.ts
        // y aterrizaba en /proyectos ("No estás en ningún proyecto todavía").
        String link = appUrl + "/invitacion?codigo=" + codigo;
        boolean emailEnviado = emailService.enviar(req.email(), "Invitación al proyecto PRODOX: " + p.getNombre(),
                "Hola,\n\n" +
                "Fuiste invitado al proyecto \"" + p.getNombre() + "\" en el sistema PRODOX.\n\n" +
                "Método: " + p.getMetodo().toUpperCase() + " | Time Box: " + p.getTimeBoxSemanas() + " semana(s)\n\n" +
                "Ingresá este código en la pantalla de Proyectos para unirte:\n\n" +
                "  " + codigo + "\n\n" +
                "O accedé directamente:\n" + link + "\n\n" +
                "Este enlace vence en " + expirationDays + " día(s) y solo puede usarse una vez.\n\n" +
                "Saludos,\nSistema PRODOX"
        );

        return new InvitarProyectoResponse(codigo, emailEnviado);
    }

    /**
     * Consulta pública (sin autenticación) del estado de una invitación por su
     * código — usada por la ruta Angular /invitacion antes de forzar login,
     * para poder mostrar "invitación válida/expirada/usada/inexistente" sin
     * necesitar sesión todavía.
     */
    public InvitacionEstadoDto consultarInvitacion(String codigo) {
        return invRepo.findByCodigo(codigo.toUpperCase())
                .map(inv -> {
                    String proyectoIdStr = inv.getProyectoId().toString();
                    String nombre = proyectoRepo.findById(inv.getProyectoId())
                            .map(Proyecto::getNombre).orElse(null);

                    if (Boolean.TRUE.equals(inv.getUsado())) {
                        return new InvitacionEstadoDto(proyectoIdStr, nombre, "usada");
                    }
                    if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(Instant.now())) {
                        return new InvitacionEstadoDto(proyectoIdStr, nombre, "expirada");
                    }
                    return new InvitacionEstadoDto(proyectoIdStr, nombre, "valida");
                })
                .orElse(new InvitacionEstadoDto(null, null, "no_existe"));
    }

    // Unirse a un proyecto usando código
    @Transactional
    public ProjectMemberDto unirse(String userId, UnirseProyectoRequest req) {
        ProjectInvitacion inv = invRepo.findByCodigoAndUsadoFalse(req.codigo().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Código inválido o ya usado."));

        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("La invitación expiró.");
        }

        String userEmail = userRepo.findById(UUID.fromString(userId))
                .map(u -> u.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // La invitación quedó asociada a un correo específico al generarse
        // (ProjectMemberService.invitar) — solo la cuenta autenticada con ESE
        // correo puede aceptarla, sin importar cómo inició sesión (login
        // normal, registro o Google): acá siempre se compara contra el email
        // ya validado de la sesión actual, nunca contra algo que mande Angular.
        // Ni se crea el ProjectMember ni se marca usada la invitación si no coincide.
        if (!inv.getEmail().equalsIgnoreCase(userEmail)) {
            throw new IllegalArgumentException("Esta invitación fue enviada a otro correo.");
        }

        if (memberRepo.existsByProyectoIdAndUserId(inv.getProyectoId(), userId)) {
            throw new IllegalArgumentException("Ya eres miembro de este proyecto.");
        }

        // V40 — respaldo ante condiciones de carrera: entre que se generó
        // esta invitación de Product Owner y que se acepta, el Scrum Master
        // pudo haber delegado el rol a otra persona (cambiarRol) o pudo
        // haberse aceptado otra invitación de PO primero. invitar() ya
        // valida esto al CREAR la invitación, pero no garantiza nada sobre
        // lo que pasó después — se vuelve a validar acá, al momento real de
        // crear el ProjectMember (el índice único parcial de V40 es el
        // respaldo final si dos aceptaciones llegaran a la vez).
        if (ProjectMember.ROL_PRODUCT_OWNER.equals(inv.getRol())) {
            validarNoHayProductOwnerActivo(inv.getProyectoId());
        }

        ProjectMember m = new ProjectMember();
        m.setProyectoId(inv.getProyectoId());
        m.setUserId(userId);
        m.setUserEmail(userEmail);
        // V39: el rol por proyecto queda determinado por lo que el Scrum
        // Master eligió al invitar (scrum_member por defecto, o
        // product_owner — ver ProjectMemberService.invitar). Una invitación
        // NUNCA otorga scrum_master (ProjectInvitacion.rol solo admite
        // scrum_member/product_owner, reforzado por el CHECK de V39), ni
        // toca el AppUser.role global del usuario (que ni siquiera se lee en
        // este método) — el rol global inmutable solo se define al crear la
        // cuenta (registro/Google).
        m.setRol(inv.getRol());
        memberRepo.save(m);

        inv.setUsado(true);
        invRepo.save(inv);

        return new ProjectMemberDto(m.getProyectoId(), m.getUserId(),
                m.getUserEmail(), m.getRol(), m.getJoinedAt());
    }

    /**
     * Cambia el rol POR PROYECTO de un miembro existente (V39 — Product Owner).
     * Reglas de seguridad, en orden:
     * <ol>
     *   <li>El solicitante debe ser miembro de {@code proyectoId} (si no,
     *       SecurityException — cubre tanto "usuario externo" como "SM de
     *       otro proyecto intentando tocar este").</li>
     *   <li>El solicitante debe tener rol scrum_master EN ESE proyecto (si
     *       no, SecurityException) — nunca se confía en el rol global de
     *       {@link com.prodox.entity.AppUser}, solo en {@link ProjectMember#getRol()}.</li>
     *   <li>{@code nuevoRol} solo admite scrum_member o product_owner —
     *       "scrum_master" se rechaza siempre (no existe hoy un mecanismo
     *       oficial de reasignación de Scrum Master; no se inventa uno acá).</li>
     *   <li>El usuario objetivo debe ser miembro de este mismo proyecto.</li>
     *   <li>No se permite tocar el rol del miembro que actualmente es
     *       scrum_master del proyecto — evita dejar el proyecto sin Scrum
     *       Master y, como el propio solicitante SIEMPRE es ese scrum_master
     *       (paso 2), esto también impide que alguien se reasigne su propio
     *       rol a sí mismo mediante este endpoint.</li>
     * </ol>
     */
    @Transactional
    public ProjectMemberDto cambiarRol(UUID proyectoId, String solicitanteId, String targetUserId, String nuevoRol) {
        ProjectMember solicitante = memberRepo.findByProyectoIdAndUserId(proyectoId, solicitanteId)
                .orElseThrow(() -> new SecurityException("No tienes acceso a este proyecto"));

        if (!ProjectMember.ROL_SCRUM_MASTER.equals(solicitante.getRol())) {
            throw new SecurityException("Solo el Scrum Master del proyecto puede cambiar roles de sus miembros.");
        }

        if (!ProjectMember.ROL_SCRUM_MEMBER.equals(nuevoRol) && !ProjectMember.ROL_PRODUCT_OWNER.equals(nuevoRol)) {
            throw new IllegalArgumentException(
                    "Rol inválido. Solo se puede asignar scrum_member o product_owner mediante este endpoint.");
        }

        ProjectMember target = memberRepo.findByProyectoIdAndUserId(proyectoId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no es miembro de este proyecto."));

        if (ProjectMember.ROL_SCRUM_MASTER.equals(target.getRol())) {
            throw new IllegalArgumentException(
                    "No se puede cambiar el rol del Scrum Master del proyecto mediante este endpoint.");
        }

        // V40 — a lo sumo un Product Owner activo por proyecto. Si el
        // objetivo YA es product_owner, "cambiarlo a product_owner" es un
        // no-op y no debe rechazarse por chocar consigo mismo — solo se
        // valida cuando se está promoviendo a alguien que todavía no lo es.
        boolean yaEsProductOwner = ProjectMember.ROL_PRODUCT_OWNER.equals(target.getRol());
        if (ProjectMember.ROL_PRODUCT_OWNER.equals(nuevoRol) && !yaEsProductOwner) {
            validarNoHayProductOwnerActivo(proyectoId);
        }

        target.setRol(nuevoRol);
        memberRepo.save(target);

        return new ProjectMemberDto(target.getProyectoId(), target.getUserId(),
                target.getUserEmail(), target.getRol(), target.getJoinedAt());
    }

    /**
     * V40 — a lo sumo un Product Owner activo por proyecto. Se usa desde
     * invitar(), unirse() y cambiarRol(); el respaldo real ante llamadas
     * concurrentes es el índice único parcial de la migración V40 sobre
     * project_members (esta validación evita el caso común con un mensaje
     * claro, sin depender únicamente de esperar la excepción de integridad).
     */
    private void validarNoHayProductOwnerActivo(UUID proyectoId) {
        if (memberRepo.existsByProyectoIdAndRol(proyectoId, ProjectMember.ROL_PRODUCT_OWNER)) {
            throw new IllegalStateException("Este proyecto ya tiene un Product Owner.");
        }
    }

    /**
     * V40 — evita que el Scrum Master genere varias invitaciones de Product
     * Owner pendientes para el mismo proyecto. Una invitación expirada pero
     * nunca aceptada ("usado" sigue en false) NO cuenta como pendiente: ya
     * no puede aceptarse, así que no debería seguir bloqueando una nueva.
     */
    private void validarNoHayInvitacionPoPendiente(UUID proyectoId) {
        boolean hayPendiente = invRepo.findByProyectoIdAndRolAndUsadoFalse(proyectoId, ProjectMember.ROL_PRODUCT_OWNER)
                .stream()
                .anyMatch(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(Instant.now()));
        if (hayPendiente) {
            throw new IllegalStateException("Ya existe una invitación de Product Owner pendiente para este proyecto.");
        }
    }

    private String generarCodigo() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("PRJ-");
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
