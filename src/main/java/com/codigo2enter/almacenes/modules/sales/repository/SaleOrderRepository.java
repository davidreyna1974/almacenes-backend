package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad SaleOrder.
 *
 * Combina query methods derivados para filtros simples con consultas JPQL
 * para casos que requieren JOIN, conteo por año o filtros multi-valor (IN).
 */
@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Long> {

    List<SaleOrder> findByStatus(SaleOrderStatus status);

    List<SaleOrder> findByClientId(Long clientId);

    List<SaleOrder> findByClientIdAndStatus(Long clientId, SaleOrderStatus status);

    Optional<SaleOrder> findByOrderNumber(String orderNumber);

    /**
     * Cuenta órdenes creadas en el año indicado para generar el correlativo
     * del número de orden (OV-YYYY-NNNN). El bucle do-while en el servicio
     * protege contra colisiones en alta concurrencia.
     */
    @Query("SELECT COUNT(so) FROM SaleOrder so WHERE YEAR(so.createdAt) = :year")
    long countByYear(@Param("year") int year);

    /**
     * Retorna todas las órdenes que contienen un producto específico en sus
     * detalles. Requiere JPQL con JOIN porque la condición cruza dos entidades
     * (SaleOrder → SaleOrderDetail → Product), algo que los query methods
     * derivados no pueden expresar directamente.
     */
    @Query("SELECT DISTINCT so FROM SaleOrder so JOIN so.details d " +
           "WHERE d.product.id = :productId ORDER BY so.createdAt DESC")
    List<SaleOrder> findByProductId(@Param("productId") Long productId);

    /**
     * Retorna órdenes que contienen un producto específico filtradas por status.
     * Usado para consultar, por ejemplo, qué órdenes APPROVED tienen reservado
     * un producto concreto.
     */
    @Query("SELECT DISTINCT so FROM SaleOrder so JOIN so.details d " +
           "WHERE d.product.id = :productId AND so.status = :status " +
           "ORDER BY so.approvedAt DESC")
    List<SaleOrder> findByProductIdAndStatus(
            @Param("productId") Long productId,
            @Param("status") SaleOrderStatus status);

    /**
     * Retorna órdenes en PENDING o APPROVED para un cliente.
     * Usado por ClientServiceImpl para verificar si el cliente tiene órdenes
     * activas antes de permitir su desactivación (soft delete).
     *
     * Se usa la FQN del enum en la consulta JPQL porque el IN con literales
     * de enum requiere la ruta completa del tipo para que el parser JPQL
     * pueda resolverlos sin ambigüedad.
     */
    @Query("SELECT so FROM SaleOrder so WHERE so.client.id = :clientId " +
           "AND so.status IN (" +
           "com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.PENDING, " +
           "com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.APPROVED)")
    List<SaleOrder> findActiveOrdersByClient(@Param("clientId") Long clientId);

    /**
     * Retorna las órdenes APPROVED más antiguas según approvedAt.
     * Reservado para futuras funcionalidades (priorización de entregas FIFO).
     * El parámetro Pageable permite limitar el resultado sin traer toda la tabla.
     */
    @Query("SELECT so FROM SaleOrder so WHERE so.status = " +
           "com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.APPROVED " +
           "ORDER BY so.approvedAt ASC")
    List<SaleOrder> findOldestApprovedOrders(Pageable pageable);
}
