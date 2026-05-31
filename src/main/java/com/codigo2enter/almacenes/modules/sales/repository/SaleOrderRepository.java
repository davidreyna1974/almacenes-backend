package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    // ── QUERIES ANALÍTICAS PARA EL MÓDULO REPORTS ──────────────────────────

    /**
     * Cuenta órdenes de venta en estado PENDING o APPROVED.
     * Representa compromisos activos con clientes que no han sido entregados ni cancelados.
     * Usado en el dashboard ejecutivo para el KPI de operaciones pendientes de venta.
     *
     * @return número de órdenes activas (PENDING + APPROVED)
     */
    @Query("SELECT COUNT(so) FROM SaleOrder so WHERE so.status IN " +
           "(com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.PENDING, " +
           " com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.APPROVED)")
    Long countPendingAndApproved();

    /**
     * Revenue de ventas DELIVERED agrupado por período.
     *
     * Usa una query nativa de PostgreSQL con TO_CHAR para formatear la fecha.
     * Se usa nativeQuery=true porque JPQL no garantiza que el parámetro :format
     * sea tratado como literal en GROUP BY/ORDER BY — PostgreSQL requiere que la
     * expresión en GROUP BY y SELECT sea idéntica, lo que falla cuando el parámetro
     * es enlazado como JDBC bind variable.
     *
     * La query nativa pasa :format como texto literal en la expresión TO_CHAR,
     * permitiendo que PostgreSQL reconozca la misma expresión en SELECT, GROUP BY y ORDER BY.
     *
     * Retorna Object[] {period (String), revenue (BigDecimal), orderCount (Long)}.
     * ORDER BY period garantiza orden cronológico en la respuesta.
     *
     * @param from   inicio del período
     * @param to     fin del período
     * @param format formato de TO_CHAR: 'YYYY-MM-DD' (día), 'IYYY-IW' (semana ISO), 'YYYY-MM' (mes)
     * @return lista de Object[]{String, BigDecimal, Long} ordenada cronológicamente
     */
    @Query(value = "SELECT period, COALESCE(SUM(total_amount), 0) AS revenue, COUNT(id) AS order_count " +
                   "FROM (SELECT total_amount, id, TO_CHAR(delivered_at, :format) AS period " +
                   "      FROM sale_orders " +
                   "      WHERE status = 'DELIVERED' AND delivered_at >= :from AND delivered_at < :to) sub " +
                   "GROUP BY period ORDER BY period",
           nativeQuery = true)
    List<Object[]> revenueByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   @Param("format") String format);

    /**
     * Retorna órdenes de venta en estado PENDING o APPROVED para el reporte operativo.
     * Ordenadas por createdAt DESC para mostrar las más recientes primero.
     * No incluye DELIVERED ni CANCELLED — solo las que requieren acción.
     *
     * @return lista de órdenes activas, la más reciente primero
     */
    @Query("SELECT so FROM SaleOrder so WHERE so.status IN " +
           "(com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.PENDING, " +
           " com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.APPROVED) " +
           "ORDER BY so.createdAt DESC")
    List<SaleOrder> findPendingAndApproved();
}
