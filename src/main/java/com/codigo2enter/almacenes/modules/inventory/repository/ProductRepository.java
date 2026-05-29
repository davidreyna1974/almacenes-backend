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
     * Retorna productos activos cuyo stock DISPONIBLE (físico - reservado)
     * es menor o igual al umbral mínimo configurado.
     *
     * Se actualiza con la introducción de reservedStock: la alerta de
     * reposición debe basarse en lo que realmente puede venderse, no en el
     * stock físico total. Un producto con currentStock=12, reservedStock=10,
     * minimumStock=5 tiene solo 2 disponibles — debe aparecer en la alerta
     * aunque su stock físico supere el mínimo.
     *
     * Criterio de éxito: un producto con availableStock <= minimumStock
     * aparece en el resultado aunque currentStock > minimumStock.
     *
     * @return lista de productos activos con stock disponible en nivel crítico
     */
    @Query("SELECT p FROM Product p " +
           "WHERE (p.currentStock - p.reservedStock) <= p.minimumStock " +
           "AND p.active = true")
    List<Product> findLowStockProducts();

    /**
     * Retorna todos los productos activos con reservas vigentes
     * (reservedStock > 0), ordenados de mayor a menor reservedStock.
     *
     * Usado por ReservationServiceImpl para construir la vista de productos
     * con stock comprometido. El índice parcial idx_products_reserved_stock
     * (WHERE reserved_stock > 0) hace esta consulta eficiente.
     *
     * Criterio de éxito: la lista solo incluye productos donde reservedStock > 0.
     * Si no hay reservas activas, retorna lista vacía.
     *
     * @return productos con reservas, ordenados de mayor a menor reservedStock
     */
    @Query("SELECT p FROM Product p " +
           "WHERE p.reservedStock > 0 AND p.active = true " +
           "ORDER BY p.reservedStock DESC")
    List<Product> findProductsWithActiveReservations();
}
