package com.codigo2enter.almacenes.modules.inventory.repository;

import com.codigo2enter.almacenes.modules.inventory.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad Category.
 *
 * Spring Data JPA genera automáticamente las implementaciones SQL de todos
 * los métodos declarados aquí, derivándolas del nombre del método en tiempo
 * de arranque de la aplicación. No es necesario escribir ninguna query manual.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Busca una categoría por su nombre exacto.
     * Utilizado en CategoryServiceImpl para validar unicidad antes de crear
     * o actualizar una categoría y evitar duplicados en la tabla.
     *
     * @param name nombre exacto de la categoría a buscar
     * @return Optional con la categoría si existe, vacío si no
     */
    Optional<Category> findByName(String name);

    /**
     * Recupera todas las categorías cuyo campo 'active' sea true.
     * Este listado se expone en los selectores desplegables de Angular para que
     * el usuario solo pueda asignar categorías vigentes a nuevos productos.
     * Las categorías dadas de baja (soft delete) no aparecen en esta consulta.
     *
     * @return lista de categorías activas, vacía si no hay ninguna
     */
    List<Category> findByActiveTrue();

    /**
     * Retorna una página de categorías activas.
     * La versión sin Pageable se conserva para uso interno (validaciones de servicio,
     * tests que necesitan la colección completa).
     *
     * @param pageable parámetros de paginación y ordenación
     * @return página de categorías activas
     */
    Page<Category> findByActiveTrue(Pageable pageable);
}
