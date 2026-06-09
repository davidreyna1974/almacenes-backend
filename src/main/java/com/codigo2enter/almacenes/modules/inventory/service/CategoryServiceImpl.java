package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.core.dto.PageResponseDTO;
import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.auth.repository.UserRepository;
import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;
import com.codigo2enter.almacenes.modules.inventory.mapper.CategoryMapper;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.repository.CategoryRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.core.exception.DuplicateResourceException;
import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final UserRepository userRepository;

    // Necesario para verificar si la categoría tiene productos activos
    // antes de permitir su desactivación — evita dejar FKs huérfanas.
    private final ProductRepository productRepository;

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
            throw new DuplicateResourceException(
                "Ya existe una categoría con el nombre '" + dto.getName() + "'."
            );
        });

        // MapStruct convierte el DTO a entidad ignorando id, active y campos de auditoría.
        Category category = categoryMapper.toEntity(dto);
        category.setCreatedBy(resolveAuthenticatedUser());
        Category saved = categoryRepository.save(category);
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
     * Sort por name ASC para que el frontend muestre el catálogo en orden
     * alfabético, coherente con la vista de lista sin paginación.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<CategoryDTO> getAllActiveCategories(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Category> result = categoryRepository.findByActiveTrue(pageable);
        return PageResponseDTO.from(result.map(categoryMapper::toDTO));
    }

    /**
     * {@inheritDoc}
     *
     * Normaliza el término de búsqueda (null/blank → null) para que el
     * patrón :search IS NULL del repositorio retorne todas las categorías
     * cuando no se introduce ningún texto.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<CategoryDTO> searchCategories(String search, int page, int size) {
        String normalized = (search != null && !search.isBlank()) ? search.trim() : null;
        PageRequest pageable = PageRequest.of(page, size);
        Page<Category> result = categoryRepository.searchCategories(normalized, pageable);
        return PageResponseDTO.from(result.map(categoryMapper::toDTO));
    }

    /**
     * {@inheritDoc}
     *
     * Flujo de validaciones antes de actualizar:
     *   1. Verifica que la categoría exista.
     *   2. Valida que el nuevo nombre no esté en uso por una categoría DIFERENTE.
     *      Sin esta validación, cambiar el nombre a uno ya registrado lanzaría
     *      una excepción de constraint UNIQUE de PostgreSQL sin mensaje claro —
     *      el frontend no podría mostrar un error comprensible al usuario.
     *      La condición !existing.getId().equals(id) permite que la categoría
     *      conserve su propio nombre sin disparar el error (editar sin cambiar nombre).
     *   3. Actualiza los campos editables directamente sobre la entidad.
     *      Hibernate dirty-checking detecta los cambios y ejecuta el UPDATE
     *      automáticamente al cerrar la transacción, sin llamar a save().
     */
    @Override
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Categoría con id " + id + " no encontrada."
                ));

        // Validar que el nuevo nombre no pertenezca a otra categoría distinta.
        categoryRepository.findByName(dto.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException(
                    "Ya existe otra categoría con el nombre '" + dto.getName() + "'."
                );
            }
        });

        // Actualizar únicamente los campos que el cliente puede modificar.
        // 'id', 'active' y los campos de auditoría son gestionados por el sistema.
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(resolveAuthenticatedUser());

        return categoryMapper.toDTO(category);
    }

    /**
     * {@inheritDoc}
     *
     * Antes de desactivar se verifica que la categoría no tenga productos activos.
     * Si se permitiera desactivar con productos asignados, esos productos
     * quedarían con una FK apuntando a una categoría inactiva — el frontend
     * los mostraría sin categoría válida en tablas y selectores, generando
     * inconsistencias visuales y posibles errores en flujos de edición.
     *
     * La solución correcta es que el usuario reasigne o desactive los productos
     * de esa categoría antes de poder desactivarla.
     */
    @Override
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Categoría con id " + id + " no encontrada."
                ));

        // Verificar que no existan productos activos asignados a esta categoría.
        // findByCategoryIdAndActiveTrue devuelve lista vacía si no hay productos —
        // isEmpty() es la forma más directa de verificarlo sin contar registros.
        if (!productRepository.findByCategoryIdAndActiveTrue(id).isEmpty()) {
            throw new BusinessRuleException(
                "No se puede desactivar la categoría: tiene productos activos asignados. " +
                "Reasigne o desactive los productos antes de continuar."
            );
        }

        // Soft delete: Hibernate dirty-checking persiste todos los cambios al cerrar la transacción.
        category.setActive(false);
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(resolveAuthenticatedUser());
    }

    /**
     * Resuelve el usuario autenticado desde el JWT en SecurityContextHolder.
     * Mismo patrón que ProductServiceImpl, PurchaseOrderServiceImpl y SupplierServiceImpl.
     */
    private User resolveAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario autenticado no encontrado en el sistema."));
    }
}
