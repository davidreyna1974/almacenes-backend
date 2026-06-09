package com.codigo2enter.almacenes.modules.inventory.repository;

import com.codigo2enter.almacenes.modules.inventory.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
     * Versión paginada del filtro por categoría.
     * La versión sin Pageable se conserva para uso en validaciones de servicio
     * (verificar si una categoría tiene productos antes de desactivarla).
     *
     * @param categoryId ID de la categoría por la que filtrar
     * @param pageable   parámetros de paginación y ordenación
     * @return página de productos activos de esa categoría
     */
    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

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
     * Versión paginada de findLowStockProducts.
     * Spring Data acepta Pageable como último parámetro en métodos @Query.
     *
     * Sort dentro de Pageable se aplica como ORDER BY adicional al WHERE ya definido.
     * El servicio pasa Sort.by("id").ascending() como criterio neutro.
     *
     * @param pageable parámetros de paginación y ordenación
     * @return página de productos activos con stock disponible en nivel crítico
     */
    @Query("SELECT p FROM Product p " +
           "WHERE (p.currentStock - p.reservedStock) <= p.minimumStock " +
           "AND p.active = true")
    Page<Product> findLowStockProducts(Pageable pageable);

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

    /**
     * Búsqueda combinada de productos con filtros opcionales.
     *
     * Siempre filtra active = true (catálogo operativo).
     * Cada parámetro es opcional: si llega null, la condición se omite
     * gracias al patrón (:param IS NULL OR ...).
     *
     * search    → búsqueda parcial (LIKE %term%) en sku o name,
     *             insensible a mayúsculas Y acentos usando f_unaccent()
     *             (PostgreSQL extension 'unaccent', función wrapper inmutable
     *             creada en almacen_db para permitir su uso en índices).
     * categoryId → filtra por categoría exacta.
     * status     → filtra por estado exacto (AVAILABLE, DISCONTINUED, etc.).
     * supplierId → filtra por proveedor exacto.
     *
     * Query nativa (nativeQuery=true) porque JPQL no expone funciones
     * PostgreSQL personalizadas. El countQuery separado evita el JOIN
     * innecesario de la proyección completa al calcular totalPages.
     */
    @Query(value =
           "SELECT p.* FROM products p " +
           "WHERE p.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(p.sku))  LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(p.name)) LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%')) " +
           "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
           "AND (:status IS NULL OR p.status = CAST(:status AS text)) " +
           "AND (:supplierId IS NULL OR p.supplier_id = :supplierId) " +
           "ORDER BY p.name ASC",
           countQuery =
           "SELECT COUNT(*) FROM products p " +
           "WHERE p.active = true " +
           "AND (:search IS NULL OR (" +
           "     f_unaccent(lower(p.sku))  LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%' " +
           "  OR f_unaccent(lower(p.name)) LIKE '%' || f_unaccent(lower(CAST(:search AS text))) || '%')) " +
           "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
           "AND (:status IS NULL OR p.status = CAST(:status AS text)) " +
           "AND (:supplierId IS NULL OR p.supplier_id = :supplierId)",
           nativeQuery = true)
    Page<Product> searchProducts(@Param("search") String search,
                                  @Param("categoryId") Long categoryId,
                                  @Param("status") String status,
                                  @Param("supplierId") Long supplierId,
                                  Pageable pageable);

    // ── QUERIES ANALÍTICAS PARA EL MÓDULO REPORTS ──────────────────────────

    /**
     * Valuación del inventario agrupada por categoría.
     * Retorna Object[] {categoryId (Long), categoryName (String), productCount (Long),
     * totalValue (BigDecimal)} para cada categoría con al menos un producto activo.
     *
     * COALESCE(..., 0) garantiza que la suma sea 0 en lugar de null cuando una
     * categoría tiene productos activos con stock cero.
     * ORDER BY SUM DESC muestra primero las categorías con mayor capital inmovilizado.
     *
     * @return lista de Object[]{Long, String, Long, BigDecimal} ordenada por valor DESC
     */
    @Query("SELECT p.category.id, p.category.name, COUNT(p), COALESCE(SUM(p.currentStock * p.unitCost), 0) " +
           "FROM Product p WHERE p.active = true GROUP BY p.category.id, p.category.name " +
           "ORDER BY SUM(p.currentStock * p.unitCost) DESC")
    List<Object[]> inventoryValueByCategory();

    /**
     * Valor total del inventario activo: Σ(currentStock × unitCost) para todos
     * los productos con active = true.
     * COALESCE garantiza BigDecimal.ZERO cuando no hay productos activos.
     *
     * @return valor total del inventario, nunca null
     */
    @Query("SELECT COALESCE(SUM(p.currentStock * p.unitCost), 0) FROM Product p WHERE p.active = true")
    BigDecimal totalInventoryValue();
}
