package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Producto con stock disponible por debajo del mínimo configurado.
 *
 * La alerta se basa en availableStock (= currentStock - reservedStock), no en
 * currentStock bruto — un producto puede tener stock físico pero todo comprometido
 * con órdenes aprobadas, dejando cero disponible para nuevas ventas.
 *
 * El campo deficit indica cuántas unidades faltan para alcanzar el mínimo.
 * Puede ser negativo si reservedStock es muy alto (el déficit real es mayor
 * de lo que indica el stock físico).
 *
 * La lista se ordena por deficit DESC para mostrar primero los productos más críticos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockReportItemDTO {

    /** ID del producto en la tabla products. */
    private Long productId;

    /** Stock Keeping Unit del producto. */
    private String sku;

    /** Nombre del producto para mostrar en la UI. */
    private String name;

    /** Nombre de la categoría — aplana la relación @ManyToOne. */
    private String categoryName;

    /**
     * Unidades físicas actuales en el almacén.
     * Puede ser mayor que minimumStock pero el producto aparece aquí
     * porque reservedStock deja availableStock <= minimumStock.
     */
    private Integer currentStock;

    /** Umbral mínimo configurado para este producto. */
    private Integer minimumStock;

    /**
     * Stock disponible para nuevas ventas = currentStock - reservedStock.
     * Es el valor real que determinó la inclusión en este reporte.
     */
    private Integer availableStock;

    /**
     * Unidades comprometidas con órdenes de venta APPROVED no entregadas.
     */
    private Integer reservedStock;

    /**
     * Unidades que faltan para alcanzar el mínimo = minimumStock - currentStock.
     * Positivo indica faltante real; negativo puede ocurrir cuando reservedStock
     * es la causa del bajo availableStock aunque currentStock > minimumStock.
     * Se ordena DESC para mostrar primero los más críticos.
     */
    private Integer deficit;
}
