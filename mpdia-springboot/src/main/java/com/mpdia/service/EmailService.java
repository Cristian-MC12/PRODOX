// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Punto único de envío de correo (invitaciones a proyectos, recuperación de
 * contraseña). Centraliza lo que antes vivía inline en ProjectMemberService.
 *
 * JavaMailSender es opcional: si no hay credenciales SMTP configuradas
 * (spring.mail.username/password vacíos) o el envío falla, no se lanza
 * excepción — se registra el error y se informa al llamador que el correo
 * no salió, para que la operación de negocio (invitar, recuperar contraseña)
 * pueda seguir adelante sin romperse por un problema de correo.
 */
@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    /** Envía un correo de texto plano. Devuelve true solo si se envió realmente. */
    public boolean enviar(String to, String subject, String text) {
        if (mailSender == null) {
            log.warn("EmailService: no hay JavaMailSender configurado, correo no enviado (asunto: {})", subject);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            if (fromAddress != null && !fromAddress.isBlank()) {
                msg.setFrom(fromAddress);
            }
            mailSender.send(msg);
            return true;
        } catch (Exception e) {
            log.error("EmailService: fallo al enviar correo (asunto: {}): {}", subject, e.getMessage());
            return false;
        }
    }
}
