package com.tienda;

import com.google.cloud.storage.Storage;
import com.tienda.domain.Categoria;
import com.tienda.domain.Producto;
import com.tienda.repository.CategoriaRepository;
import com.tienda.repository.ProductoRepository;
import com.tienda.service.ProductoService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tienda_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class TiendaApplicationTests {
    @MockitoBean
    Storage storage;
    @Autowired
    MockMvc mvc;
    @Autowired
    CategoriaRepository categorias;
    @Autowired
    ProductoRepository productos;
    @Autowired
    ProductoService productoService;

    @Autowired com.tienda.repository.UsuarioRepository usuarios;
    @Autowired com.tienda.repository.RolRepository roles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @MockitoBean org.springframework.mail.javamail.JavaMailSender mailSender;

    @org.junit.jupiter.api.BeforeEach
    void usuariosDePrueba() {
        String[][] datos = {{"juan", "123", "ADMIN"}, {"rebeca", "456", "VENDEDOR"}, {"pedro", "789", "USUARIO"}};
        for (String[] dato : datos) {
            var rol = new com.tienda.domain.Rol(); rol.setRol(dato[2]); roles.save(rol);
            var usuario = new com.tienda.domain.Usuario();
            usuario.setUsername(dato[0]); usuario.setPassword(encoder.encode(dato[1]));
            usuario.setCorreo(dato[0] + "@example.com"); usuario.setActivo(true);
            usuario.getRoles().add(rol); usuarios.save(usuario);
        }
    }

    private Categoria crearCategoria() {
        Categoria categoria = new Categoria();
        categoria.setDescripcion("Prueba");
        return categorias.save(categoria);
    }

    private Producto crearProducto(Categoria categoria, String descripcion, String precio) {
        Producto producto = new Producto();
        producto.setCategoria(categoria);
        producto.setDescripcion(descripcion);
        producto.setPrecio(new BigDecimal(precio));
        producto.setExistencias(5);
        producto.setActivo(true);
        return productos.save(producto);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void consultasDevuelvenElMismoRangoOrdenado() {
        Categoria categoria = crearCategoria();
        crearProducto(categoria, "Fuera del rango", "30.00");
        Producto mayor = crearProducto(categoria, "Mayor", "20.50");
        Producto menor = crearProducto(categoria, "Menor", "10.25");
        BigDecimal inferior = new BigDecimal("10.25");
        BigDecimal superior = new BigDecimal("20.50");
        List<Integer> esperado = List.of(menor.getIdProducto(), mayor.getIdProducto());
        assertThat(productoService.consultaDerivada(inferior, superior))
                .extracting(Producto::getIdProducto).containsExactlyElementsOf(esperado);
        assertThat(productoService.consultaJPQL(inferior, superior))
                .extracting(Producto::getIdProducto).containsExactlyElementsOf(esperado);
        assertThat(productoService.consultaSQL(inferior, superior))
                .extracting(Producto::getIdProducto).containsExactlyElementsOf(esperado);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void paginasYConsultasSeRenderizan() throws Exception {
        Categoria categoria = crearCategoria();
        crearProducto(categoria, "Producto de prueba", "10.25");
        for (String ruta : List.of("/", "/categoria/listado", "/producto/listado",
                "/consultas/listado", "/usuario/listado", "/categoria/modificar/" + categoria.getIdCategoria(),
                "/consultas/" + categoria.getIdCategoria())) {
            mvc.perform(get(ruta)).andExpect(status().isOk());
        }
        for (String consulta : List.of("consultaDerivada", "consultaJPQL", "consultaSQL")) {
            mvc.perform(post("/consultas/" + consulta).with(csrf())
                    .param("precioInf", "10.00").param("precioSup", "11.00"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Producto de prueba")));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productoGuardaLaCategoriaSinImagen() throws Exception {
        Categoria categoria = crearCategoria();
        mvc.perform(post("/producto/guardar").with(csrf())
                .param("descripcion", "Producto nuevo")
                .param("precio", "12.50").param("existencias", "3")
                .param("activo", "true")
                .param("categoria.idCategoria", categoria.getIdCategoria().toString()))
                .andExpect(redirectedUrl("/producto/listado"))
                .andExpect(flash().attributeExists("todoOk"));
        assertThat(productos.findAll()).singleElement().satisfies(producto ->
                assertThat(producto.getCategoria().getIdCategoria()).isEqualTo(categoria.getIdCategoria()));
    }

    @Test
    @WithAnonymousUser
    void paginasPublicasYLoginSonAccesibles() throws Exception {
        for (String ruta : List.of("/", "/consultas/listado", "/login", "/acceso_denegado", "/registro/nuevo", "/registro/recordar")) {
            mvc.perform(get(ruta)).andExpect(status().isOk());
        }
        mvc.perform(get("/producto/listado"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "VENDEDOR")
    void vendedorConsultaPeroNoModifica() throws Exception {
        mvc.perform(get("/producto/listado")).andExpect(status().isOk());
        mvc.perform(get("/categoria/listado")).andExpect(status().isOk());
        for (String ruta : List.of("/producto/guardar", "/producto/eliminar",
                "/categoria/guardar", "/categoria/eliminar")) {
            mvc.perform(post(ruta).with(csrf())).andExpect(status().isForbidden())
                    .andExpect(forwardedUrl("/acceso_denegado"));
        }
    }

    @Test
    @WithMockUser(roles = "USUARIO")
    void usuarioNoAccedeAlMantenimiento() throws Exception {
        mvc.perform(get("/producto/listado")).andExpect(status().isForbidden());
        mvc.perform(get("/categoria/listado")).andExpect(status().isForbidden());
    }

    @Test
    void credencialesDelProfesorYLogoutFuncionan() throws Exception {
        for (String[] credenciales : new String[][] {
                {"juan", "123"}, {"rebeca", "456"}, {"pedro", "789"}}) {
            MvcResult login = mvc.perform(post("/login").with(csrf())
                    .param("username", credenciales[0]).param("password", credenciales[1]))
                    .andExpect(redirectedUrl("/")).andReturn();
            MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
            assertThat(sesion).isNotNull();
            mvc.perform(get("/").session(sesion)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(credenciales[0])));
            mvc.perform(post("/logout").session(sesion).with(csrf()))
                    .andExpect(redirectedUrl("/login?logout=true"));
        }
        mvc.perform(post("/login").with(csrf()).param("username", "juan")
                .param("password", "incorrecta"))
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Autowired com.tienda.service.UsuarioService usuarioService;

    @Test
    void editarSinClaveConservaHashYRoles() {
        var existente = usuarios.findByUsername("juan").orElseThrow();
        String hash = existente.getPassword();
        var editado = new com.tienda.domain.Usuario();
        editado.setIdUsuario(existente.getIdUsuario());
        editado.setUsername("juan"); editado.setCorreo("juan@example.com");
        editado.setPassword(""); editado.setActivo(true);
        usuarioService.save(editado, null, true);
        var guardado = usuarios.findByUsername("juan").orElseThrow();
        assertThat(guardado.getPassword()).isEqualTo(hash);
        assertThat(encoder.matches("123", guardado.getPassword())).isTrue();
        assertThat(guardado.getRoles()).extracting(com.tienda.domain.Rol::getRol).contains("ADMIN");
    }

    @Test
    void registroActivaSoloConTokenYNoPermiteReutilizarlo() throws Exception {
        var mensaje = new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mensaje);
        mvc.perform(post("/registro/crearUsuario").with(csrf())
                .param("username", "nueva").param("correo", "nueva@example.com")
                .param("nombre", "Nueva").param("apellidos", "Prueba"))
                .andExpect(status().isOk());
        var nuevo = usuarios.findByUsername("nueva").orElseThrow();
        assertThat(nuevo.isActivo()).isFalse();
        String token = nuevo.getPassword();
        org.mockito.Mockito.verify(mailSender).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
        mvc.perform(get("/registro/activacion/nueva/" + token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"clave\"")));
        mvc.perform(post("/registro/activar").with(csrf())
                .param("idUsuario", nuevo.getIdUsuario().toString())
                .param("clave", "incorrecta").param("password", "Nueva123"))
                .andExpect(status().isOk());
        assertThat(nuevo.isActivo()).isFalse();
        mvc.perform(post("/registro/activar").with(csrf())
                .param("idUsuario", nuevo.getIdUsuario().toString())
                .param("clave", token).param("password", "Nueva123")
                .param("nombre", "Nueva").param("apellidos", "Prueba"))
                .andExpect(redirectedUrl("/"));
        assertThat(nuevo.isActivo()).isTrue();
        assertThat(encoder.matches("Nueva123", nuevo.getPassword())).isTrue();
        mvc.perform(get("/registro/activacion/nueva/" + token))
                .andExpect(view().name("/registro/salida"));
        mvc.perform(post("/registro/crearUsuario").param("username", "sinCsrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void formularioEdicionUsuarioSeRenderiza() throws Exception {
        var usuario = usuarios.findByUsername("juan").orElseThrow();
        mvc.perform(get("/usuario/modificar/" + usuario.getIdUsuario()))
                .andExpect(status().isOk());
    }

    @Test
    void recuperarCuentaExigeUsuarioYCorreoYEnviaEnlace() throws Exception {
        var existente = usuarios.findByUsername("juan").orElseThrow();
        String hash = existente.getPassword();
        mvc.perform(post("/registro/recordarUsuario").with(csrf())
                .param("username", "juan").param("correo", "otro@example.com"))
                .andExpect(status().isOk());
        assertThat(existente.getPassword()).isEqualTo(hash);
        org.mockito.Mockito.verifyNoInteractions(mailSender);
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
        mvc.perform(post("/registro/recordarUsuario").with(csrf())
                .param("username", "juan").param("correo", "juan@example.com"))
                .andExpect(status().isOk());
        assertThat(existente.isActivo()).isFalse();
        assertThat(existente.getPassword()).isNotEqualTo(hash);
        org.mockito.Mockito.verify(mailSender).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
        mvc.perform(get("/registro/activacion/juan/" + existente.getPassword()))
                .andExpect(status().isOk());
    }
    @Test
    void inventarioIncluyeElLimiteYOrdenaSoloProductosActivos() throws Exception {
        Categoria categoria = crearCategoria();
        Producto limite = crearProducto(categoria, "En el límite", "15.00");
        Producto agotado = crearProducto(categoria, "Agotado", "10.00");
        agotado.setExistencias(0);
        Producto abundante = crearProducto(categoria, "Fuera del límite", "20.00");
        abundante.setExistencias(6);
        Producto inactivo = crearProducto(categoria, "Inactivo", "12.00");
        inactivo.setActivo(false);
        assertThat(productoService.getProductosConInventarioBajo(5))
                .extracting(Producto::getIdProducto)
                .containsExactly(agotado.getIdProducto(), limite.getIdProducto());
        mvc.perform(get("/consultas/inventario").param("maximo", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Agotado")));
    }

    @Autowired jakarta.persistence.EntityManager entidades;

    @Test
    @WithMockUser(username = "pedro", roles = "USUARIO")
    void compraGeneraFacturaConDetalleYDescuentaInventario() throws Exception {
        var producto = crearProducto(crearCategoria(), "Producto de compra", "12.50");
        String id = producto.getIdProducto().toString();
        var sesion = new MockHttpSession();
        mvc.perform(post("/carrito/agregar").session(sesion).with(csrf()).param("idProducto", id))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/carrito/listado")));
        mvc.perform(get("/carrito/modificar/" + id).session(sesion)).andExpect(status().isOk());
        mvc.perform(post("/carrito/actualizar").session(sesion).with(csrf())
                .param("producto.idProducto", id).param("cantidad", "2")).andExpect(redirectedUrl("/carrito/listado"));
        mvc.perform(get("/carrito/listado").session(sesion)).andExpect(content().string(org.hamcrest.Matchers.containsString("Producto de compra")));
        mvc.perform(post("/facturar/carrito").session(sesion).with(csrf())).andExpect(redirectedUrl("/carrito/verFactura"));
        entidades.flush();
        entidades.clear();
        mvc.perform(get("/carrito/verFactura").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Producto de compra")));
        assertThat(productos.findById(Integer.valueOf(id)).orElseThrow().getExistencias()).isEqualTo(3);
        assertThat((java.util.List<?>) sesion.getAttribute("carrito")).isEmpty();
    }
}
