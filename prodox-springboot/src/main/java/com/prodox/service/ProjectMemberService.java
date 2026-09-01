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

        String codigo = generarCodigo();
        String token  = UUID.randomUUID().toString().replace("-", "");

        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail(req.email());
        inv.setToken(token);
        inv.setCodigo(codigo);
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

        ProjectMember m = new ProjectMember();
        m.setProyectoId(inv.getProyectoId());
        m.setUserId(userId);
        m.setUserEmail(userEmail);
        // Rol de miembro fijo en "scrum_member": una invitación nunca otorga
        // scrum_master, ni acá ni tocando el AppUser.role global del usuario
        // (que ni siquiera se lee en este método) — el rol global inmutable
        // solo se define al crear la cuenta (registro/Google).
        m.setRol("scrum_member");
        memberRepo.save(m);

        inv.setUsado(true);
        invRepo.save(inv);

        return new ProjectMemberDto(m.getProyectoId(), m.getUserId(),
                m.getUserEmail(), m.getRol(), m.getJoinedAt());
    }

    private String generarCodigo() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("PRJ-");
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
