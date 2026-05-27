package com.codigo2enter.almacenes.modules.purchases.repository;

import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad PurchaseOrder.
 *
 * Combina query methods derivados para consultas simples con @Query JPQL
 * para condiciones que no pueden expresarse con la nomenclatura de Spring Data:
 *   - findActiveOrdersBySupplier: filtra por dos valores de enum en la misma columna
 *   - findByProductId: navega la relación @OneToMany details para filtrar por producto
 *   - countByYear: compara un campo de fecha extraído con un parámetro entero
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /**
     * Busca una orden por su número único en formato OC-YYYY-NNNN.
     * Usado en generateOrderNumber para verificar que el candidato generado
     * no esté ya registrado antes de asignarlo a la nueva orden.
     *
     * @param orderNumber número de orden a buscar
     * @return Optional con la orden si existe, vacío si no
     */
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    /**
     * Recupera todas las órdenes que se encuentran en el estado indicado.
     * El servicio convierte el String recibido del cliente al enum antes de
     * invocar este método, con manejo de IllegalArgumentException para
     * devolver un mensaje de error claro si el valor es inválido.
     *
     * @param status estado del enum por el que filtrar
     * @return lista de órdenes en ese estado, vacía si no hay ninguna
     */
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Recupera todas las órdenes de un proveedor específico.
     * Spring Data navega la relación @ManyToOne supplier para generar
     * el JOIN y el filtro por supplier.id automáticamente.
     *
     * @param supplierId ID del proveedor
     * @return lista de órdenes del proveedor, vacía si no tiene ninguna
     */
    List<PurchaseOrder> findBySupplierId(Long supplierId);

    /**
     * Recupera las órdenes de un proveedor filtradas por estado.
     * Combinación de los dos filtros anteriores para consultas más específicas.
     *
     * @param supplierId ID del proveedor
     * @param status     estado del enum por el que filtrar
     * @return lista de órdenes del proveedor en ese estado
     */
    List<PurchaseOrder> findBySupplierIdAndStatus(Long supplierId, PurchaseOrderStatus status);

    /**
     * Recupera las órdenes creadas por un usuario específico.
     * Spring Data navega la relación @ManyToOne createdBy para generar
     * el JOIN y el filtro por createdBy.id automáticamente.
     *
     * @param userId ID del usuario creador
     * @return lista de órdenes creadas por ese usuario
     */
    List<PurchaseOrder> findByCreatedById(Long userId);

    /**
     * Recupera las órdenes activas (PENDING o APPROVED) de un proveedor.
     * Usada en deactivateSupplier para bloquear la baja del proveedor cuando
     * tiene órdenes que aún no han sido recibidas o canceladas.
     *
     * Se usa @Query JPQL porque el filtro por múltiples valores de enum
     * (IN con valores literales) no puede expresarse con query methods derivados.
     *
     * @param supplierId ID del proveedor a verificar
     * @return lista de órdenes activas del proveedor
     */
    @Query("SELECT p FROM PurchaseOrder p " +
           "WHERE p.supplier.id = :supplierId " +
           "AND p.status IN (com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus.PENDING, " +
           "                 com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus.APPROVED)")
    List<PurchaseOrder> findActiveOrdersBySupplier(@Param("supplierId") Long supplierId);

    /**
     * Recupera todas las órdenes que contienen un producto específico,
     * navegando la relación @OneToMany details de cada orden.
     *
     * DISTINCT elimina duplicados — aunque un producto no debería aparecer
     * más de una vez en la misma orden (validado por existsByPurchaseOrderIdAndProductId),
     * DISTINCT garantiza resultados únicos ante cualquier caso edge.
     *
     * Se requiere @Query porque Spring Data no puede derivar automáticamente
     * un JOIN a través de una colección (@OneToMany) con filtro en un campo
     * de la entidad relacionada.
     *
     * @param productId ID del producto a buscar en los detalles
     * @return lista de órdenes que contienen ese producto
     */
    @Query("SELECT DISTINCT p FROM PurchaseOrder p " +
           "JOIN p.details d " +
           "WHERE d.product.id = :productId")
    List<PurchaseOrder> findByProductId(@Param("productId") Long productId);

    /**
     * Cuenta el número de órdenes creadas en un año específico.
     * Usada por generateOrderNumber() para calcular la secuencia anual:
     *   count + 1 determina el siguiente número de orden del año.
     *
     * El contador se reinicia cada año — OC-2026-0001 es el primero de 2026,
     * OC-2027-0001 es el primero de 2027, independientemente del volumen
     * del año anterior.
     *
     * Se requiere @Query porque YEAR() sobre un campo LocalDateTime no puede
     * expresarse como query method derivado de Spring Data.
     *
     * @param year año por el que filtrar (ej. 2026)
     * @return número de órdenes creadas en ese año
     */
    @Query("SELECT COUNT(p) FROM PurchaseOrder p " +
           "WHERE YEAR(p.createdAt) = :year")
    Long countByYear(@Param("year") int year);
}
