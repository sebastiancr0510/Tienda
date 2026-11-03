package com.tienda.controller;

import com.tienda.domain.Producto;
import com.tienda.service.*;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/producto")
public class ProductoController {

    private final ProductoService productoService;
    private final CategoriaService categoriaService;
    private final MessageSource messageSource;

    @GetMapping("/listado")
    public String listado(Model model) {
        var productos = productoService.getProductos(false);
        model.addAttribute("productos", productos);
        model.addAttribute("totalProductos", productos.size());

        var categorias = categoriaService.getCategorias(true);
        model.addAttribute("categorias", categorias);

        return "producto/listado";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid Producto producto, BindingResult bindingResult,
            @RequestParam(value = "imagenFile", required = false) MultipartFile imagenFile,
            RedirectAttributes redirectAttributes) {

        String titulo = "todoOk";
        String mensaje = "mensaje.actualizado";

        if (bindingResult.hasErrors()) {
            titulo = "error";
            mensaje = "producto.error04";
        } else {
            try {
                productoService.save(producto, imagenFile);
            } catch (DataIntegrityViolationException e) {
                titulo = "error";
                mensaje = "producto.error05";
            } catch (IllegalStateException e) {
                titulo = "error";
                mensaje = "producto.error06";
            }
        }

        redirectAttributes.addFlashAttribute(
                titulo,
                messageSource.getMessage(mensaje, null, Locale.getDefault())
        );

        return "redirect:/producto/listado";
    }

    @PostMapping("/eliminar")
    public String eliminar(@RequestParam Integer idProducto,
            RedirectAttributes redirectAttributes) {

        String titulo = "todoOk";
        String detalle = "mensaje.eliminado";

        try {
            productoService.delete(idProducto);

        } catch (IllegalArgumentException e) {
            titulo = "error";
            detalle = "producto.error01";

        } catch (IllegalStateException e) {
            titulo = "error";
            detalle = "producto.error02";

        } catch (Exception e) {
            titulo = "error";
            detalle = "producto.error03";
        }

        redirectAttributes.addFlashAttribute(
                titulo,
                messageSource.getMessage(detalle, null, Locale.getDefault())
        );

        return "redirect:/producto/listado";
    }
}
