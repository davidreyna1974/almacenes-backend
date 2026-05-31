package com.codigo2enter.almacenes.modules.reports.dto.management;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumen de compras realizadas a un proveedor en un período.
 *
 * Solo considera órdenes de compra en estado RECEIVED — las PENDING, APPROVED
 * y CANCELLED no representan dinero efectivamente desembolsado. El campo
 * lastOrderDate es la fecha de recepción más reciente, útil para identificar
 * proveedores inactivos.
 *
 * Criterio de éxito: avgOrderAmount es BigDecimal.ZERO (no null) cuando
 * orderCount == 0, para consistencia con el tipo declarado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseBySupplierDTO {

    /** ID del proveedor en la tabla suppliers. */
    private Long supplierId;

    /** Nombre comercial del proveedor (companyName). */
    private String supplierName;

    /** RFC del proveedor, para referencia fiscal. */
    private String rfc;

    /**
     * Número de órdenes de compra RECEIVED del proveedor en el período.
     * Base para calcular avgOrderAmount.
     */
    private Long orderCount;

    /**
     * Suma de totalAmount de las órdenes RECEIVED del proveedor en el período.
     * Indica el volumen total de compras realizadas a este proveedor.
     */
    private BigDecimal totalAmount;

    /**
     * Importe promedio por orden = totalAmount / orderCount.
     * BigDecimal.ZERO cuando orderCount == 0.
     */
    private BigDecimal avgOrderAmount;

    /**
     * Fecha de la última recepción (receivedAt) del proveedor en el período.
     * Null si no hay órdenes RECEIVED en el período.
     */
    private LocalDateTime lastOrderDate;
}
