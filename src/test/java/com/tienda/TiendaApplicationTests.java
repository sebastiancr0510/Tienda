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
                "/consultas/listado", "/categoria/modificar/" + categoria.getIdCategoria(),
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
        for (String ruta : List.of("/", "/consultas/listado", "/login", "/acceso_denegado")) {
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
}
