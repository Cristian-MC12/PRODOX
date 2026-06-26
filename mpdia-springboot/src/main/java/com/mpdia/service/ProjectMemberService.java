// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.InvitarProyectoRequest;
import com.mpdia.dto.ProjectMemberDto;
import com.mpdia.dto.UnirseProyectoRequest;
import com.mpdia.entity.ProjectInvitacion;
import com.mpdia.entity.ProjectMember;
import com.mpdia.entity.Proyecto;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.ProjectInvitacionRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository    memberRepo;
    private final ProjectInvitacionRepository invRepo;
    private final ProyectoRepository         proyectoRepo;
    private final AppUserRepository          userRepo;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mpdia.app.url:http://localhost:4200}")
    private String appUrl;

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

    /** Lista miembros de un proyecto */
    public List<ProjectMemberDto> listarMiembros(UUID proyectoId) {
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

    /** Genera código de invitación y envía email */
    @Transactional
    public String invitar(UUID proyectoId, String scrumMasterId, InvitarProyectoRequest req) {
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
        invRepo.save(inv);

        if (mailSender != null) {
            String link = appUrl + "/proyectos/unirse?codigo=" + codigo;
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(req.email());
            msg.setSubject("Invitación al proyecto MPDIA: " + p.getNombre());
            msg.setText(
                "Hola,\n\n" +
                "Fuiste invitado al proyecto \"" + p.getNombre() + "\" en el sistema MPDIA.\n\n" +
                "Método: " + p.getMetodo().toUpperCase() + " | Time Box: " + p.getTimeBoxSemanas() + " semana(s)\n\n" +
                "Ingresá este código en la pantalla de Proyectos para unirte:\n\n" +
                "  " + codigo + "\n\n" +
                "O accedé directamente:\n" + link + "\n\n" +
                "Saludos,\nSistema MPDIA"
            );
            mailSender.send(msg);
        }

        return codigo;
    }

    /** Unirse a un proyecto usando código */
    @Transactional
    public ProjectMemberDto unirse(String userId, UnirseProyectoRequest req) {
        ProjectInvitacion inv = invRepo.findByCodigoAndUsadoFalse(req.codigo().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Código inválido o ya usado."));

        String userEmail = userRepo.findById(UUID.fromString(userId))
                .map(u -> u.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (memberRepo.existsByProyectoIdAndUserId(inv.getProyectoId(), userId)) {
            throw new IllegalArgumentException("Ya eres miembro de este proyecto.");
        }

        ProjectMember m = new ProjectMember();
        m.setProyectoId(inv.getProyectoId());
        m.setUserId(userId);
        m.setUserEmail(userEmail);
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
