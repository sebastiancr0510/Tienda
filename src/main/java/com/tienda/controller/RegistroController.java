package com.tienda.controller;

import com.tienda.domain.Usuario;
import com.tienda.service.RegistroService;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/registro")
public class RegistroController {
    
    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }    
    
    @org.springframework.web.bind.annotation.InitBinder("usuario")
    public void camposPermitidos(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("idUsuario", "username", "nombre", "apellidos", "correo", "telefono", "password");
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model, Usuario usuario) {
        return "/registro/nuevo";
    }

    @GetMapping("/recordar")
    public String recordar(Model model, Usuario usuario) {
        return "/registro/recordar";
    }

    @PostMapping("/crearUsuario")
    public String crearUsuario(Model model, Usuario usuario) 
            throws MessagingException {
        model = registroService.crearUsuario(model, usuario);
        return "/registro/salida";
    }

    @GetMapping("/activacion/{usuario}/{id}")
    public String activar(
            Model model, 
            @PathVariable(value = "usuario") String usuario, 
            @PathVariable(value = "id") String id) {
        model = registroService.activar(model, usuario, id);
        if (model.containsAttribute("usuario")) {
            return "/registro/activa";
        } else {
            return "/registro/salida";
        }
    }

    @PostMapping("/activar")
    public String activar(
            Usuario usuario, 
            @RequestParam(value = "imagenFile", required = false) MultipartFile imagenFile,
            @RequestParam String clave) {
        registroService.activar(usuario, imagenFile, clave);
        return "redirect:/";
    }

    @PostMapping("/recordarUsuario")
    public String recordarUsuario(Model model, Usuario usuario) 
            throws MessagingException {
        model = registroService.recordarUsuario(model, usuario);
        return "/registro/salida";
    }
    @org.springframework.web.bind.annotation.ExceptionHandler({MessagingException.class,
        org.springframework.mail.MailException.class, IllegalArgumentException.class,
        org.springframework.dao.DataIntegrityViolationException.class})
    public String error(Exception ex, Model model) {
        model.addAttribute("titulo", "No se pudo completar la solicitud");
        model.addAttribute("mensaje", ex instanceof IllegalArgumentException
                ? ex.getMessage() : "Revise los datos y la configuración de correo e intente nuevamente.");
        return "registro/salida";
    }
}
