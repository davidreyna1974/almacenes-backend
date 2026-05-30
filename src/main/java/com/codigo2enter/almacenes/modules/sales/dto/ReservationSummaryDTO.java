package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard ejecutivo de reservas activas.
 * Agrega en un solo objeto los indicadores clave de stock comprometido
 * para que el operador de almacén evalúe la situación de inventario de un vistazo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSummaryDTO {

    /** Número de productos distintos con reservedStock > 0. */
    private int totalProductsWithReservations;

    /** Suma de reservedStock de todos los productos con reservas activas. */
    private int totalReservedUnits;

    /**
     * Suma de (reservedStock × price) para cada producto con reservas.
     * Representa el valor monetario del inventario comprometido.
     */
    private BigDecimal totalReservedValue;

    /** Número de órdenes de venta actualmente en estado APPROVED. */
    private int totalApprovedOrders;
}
