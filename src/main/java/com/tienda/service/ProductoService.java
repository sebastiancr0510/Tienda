
package com.tienda.service;

import com.tienda.domain.Producto;
import com.tienda.repository.ProductoRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final FirebaseStorageService firebaseStorageService;

    public ProductoService(ProductoRepository productoRepository,
            FirebaseStorageService firebaseStorageService) {
        this.productoRepository = productoRepository;
        this.firebaseStorageService = firebaseStorageService;
    }

    @Transactional(readOnly = true)
    public List<Producto> getProductos(boolean activo) {
        if (activo) {
            return productoRepository.findByActivoTrue();
        }
        return productoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Producto> getProducto(Integer idProducto) {
        return productoRepository.findById(idProducto);
    }

    @Transactional
    public void save(Producto producto, MultipartFile imagenFile) {
        producto = productoRepository.save(producto);

        if (imagenFile != null && !imagenFile.isEmpty()) {
            try {
                String rutaImagen = firebaseStorageService.uploadImage(
                        imagenFile, "productos",
                        producto.getIdProducto());

                producto.setRutaImagen(rutaImagen);
                productoRepository.save(producto);

            } catch (IOException e) {
                throw new IllegalStateException("No se pudo subir la imagen del producto.", e);
            }
        }
    }

    @Transactional
    public void delete(Integer idProducto) {

        // Verifica si la categoría está dentro de interés eliminado
        if (!productoRepository.existsById(idProducto)) {
            // Lanza una excepción para indicar que el usuario no fue encontrado
            throw new IllegalArgumentException(
                    "El producto con ID " + idProducto + " no existe.");
        }

        try {
            productoRepository.deleteById(idProducto);
            productoRepository.flush();

        } catch (DataIntegrityViolationException e) {
            // Lanza una excepción personalizada si existen problemas de integridad de datos
            throw new IllegalStateException(
                    "No se puede eliminar el producto. Tiene datos asociados.", e);
        }
    }
    @Transactional(readOnly = true)
    public List<Producto> consultaDerivada(BigDecimal precioInf, BigDecimal precioSup) {
        return productoRepository.findByPrecioBetweenOrderByPrecioAsc(precioInf, precioSup);
    }

    @Transactional(readOnly = true)
    public List<Producto> consultaJPQL(BigDecimal precioInf, BigDecimal precioSup) {
        return productoRepository.consultaJPQL(precioInf, precioSup);
    }

    @Transactional(readOnly = true)
    public List<Producto> consultaSQL(BigDecimal precioInf, BigDecimal precioSup) {
        return productoRepository.consultaSQL(precioInf, precioSup);
    }

}
