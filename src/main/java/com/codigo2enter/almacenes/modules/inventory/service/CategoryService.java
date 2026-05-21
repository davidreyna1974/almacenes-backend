package com.codigo2enter.almacenes.modules.inventory.service;

import com.codigo2enter.almacenes.modules.inventory.dto.CategoryDTO;

import java.util.List;

/**
 * Contrato de la capa de servicio para la gestión de categorías de inventario.
 *
 * Define las operaciones de negocio disponibles para el controlador REST.
 * La implementación concreta vive en CategoryServiceImpl, desacoplando
 * la interfaz del detalle técnico (JPA, validaciones, transacciones).
 */
public interface CategoryService {

    /**
     * Crea una nueva categoría en el sistema.
     * Valida que no exista otra categoría con el mismo nombre antes de persistir.
     *
     * @param dto datos de la nueva categoría enviados por el cliente
     * @return CategoryDTO con el id asignado por la base de datos
     */
    CategoryDTO createCategory(CategoryDTO dto);

    /**
     * Retorna todas las categorías activas (active = true).
     * Usado por el frontend para poblar selectores de asignación de productos.
     *
     * @return lista de categorías vigentes, vacía si no hay ninguna
     */
    List<CategoryDTO> getAllActiveCategories();

    /**
     * Actualiza los datos de una categoría existente.
     *
     * @param id  identificador de la categoría a modificar
     * @param dto datos nuevos enviados por el cliente
     * @return CategoryDTO con los datos actualizados
     */
    CategoryDTO updateCategory(Long id, CategoryDTO dto);

    /**
     * Desactiva lógicamente una categoría (soft delete: active = false).
     * No elimina el registro de la base de datos para preservar la integridad
     * referencial con los productos que tengan esta categoría asignada.
     *
     * @param id identificador de la categoría a desactivar
     */
    void deactivateCategory(Long id);
}
