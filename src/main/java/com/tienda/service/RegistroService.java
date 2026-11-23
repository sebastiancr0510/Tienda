package com.tienda.service;

import com.tienda.domain.Usuario;
import jakarta.mail.MessagingException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RegistroService {

    private final CorreoService correoService;
    private final UsuarioService usuarioService;
    private final MessageSource messageSource;

    public RegistroService(CorreoService correoService, UsuarioService usuarioService, MessageSource messageSource) {
        this.correoService = correoService;
        this.usuarioService = usuarioService;
        this.messageSource = messageSource;
    }

    //Este método se usa en el enlace del correo enviado...
    public Model activar(Model model, String username, String clave) {
        Optional<Usuario> usuario = usuarioService.getUsuarioPorUsernameYPassword(username, clave);
        if (usuario.isPresent() && !usuario.get().isActivo()) {  //Si estaba...
            model.addAttribute("clave", clave);
            model.addAttribute("usuario", usuario.get());
        } else { //hay que devolver error
            model.addAttribute("titulo", messageSource.getMessage("registro.activar", null, Locale.getDefault()));
            model.addAttribute("mensaje", messageSource.getMessage("registro.activar.error", null, Locale.getDefault()));
        }
        return model;
    }

    //Este método es el que finalmente crea el usuario en el sistema
    @org.springframework.transaction.annotation.Transactional
    public void activar(Usuario formulario, MultipartFile imagenFile, String clave) {
        Usuario usuario = usuarioService.getUsuario(formulario.getIdUsuario())
                .filter(u -> !u.isActivo() && u.getPassword().equals(clave))
                .orElseThrow(() -> new IllegalArgumentException("Enlace de activación inválido o utilizado."));
        if (formulario.getPassword() == null || formulario.getPassword().isBlank()) {
            throw new IllegalArgumentException("Debe indicar una contraseña nueva.");
        }
        usuario.setPassword(formulario.getPassword());
        usuario.setNombre(formulario.getNombre());
        usuario.setApellidos(formulario.getApellidos());
        usuario.setTelefono(formulario.getTelefono());
        usuario.setActivo(true);
        usuarioService.save(usuario, imagenFile, true);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Model crearUsuario(Model model, Usuario usuario) throws MessagingException {
        if (usuario.getUsername() == null || !usuario.getUsername().matches("[A-Za-z0-9_.-]{3,30}")
                || usuario.getCorreo() == null || !usuario.getCorreo().contains("@")) {
            throw new IllegalArgumentException("Indique un usuario válido (3 a 30 caracteres) y un correo.");
        }
        if (usuarioService.existeUsuarioPorUsernameOCorreo(usuario.getUsername(), usuario.getCorreo())) {
            throw new IllegalArgumentException("El usuario o correo ya existe.");
        }
        usuario.setIdUsuario(null);
        usuario.setRutaImagen(null);
        usuario.setRoles(new java.util.HashSet<>());
        String clave = demeClave();
        usuario.setPassword(clave);
        usuario.setActivo(false);
        usuarioService.save(usuario, null, false);
        enviaCorreoActivar(usuario, clave);
        model.addAttribute("titulo", "Activación de cuenta");
        model.addAttribute("mensaje", "Revise su correo para activar su cuenta.");
        return model;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Model recordarUsuario(Model model, Usuario usuario)
            throws MessagingException {
        String mensaje;
        final String correoBuscado = usuario.getCorreo();
        Optional<Usuario> usuarioOpt = usuarioService.getUsuarioPorUsername(usuario.getUsername())
                .filter(u -> u.getCorreo() != null && u.getCorreo().equalsIgnoreCase(correoBuscado));
        if (!usuarioOpt.isEmpty()) {
            usuario=usuarioOpt.get();
            String clave = demeClave();
            usuario.setPassword(clave);
            usuario.setActivo(false);
            usuarioService.save(usuario, null,false);
            enviaCorreoRecordar(usuario, clave);
            mensaje = String.format(messageSource.getMessage("registro.mensaje.recordar.ok",   null, Locale.getDefault()), usuario.getCorreo());
        } else {
            mensaje = "El usuario y correo no coinciden con una cuenta registrada.";
        }
        model.addAttribute("titulo", messageSource.getMessage("registro.activar", null, Locale.getDefault()));
        model.addAttribute("mensaje", mensaje);
        return model;
    }

    private String demeClave() {
        byte[] bytes = new byte[30];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    //Ojo cómo le lee una informacion del application.properties
    @Value("${servidor.http}")
    private String servidor;

    private void enviaCorreoActivar(Usuario usuario, String clave) throws MessagingException {
        String mensaje = messageSource.getMessage("registro.correo.activar", null, Locale.getDefault());
        mensaje = String.format(mensaje, org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(usuario.getNombre(), "")), org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(usuario.getApellidos(), "")), servidor, usuario.getUsername(), clave);
        String asunto = messageSource.getMessage("registro.mensaje.activacion", null, Locale.getDefault());
        correoService.enviarCorreoHtml(usuario.getCorreo(), asunto, mensaje);
    }

    private void enviaCorreoRecordar(Usuario usuario, String clave) throws MessagingException {
        String mensaje = messageSource.getMessage("registro.correo.recordar", null, Locale.getDefault());
        mensaje = String.format(mensaje, org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(usuario.getNombre(), "")), org.springframework.web.util.HtmlUtils.htmlEscape(java.util.Objects.toString(usuario.getApellidos(), "")), servidor, usuario.getUsername(), clave);
        String asunto = messageSource.getMessage("registro.mensaje.recordar", null, Locale.getDefault());
        correoService.enviarCorreoHtml(usuario.getCorreo(), asunto, mensaje);
    }
}
