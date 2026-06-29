package com.codigo2enter.almacenes.modules.reports.dto.management;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Producto con mejor desempeño en ventas, ordenado por revenue descendente.
 *
 * Combina datos de ventas (revenue, COGS, margen) con información de catálogo
 * (SKU, nombre, categoría) para que el analista pueda identificar los productos
 * estrella sin necesidad de cruzar información adicional.
 *
 * El campo rank es la posición en el ranking (1 = mayor revenue), asignado
 * por el servicio según la posición en la lista ordenada que retorna la query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductDTO {

    /** Posición en el ranking de revenue descendente (1 = mayor revenue). */
    private Integer rank;

    /** ID del producto en la tabla products. */
    private Long productId;

    /** Stock Keeping Unit del producto. */
    private String sku;

    /** Nombre del producto para mostrar en la UI. */
    private String name;

    /** Nombre de la categoría del producto — aplana la relación @ManyToOne. */
    private String categoryName;

    /**
     * Unidades totales vendidas en órdenes DELIVERED en el período.
     * Útil para comparar volumen vs. revenue (productos baratos de alto volumen
     * vs. productos caros de bajo volumen).
     */
    private Long totalQuantitySold;

    /**
     * Suma de subtotales de ventas DELIVERED del producto en el período.
     * Es el criterio primario de ordenamiento del ranking.
     */
    private BigDecimal totalRevenue;

    /**
     * Costo total de los bienes vendidos del producto:
     * Σ(quantity × unitCost) de sus detalles en órdenes DELIVERED.
     */
    private BigDecimal totalCogs;

    /**
     * Margen bruto del producto = totalRevenue - totalCogs.
     */
    private BigDecimal grossMargin;

    /**
     * Porcentaje de margen bruto = (grossMargin / totalRevenue) × 100.
     * Null cuando totalRevenue == 0.
     */
    private BigDecimal grossMarginPct;
}
