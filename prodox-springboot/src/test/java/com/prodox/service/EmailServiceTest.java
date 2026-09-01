package com.prodox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EmailService — pruebas unitarias")
class EmailServiceTest {

    @Mock JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "mpdia@gmail.com");
    }

    @Test
    @DisplayName("enviar: envía el correo y retorna true cuando JavaMailSender está disponible")
    void enviar_mailSenderDisponible_envioExitoso_retornaTrue() {
        boolean resultado = emailService.enviar("destino@prodox.com", "Asunto", "Cuerpo del mensaje");

        assertThat(resultado).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("enviar: si mailSender.send() lanza una excepción, retorna false sin propagarla")
    void enviar_fallaEnvio_retornaFalseSinLanzar() {
        doThrow(new MailSendException("SMTP no disponible")).when(mailSender).send(any(SimpleMailMessage.class));

        boolean resultado = emailService.enviar("destino@prodox.com", "Asunto", "Cuerpo del mensaje");

        assertThat(resultado).isFalse();
    }

    @Test
    @DisplayName("enviar: retorna false sin lanzar si no hay JavaMailSender configurado (credenciales SMTP ausentes)")
    void enviar_sinMailSenderConfigurado_retornaFalse() {
        ReflectionTestUtils.setField(emailService, "mailSender", null);

        boolean resultado = emailService.enviar("destino@prodox.com", "Asunto", "Cuerpo del mensaje");

        assertThat(resultado).isFalse();
    }
}
