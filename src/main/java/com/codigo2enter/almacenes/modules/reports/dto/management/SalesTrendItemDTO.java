package com.codigo2enter.almacenes.modules.reports.dto.management;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Punto de datos de la tendencia de ventas para un período agrupado.
 *
 * El campo period es la representación textual del agrupamiento:
 *   - groupBy=DAY   → "2026-01-15"  (formato YYYY-MM-DD)
 *   - groupBy=WEEK  → "2026-03"     (formato IYYY-IW, semana ISO)
 *   - groupBy=MONTH → "2026-01"     (formato YYYY-MM)
 *
 * La lista de SalesTrendItemDTO enviada al frontend está ordenada por period ASC,
 * lo que permite renderizar gráficas de línea cronológicamente sin re-ordenar.
 *
 * Criterio de éxito: avgTicket es null cuando orderCount == 0, indicando
 * que no hubo ventas en ese período y evitando división por cero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesTrendItemDTO {

    /**
     * Período agrupado como String según el formato del groupBy:
     * DAY → YYYY-MM-DD, WEEK → IYYY-IW, MONTH → YYYY-MM.
     */
    private String period;

    /**
     * Suma de totalAmount de órdenes DELIVERED en el período.
     * Es el revenue real cobrado (no el presupuestado o pendiente).
     */
    private BigDecimal revenue;

    /**
     * Número de órdenes DELIVERED en el período.
     * Indica el volumen de operaciones, complementario al revenue.
     */
    private Long orderCount;

    /**
     * Ticket promedio = revenue / orderCount.
     * Null cuando orderCount == 0.
     */
    private BigDecimal avgTicket;
}
