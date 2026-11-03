package com.tienda.service;

import com.tienda.domain.Categoria;
import com.tienda.repository.CategoriaRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final FirebaseStorageService firebaseStorageService;

    public CategoriaService(
            CategoriaRepository categoriaRepository,
            FirebaseStorageService firebaseStorageService) {
        this.categoriaRepository = categoriaRepository;
        this.firebaseStorageService = firebaseStorageService;
    }

    @Transactional(readOnly = true)
    public List<Categoria> getCategorias(boolean activo) {
        if (activo) {
            return categoriaRepository.findByActivoTrue();
        }
        return categoriaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Categoria> getCategoria(Integer idCategoria) {
        return categoriaRepository.findById(idCategoria);
    }

    @Transactional
    public void save(Categoria categoria, MultipartFile imagenFile) {
        categoria = categoriaRepository.save(categoria);

        if (imagenFile != null && !imagenFile.isEmpty()) {
            try {
                String rutaImagen = firebaseStorageService.uploadImage(
                        imagenFile,
                        "categoria",
                        categoria.getIdCategoria()
                );

                categoria.setRutaImagen(rutaImagen);
                categoriaRepository.save(categoria);

            } catch (IOException e) {
                throw new IllegalStateException(
                        "No se pudo subir la imagen de la categoría.", e
                );
            }
        }
    }

    @Transactional
    public void delete(Integer idCategoria) {
        if (!categoriaRepository.existsById(idCategoria)) {
            throw new IllegalArgumentException(
                    "La categoría con ID " + idCategoria + " no existe."
            );
        }

        try {
            categoriaRepository.deleteById(idCategoria);
            categoriaRepository.flush();

        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "No se puede eliminar la categoría porque tiene datos asociados.",
                    e
            );
        }
    }
}