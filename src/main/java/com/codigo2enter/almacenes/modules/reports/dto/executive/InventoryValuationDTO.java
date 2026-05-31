package com.codigo2enter.almacenes.modules.reports.dto.executive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte de valuación total del inventario, desglosado por categoría.
 *
 * Responde la pregunta: ¿cuánto capital está inmovilizado en inventario
 * y cómo se distribuye entre las categorías? Útil para decisiones de
 * reposición y gestión de capital de trabajo.
 *
 * La lista de categorías está ordenada de mayor a menor valor (la categoría
 * más costosa en inventario aparece primero), facilitando identificar dónde
 * está concentrado el capital.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryValuationDTO {

    /**
     * Suma total del inventario activo en todos los productos:
     * Σ(currentStock × unitCost) para todos los productos con active = true.
     */
    private BigDecimal totalValue;

    /**
     * Desglose del valor por categoría, ordenado de mayor a menor valor.
     * Cada elemento incluye el porcentaje que esa categoría representa del total.
     */
    private List<InventoryValuationCategoryDTO> categories;

    /**
     * Momento en que se generó este reporte.
     */
    private LocalDateTime generatedAt;
}
