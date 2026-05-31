package com.codigo2enter.almacenes.modules.reports.dto.executive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resumen ejecutivo de KPIs financieros del negocio.
 *
 * Consolida en una sola respuesta los indicadores más relevantes para gerencia:
 * rentabilidad bruta, valor del inventario y volumen de operaciones pendientes.
 * El período es opcional — si no se especifica, se calcula desde el inicio del tiempo.
 *
 * Criterio de éxito: grossMarginPct es null cuando revenue == 0, evitando división
 * por cero y comunicando al usuario que no hay datos suficientes para el cálculo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveDashboardDTO {

    /**
     * Suma de subtotales de todas las ventas entregadas (status = DELIVERED)
     * en el período indicado. Es el "top line" financiero del negocio.
     */
    private BigDecimal totalRevenue;

    /**
     * Costo total de los bienes vendidos (COGS): suma de quantity × unitCost
     * de los detalles de ventas DELIVERED en el período. unitCost es el costo
     * histórico capturado al crear el detalle — nunca el costo actual del producto.
     */
    private BigDecimal totalCogs;

    /**
     * Margen bruto = totalRevenue - totalCogs.
     * Representa la ganancia antes de gastos operativos.
     */
    private BigDecimal grossMargin;

    /**
     * Porcentaje de margen bruto = (grossMargin / totalRevenue) × 100.
     * Null cuando totalRevenue == 0 para evitar división por cero.
     * Redondeado a 2 decimales con HALF_UP.
     */
    private BigDecimal grossMarginPct;

    /**
     * Valor del inventario activo = suma de currentStock × unitCost por producto activo.
     * Snapshot en tiempo real del capital inmovilizado en almacén.
     */
    private BigDecimal inventoryValue;

    /**
     * Número de órdenes de compra en estado PENDING o APPROVED.
     * Indica compromisos pendientes con proveedores.
     */
    private Long pendingPurchaseOrders;

    /**
     * Número de órdenes de venta en estado PENDING o APPROVED.
     * Indica compromisos pendientes con clientes.
     */
    private Long pendingSaleOrders;

    /**
     * Momento en que se generó este reporte.
     * Permite al frontend mostrar la vigencia de los datos sin re-fetch inmediato.
     */
    private LocalDateTime generatedAt;
}
