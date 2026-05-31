package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumen de una orden pendiente (de compra o de venta).
 *
 * Estructura unificada para representar tanto PurchaseOrder como SaleOrder en el
 * reporte de operaciones pendientes. El campo counterpartName adapta su semántica
 * según el tipo de orden:
 *   - Para órdenes de compra: nombre del proveedor (companyName)
 *   - Para órdenes de venta: nombre del cliente (name)
 *
 * Este diseño evita dos DTOs separados (uno por tipo de orden) y simplifica
 * el frontend que renderiza ambas listas con el mismo componente de tabla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOrderSummaryDTO {

    /** ID de la orden en su tabla correspondiente. */
    private Long orderId;

    /**
     * Número de orden en formato OC-YYYY-NNNN (compras) u OV-YYYY-NNNN (ventas).
     * Referencia visible en documentos físicos o comunicaciones con proveedor/cliente.
     */
    private String orderNumber;

    /**
     * Estado actual de la orden como String: "PENDING" o "APPROVED".
     * PENDING indica que aún puede editarse; APPROVED está comprometida.
     */
    private String status;

    /**
     * Nombre de la contraparte de la orden:
     *   - Compras: supplier.companyName
     *   - Ventas: client.name
     */
    private String counterpartName;

    /** Fecha de creación de la orden. */
    private LocalDateTime createdAt;

    /** Importe total de la orden (suma de subtotales de detalles). */
    private BigDecimal totalAmount;

    /**
     * Número de líneas de detalle en la orden.
     * Indica la complejidad de la operación (muchos productos vs. pocos).
     */
    private Integer detailCount;
}
