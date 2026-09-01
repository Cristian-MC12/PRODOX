// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Punto único de envío de correo (invitaciones a proyectos, recuperación de
 * contraseña). Centraliza lo que antes vivía inline en ProjectMemberService.
 *
 * Soporta dos métodos de envío:
 * 1. SendGrid Web API (preferido, evita bloqueos de puerto SMTP)
 * 2. JavaMailSender/SMTP (fallback si no hay API Key de SendGrid)
 *
 * Si no hay credenciales configuradas o el envío falla, no se lanza
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

    @Value("${prodox.sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${prodox.sendgrid.from-email:noreply@prodox.app}")
    private String sendgridFromEmail;

    @Value("${prodox.sendgrid.from-name:PRODOX}")
    private String sendgridFromName;

    /** Envía un correo de texto plano. Devuelve true solo si se envió realmente. */
    public boolean enviar(String to, String subject, String text) {
        // Intentar primero con SendGrid Web API (evita bloqueos de puerto)
        if (sendgridApiKey != null && !sendgridApiKey.isBlank()) {
            return enviarConSendGrid(to, subject, text);
        }

        // Fallback: usar SMTP tradicional
        if (mailSender != null) {
            return enviarConSMTP(to, subject, text);
        }

        log.warn("EmailService: no hay SendGrid API Key ni JavaMailSender configurado, correo no enviado (asunto: {})", subject);
        return false;
    }

    private boolean enviarConSendGrid(String to, String subject, String text) {
        try {
            Email from = new Email(sendgridFromEmail, sendgridFromName);
            Email toEmail = new Email(to);
            Content content = new Content("text/plain", text);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(sendgridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("EmailService: correo enviado exitosamente vía SendGrid API (asunto: {}, destinatario: {})", subject, to);
                return true;
            } else {
                log.error("EmailService: SendGrid API retornó código {}: {} (asunto: {})", 
                    response.getStatusCode(), response.getBody(), subject);
                return false;
            }
        } catch (IOException e) {
            log.error("EmailService: fallo al enviar correo vía SendGrid API (asunto: {}): {}", subject, e.getMessage());
            return false;
        }
    }

    private boolean enviarConSMTP(String to, String subject, String text) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            if (fromAddress != null && !fromAddress.isBlank()) {
                msg.setFrom(fromAddress);
            }
            mailSender.send(msg);
            log.info("EmailService: correo enviado exitosamente vía SMTP (asunto: {}, destinatario: {})", subject, to);
            return true;
        } catch (Exception e) {
            log.error("EmailService: fallo al enviar correo vía SMTP (asunto: {}): {}", subject, e.getMessage());
            return false;
        }
    }
}
