package com.codigo2enter.almacenes.modules.reports.dto.executive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Valuación del inventario para una categoría específica.
 *
 * Usado como elemento de la lista en InventoryValuationDTO para mostrar
 * la distribución del capital por categoría. El campo pct permite al frontend
 * renderizar gráficas de tipo "pie chart" o barras de progreso directamente
 * sin cálculos adicionales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryValuationCategoryDTO {

    /** ID de la categoría en la tabla categories. */
    private Long categoryId;

    /** Nombre descriptivo de la categoría para mostrar en la UI. */
    private String categoryName;

    /**
     * Número de productos activos en esta categoría.
     * Útil para contexto: una categoría con valor alto pero pocos productos
     * indica artículos de alto costo unitario.
     */
    private Long productCount;

    /**
     * Valor total del inventario de esta categoría:
     * Σ(currentStock × unitCost) para todos los productos activos de la categoría.
     */
    private BigDecimal categoryValue;

    /**
     * Porcentaje que esta categoría representa del inventario total:
     * (categoryValue / totalValue) × 100.
     * Cero cuando totalValue == 0. Redondeado a 2 decimales con HALF_UP.
     */
    private BigDecimal pct;
}
