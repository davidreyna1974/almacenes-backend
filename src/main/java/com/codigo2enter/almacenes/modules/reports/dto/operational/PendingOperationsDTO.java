package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reporte consolidado de operaciones pendientes (compras y ventas simultáneamente).
 *
 * Permite al operador o gerente ver de un vistazo todos los compromisos activos:
 * compras que aún no se han recibido y ventas que aún no se han entregado.
 * Ambas listas incluyen órdenes en estado PENDING y APPROVED.
 *
 * Los contadores totalPendingPurchases y totalPendingSales son redundantes con
 * el tamaño de las listas pero evitan que el frontend necesite calcularlos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOperationsDTO {

    /**
     * Órdenes de compra en estado PENDING o APPROVED, ordenadas por createdAt DESC.
     * No incluye órdenes RECEIVED (completadas) ni CANCELLED (terminadas).
     */
    private List<PendingOrderSummaryDTO> pendingPurchaseOrders;

    /**
     * Órdenes de venta en estado PENDING o APPROVED, ordenadas por createdAt DESC.
     * No incluye órdenes DELIVERED (completadas) ni CANCELLED (terminadas).
     */
    private List<PendingOrderSummaryDTO> pendingSaleOrders;

    /**
     * Total de órdenes de compra pendientes = pendingPurchaseOrders.size().
     * Facilita mostrar badges o contadores en el dashboard sin recalcular.
     */
    private Integer totalPendingPurchases;

    /**
     * Total de órdenes de venta pendientes = pendingSaleOrders.size().
     */
    private Integer totalPendingSales;
}
