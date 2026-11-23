package com.tienda.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class CorreoService {

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String remitente;

    private final JavaMailSender mailSender;

    public CorreoService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarCorreoHtml(String para,
            String asunto,
            String contenido) throws MessagingException {

        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper correo = new MimeMessageHelper(mensaje, true, "UTF-8");

        correo.setFrom(remitente);
        correo.setTo(para);
        correo.setSubject(asunto);
        correo.setText(contenido, true);
        mailSender.send(mensaje);
    }
}
