package com.codigo2enter.almacenes.modules.reports.dto.management;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Clasificación ABC de un producto según el principio de Pareto aplicado a revenue.
 *
 * La clasificación ABC identifica qué productos generan el 80% del revenue (A),
 * el siguiente 15% (B), y el resto (C). Permite priorizar el control de inventario:
 * los productos A requieren más atención que los C.
 *
 * Criterio de clasificación:
 *   A: cumulativePct <= 80%  (productos que acumulan el 80% del revenue total)
 *   B: cumulativePct <= 95%  (siguiente 15%)
 *   C: el resto              (último 5%)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbcProductDTO {

    /**
     * Clasificación del producto: "A", "B" o "C".
     * A = alto impacto en revenue, C = bajo impacto.
     */
    private String classification;

    /** ID del producto en la tabla products. */
    private Long productId;

    /** Stock Keeping Unit del producto. */
    private String sku;

    /** Nombre del producto para mostrar en la UI. */
    private String name;

    /**
     * Revenue del producto en el período: suma de subtotales de ventas DELIVERED.
     * Es el valor base para el cálculo de porcentajes.
     */
    private BigDecimal totalRevenue;

    /**
     * Porcentaje individual del producto sobre el revenue total del período:
     * (totalRevenue / sumaTotal) × 100.
     * Indica la contribución relativa de este producto al total de ventas.
     */
    private BigDecimal revenuePct;

    /**
     * Porcentaje acumulado hasta este producto (ordenado por revenue DESC).
     * Es el criterio real de clasificación: cuando cumulativePct supera 80%
     * se pasa de A a B, cuando supera 95% se pasa de B a C.
     */
    private BigDecimal cumulativePct;
}
