package com.codigo2enter.almacenes.modules.sales.repository;

import com.codigo2enter.almacenes.modules.sales.model.SaleOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de persistencia para la entidad SaleOrderDetail.
 *
 * Dos métodos clave de seguridad:
 *
 * existsBySaleOrderIdAndProductId — evita que el mismo producto aparezca dos
 * veces en una misma orden. Si el cliente quiere cambiar la cantidad, debe usar
 * updateDetail, no agregar un segundo detalle del mismo producto.
 *
 * findByIdAndSaleOrderId — valida que el detalle que se quiere modificar o
 * eliminar pertenece efectivamente a la orden indicada en la URL, previniendo
 * accesos cruzados entre órdenes distintas.
 *
 * Adicionalmente, contiene queries analíticas usadas por el módulo reports para
 * calcular revenue, COGS y cantidades vendidas en un período. Todas las queries
 * filtran exclusivamente órdenes con status = DELIVERED para que solo el dinero
 * efectivamente cobrado figure en los reportes financieros.
 */
@Repository
public interface SaleOrderDetailRepository extends JpaRepository<SaleOrderDetail, Long> {

    /**
     * Verifica si ya existe un detalle con ese producto en la orden indicada.
     * Usado en addDetail() para prevenir duplicados dentro de la misma orden.
     */
    boolean existsBySaleOrderIdAndProductId(Long saleOrderId, Long productId);

    /**
     * Busca un detalle por su ID validando que pertenezca a la orden indicada.
     * Previene que un cliente pueda modificar detalles de otras órdenes
     * manipulando el detailId en la URL.
     */
    Optional<SaleOrderDetail> findByIdAndSaleOrderId(Long id, Long saleOrderId);

    // ── QUERIES ANALÍTICAS PARA EL MÓDULO REPORTS ──────────────────────────

    /**
     * Suma de subtotales de ventas DELIVERED en el período indicado.
     * Es el revenue "top line" del negocio: dinero efectivamente cobrado.
     * COALESCE(..., 0) garantiza BigDecimal.ZERO en lugar de null cuando no hay datos.
     *
     * @param from inicio del período (inclusivo, a las 00:00:00)
     * @param to   fin del período (exclusivo, día siguiente a las 00:00:00)
     * @return suma de subtotales, nunca null
     */
    @Query("SELECT COALESCE(SUM(sod.subtotal), 0) FROM SaleOrderDetail sod " +
           "JOIN sod.saleOrder so WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to")
    BigDecimal sumRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Suma del costo de bienes vendidos (COGS) en el período: Σ(quantity × unitCost).
     * unitCost es el costo histórico capturado al crear el detalle, no el costo
     * actual del producto — esto preserva la integridad del análisis financiero histórico.
     * COALESCE(..., 0) garantiza BigDecimal.ZERO cuando no hay datos.
     *
     * @param from inicio del período (inclusivo)
     * @param to   fin del período (exclusivo)
     * @return suma de COGS, nunca null
     */
    @Query("SELECT COALESCE(SUM(sod.quantity * sod.unitCost), 0) FROM SaleOrderDetail sod " +
           "JOIN sod.saleOrder so WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to")
    BigDecimal sumCogs(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Revenue por producto en el período, ordenado por revenue descendente.
     * Retorna Object[] {productId (Long), revenue (BigDecimal)} por producto.
     * El servicio usa este resultado para construir el ranking de top products y el ABC.
     *
     * ORDER BY SUM DESC garantiza que el producto con mayor revenue aparezca primero,
     * facilitando la asignación de rank en el servicio sin re-ordenar.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return lista de Object[]{Long productId, BigDecimal revenue} ordenada DESC
     */
    @Query("SELECT sod.product.id, COALESCE(SUM(sod.subtotal), 0) FROM SaleOrderDetail sod " +
           "JOIN sod.saleOrder so WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to " +
           "GROUP BY sod.product.id ORDER BY SUM(sod.subtotal) DESC")
    List<Object[]> revenueByProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * COGS por producto en el período: Σ(quantity × unitCost) agrupado por productId.
     * Retorna Object[] {productId (Long), cogs (BigDecimal)}.
     * No tiene ORDER BY — el servicio lo cruza con revenueByProduct que ya viene ordenado.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return lista de Object[]{Long productId, BigDecimal cogs}
     */
    @Query("SELECT sod.product.id, COALESCE(SUM(sod.quantity * sod.unitCost), 0) FROM SaleOrderDetail sod " +
           "JOIN sod.saleOrder so WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to " +
           "GROUP BY sod.product.id")
    List<Object[]> cogsByProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Cantidad vendida por producto en el período: Σ(quantity) agrupado por productId.
     * Retorna Object[] {productId (Long), totalQty (Long)}.
     * Usado en TopProductDTO para mostrar volumen de ventas junto al revenue.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return lista de Object[]{Long productId, Long totalQuantity}
     */
    @Query("SELECT sod.product.id, COALESCE(SUM(sod.quantity), 0) FROM SaleOrderDetail sod " +
           "JOIN sod.saleOrder so WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to " +
           "GROUP BY sod.product.id")
    List<Object[]> quantitySoldByProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Cuenta de órdenes DELIVERED distintas en el período.
     * Usada para calcular el ticket promedio = revenue / countDeliveredOrders.
     * COUNT(DISTINCT so.id) evita contar la misma orden múltiples veces si tiene
     * varios detalles que pasan el filtro.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return número de órdenes DELIVERED en el período
     */
    @Query("SELECT COUNT(DISTINCT so.id) FROM SaleOrderDetail sod JOIN sod.saleOrder so " +
           "WHERE so.status = com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus.DELIVERED " +
           "AND so.deliveredAt >= :from AND so.deliveredAt < :to")
    Long countDeliveredOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
