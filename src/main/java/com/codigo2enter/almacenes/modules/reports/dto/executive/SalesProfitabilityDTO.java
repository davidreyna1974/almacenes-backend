package com.codigo2enter.almacenes.modules.reports.dto.executive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Análisis de rentabilidad de ventas para un período explícito.
 *
 * A diferencia de ExecutiveDashboardDTO, el período (from/to) es obligatorio —
 * el servicio lanza excepción si se omite o si from > to. Esto fuerza al
 * usuario a especificar el contexto temporal del análisis.
 *
 * Criterio de éxito: avgTicket es null cuando deliveredOrderCount == 0,
 * evitando división por cero y comunicando que no hay datos suficientes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesProfitabilityDTO {

    /** Inicio del período analizado (inclusivo, a las 00:00:00). */
    private LocalDate from;

    /** Fin del período analizado (inclusivo, hasta las 23:59:59). */
    private LocalDate to;

    /**
     * Suma de subtotales de ventas DELIVERED en el período.
     * Equivale a totalRevenue en el dashboard pero acotado al período.
     */
    private BigDecimal totalRevenue;

    /**
     * Costo total de los bienes vendidos en el período.
     * Suma de quantity × unitCost de detalles de órdenes DELIVERED.
     */
    private BigDecimal totalCogs;

    /**
     * Ganancia bruta = totalRevenue - totalCogs.
     */
    private BigDecimal grossMargin;

    /**
     * Porcentaje de margen bruto = (grossMargin / totalRevenue) × 100.
     * Null cuando totalRevenue == 0.
     */
    private BigDecimal grossMarginPct;

    /**
     * Número de órdenes con status = DELIVERED en el período.
     * Base para calcular el ticket promedio.
     */
    private Long deliveredOrderCount;

    /**
     * Ticket promedio = totalRevenue / deliveredOrderCount.
     * Null cuando deliveredOrderCount == 0. Indica el valor medio por operación.
     */
    private BigDecimal avgTicket;

    /** Momento en que se generó este reporte. */
    private LocalDateTime generatedAt;
}
