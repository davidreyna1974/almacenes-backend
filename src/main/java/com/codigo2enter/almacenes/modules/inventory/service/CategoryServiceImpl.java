package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.CategoryMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación concreta de CategoryService.
 *
 * @Service      — registra la clase como bean de la capa de negocio.
 * @Transactional — todas las operaciones de escritura se envuelven en una
 *                  transacción; si ocurre una excepción, Hibernate hace rollback
 *                  automáticamente sin dejar datos parciales en la base de datos.
 * @RequiredArgsConstructor — Lombok genera el constructor con los campos 'final',
 *                            que Spring usa para inyectar las dependencias.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    /**
     * {@inheritDoc}
     *
     * Usa findByName en lugar de existsByName para aplicar la validación
     * de unicidad: si el Optional tiene valor, el nombre ya está registrado.
     * El servicio inicializa 'active = true' a través del @Builder.Default
     * de la entidad — el mapper lo ignora en toEntity().
     */
    @Override
    public CategoryDTO createCategory(CategoryDTO dto) {
        // Validar unicidad del nombre antes de intentar persistir.
        // findByName devuelve Optional, isPresent() indica duplicado.
        categoryRepository.findByName(dto.getName()).ifPresent(existing -> {
            throw new RuntimeException(
                "Ya existe una categoría con el nombre '" + dto.getName() + "'."
            );
        });

        // MapStruct convierte el DTO a entidad ignorando id y active.
        // active se inicializa en true por @Builder.Default en Category.
        Category saved = categoryRepository.save(categoryMapper.toEntity(dto));
        return categoryMapper.toDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * readOnly = true indica a Hibernate que no necesita rastrear cambios
     * en las entidades devueltas — optimización para operaciones de solo lectura.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllActiveCategories() {
        return categoryMapper.toDTOList(categoryRepository.findByActiveTrue());
    }

    /**
     * {@inheritDoc}
     *
     * Como eliminamos updateFromDTO del mapper, el servicio actualiza
     * directamente los campos editables de la entidad. Hibernate detecta
     * los cambios al final de la transacción y ejecuta el UPDATE automáticamente
     * sin necesidad de llamar explícitamente a save().
     */
    @Override
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Categoría con id " + id + " no encontrada."
                ));

        // Actualizar únicamente los campos que el cliente puede modificar.
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());

        // Hibernate dirty-checking persiste el cambio al cerrar la transacción.
        return categoryMapper.toDTO(category);
    }

    /**
     * {@inheritDoc}
     *
     * Soft delete: marca la categoría como inactiva sin borrar el registro.
     * Esto preserva la integridad referencial — los productos que apunten
     * a esta categoría no quedan con una FK huérfana.
     */
    @Override
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                    "Categoría con id " + id + " no encontrada."
                ));

        // Hibernate detecta el cambio en active y ejecuta el UPDATE al cerrar la transacción.
        category.setActive(false);
    }
}
