package com.codigo2enter.almacenes.modules.reports.dto.management;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Rotación de inventario para un producto en un período.
 *
 * La rotación de inventario mide cuántas veces el inventario de un producto
 * fue "vendido" durante el período. Una rotación alta indica que el producto
 * se mueve rápidamente (poco tiempo en almacén); una rotación baja puede
 * indicar sobrestock o producto de movimiento lento.
 *
 * Fórmula: turnoverRate = cogsInPeriod / currentInventoryValue
 * donde currentInventoryValue = currentStock × unitCost (snapshot actual).
 *
 * Criterio de éxito: turnoverRate es null cuando currentInventoryValue == 0
 * (sin inventario no hay rotación calculable).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTurnoverItemDTO {

    /** ID del producto en la tabla products. */
    private Long productId;

    /** Stock Keeping Unit del producto. */
    private String sku;

    /** Nombre del producto para mostrar en la UI. */
    private String name;

    /** Nombre de la categoría del producto — aplana la relación @ManyToOne. */
    private String categoryName;

    /**
     * Costo de los bienes vendidos del producto en el período:
     * Σ(quantity × unitCost) de detalles en ventas DELIVERED.
     * Es el numerador de la tasa de rotación.
     */
    private BigDecimal cogsInPeriod;

    /**
     * Valor del inventario actual del producto: currentStock × unitCost.
     * Snapshot en tiempo real — es el denominador de la tasa de rotación.
     */
    private BigDecimal currentInventoryValue;

    /**
     * Tasa de rotación = cogsInPeriod / currentInventoryValue.
     * Null cuando currentInventoryValue == 0.
     * Un valor de 4 significa que el inventario se renovó 4 veces en el período.
     */
    private BigDecimal turnoverRate;

    /**
     * Interpretación textual de la tasa de rotación para facilitar lectura:
     *   "Alta"      — turnoverRate > 4
     *   "Media"     — turnoverRate >= 1
     *   "Baja"      — turnoverRate < 1
     *   "Sin datos" — turnoverRate es null
     */
    private String interpretation;
}
