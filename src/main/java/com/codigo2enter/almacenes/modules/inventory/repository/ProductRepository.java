package com.codigo2enter.almacenes.modules.inventory.repository;

import com.codigo2enter.almacenes.modules.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad Product.
 *
 * Combina query methods derivados (Spring Data infiere el SQL del nombre del
 * método) con una consulta JPQL personalizada para el caso de stock bajo,
 * que requiere comparar dos campos de la misma entidad — algo que los query
 * methods derivados no pueden expresar directamente.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Busca un producto por su SKU (Stock Keeping Unit).
     * Utilizado en ProductServiceImpl para validar unicidad del SKU antes de
     * crear un producto y para el endpoint de búsqueda por código.
     *
     * @param sku código único del producto
     * @return Optional con el producto si existe, vacío si no
     */
    Optional<Product> findBySku(String sku);

    /**
     * Recupera todos los productos activos que pertenezcan a una categoría específica.
     * La condición 'AndActiveTrue' excluye productos dados de baja (soft delete),
     * garantizando que Angular solo muestre artículos operativos en el filtro
     * por categoría del módulo de inventario.
     *
     * @param categoryId ID de la categoría por la que filtrar
     * @return lista de productos activos de esa categoría
     */
    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * Verifica si ya existe un producto registrado con el SKU indicado.
     * Más eficiente que findBySku() para validaciones de unicidad porque
     * Spring Data genera un SELECT COUNT en lugar de traer el objeto completo.
     *
     * @param sku código a verificar
     * @return true si el SKU ya está registrado, false si está disponible
     */
    boolean existsBySku(String sku);

    /**
     * Consulta JPQL personalizada que retorna todos los productos activos
     * cuyo stock actual es menor o igual a su umbral mínimo configurado.
     *
     * Se usa JPQL en lugar de un query method derivado porque la condición
     * compara dos campos de la misma entidad (currentStock <= minimumStock),
     * algo que la nomenclatura de Spring Data no puede expresar directamente.
     *
     * El resultado alimenta el endpoint GET /products/low-stock, que Angular
     * usa para mostrar alertas visuales de productos que requieren reposición.
     *
     * @return lista de productos activos con stock en nivel crítico
     */
    @Query("SELECT p FROM Product p WHERE p.currentStock <= p.minimumStock AND p.active = true")
    List<Product> findLowStockProducts();
}
