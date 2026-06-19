package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.reports.dto.executive.ExecutiveDashboardDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.InventoryValuationCategoryDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.InventoryValuationDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.SalesProfitabilityDTO;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderDetailRepository;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de los informes ejecutivos de alto nivel.
 *
 * Todos los métodos son readOnly — el módulo reports nunca modifica datos.
 *
 * Dependencias:
 *   - SaleOrderDetailRepository: revenue, COGS, conteo de órdenes DELIVERED
 *   - ProductRepository: valuación del inventario
 *   - PurchaseOrderRepository: conteo de órdenes de compra activas
 *   - SaleOrderRepository: conteo de órdenes de venta activas
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExecutiveReportServiceImpl implements ExecutiveReportService {

    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SaleOrderRepository saleOrderRepository;

    /**
     * Dashboard ejecutivo con KPIs financieros del período.
     *
     * Flujo:
     *   1. Resolver fechas (null from → inicio del año en curso; null to → mañana)
     *   2. Ejecutar 4 queries (revenue, cogs, pendingPO, pendingSO) + inventoryValue
     *   3. Calcular grossMargin y grossMarginPct con protección contra división por cero
     *
     * Criterio de éxito: grossMarginPct es null cuando revenue == 0, no lanza NPE.
     * Para períodos sin ventas, grossMarginPct es null y grossMargin es 0.
     *
     * @param from inicio del período (null = inicio del tiempo)
     * @param to   fin del período (null = mañana)
     */
    @Override
    public ExecutiveDashboardDTO getExecutiveDashboard(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDate.now().withDayOfYear(1).atStartOfDay();
        LocalDateTime toDt   = to   != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        BigDecimal revenue  = saleOrderDetailRepository.sumRevenue(fromDt, toDt);
        BigDecimal cogs     = saleOrderDetailRepository.sumCogs(fromDt, toDt);
        BigDecimal margin   = revenue.subtract(cogs);
        BigDecimal marginPct = computeMarginPct(revenue, margin);
        BigDecimal invValue  = productRepository.totalInventoryValue();
        Long pendingPO = purchaseOrderRepository.countPendingAndApproved();
        Long pendingSO = saleOrderRepository.countPendingAndApproved();

        return ExecutiveDashboardDTO.builder()
                .totalRevenue(revenue)
                .totalCogs(cogs)
                .grossMargin(margin)
                .grossMarginPct(marginPct)
                .inventoryValue(invValue)
                .pendingPurchaseOrders(pendingPO)
                .pendingSaleOrders(pendingSO)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Valuación del inventario por categoría.
     *
     * Flujo:
     *   1. Consultar inventoryValueByCategory() → lista de Object[] con los datos de cada categoría
     *   2. Consultar totalInventoryValue() para el gran total
     *   3. Iterar para construir DTOs con porcentaje = categoryValue / totalValue × 100
     *
     * Criterio de éxito: pct es BigDecimal.ZERO (no null) cuando totalValue == 0,
     * y la lista categories puede estar vacía si no hay productos activos.
     */
    @Override
    public InventoryValuationDTO getInventoryValuation() {
        BigDecimal totalValue = productRepository.totalInventoryValue();
        List<Object[]> rows   = productRepository.inventoryValueByCategory();
        List<InventoryValuationCategoryDTO> categories = new ArrayList<>();

        for (Object[] row : rows) {
            Long       categoryId    = (Long)       row[0];
            String     categoryName  = (String)     row[1];
            Long       productCount  = (Long)       row[2];
            BigDecimal categoryValue = (BigDecimal) row[3];

            BigDecimal pct = BigDecimal.ZERO;
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                pct = categoryValue.divide(totalValue, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            categories.add(InventoryValuationCategoryDTO.builder()
                    .categoryId(categoryId)
                    .categoryName(categoryName)
                    .productCount(productCount)
                    .categoryValue(categoryValue)
                    .pct(pct)
                    .build());
        }

        return InventoryValuationDTO.builder()
                .totalValue(totalValue)
                .categories(categories)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Rentabilidad de ventas en el período explícito.
     *
     * El período es obligatorio — a diferencia del dashboard, este reporte
     * no tiene valores por defecto. La razón es que "rentabilidad sin período"
     * es ambigua: ¿all-time? ¿último mes? El analista debe ser explícito.
     *
     * Validaciones:
     *   - from y to no pueden ser null
     *   - from no puede ser posterior a to
     *
     * Criterio de éxito: avgTicket es null cuando no hay órdenes entregadas,
     * evitando división por cero y comunicando que el dato no es calculable.
     *
     * @throws RuntimeException si from o to son null, o si from > to
     */
    @Override
    public SalesProfitabilityDTO getSalesProfitability(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException(
                    "Los parámetros 'from' y 'to' son obligatorios para el reporte de rentabilidad.");
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException(
                    "El parámetro 'from' no puede ser posterior a 'to'.");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        BigDecimal revenue     = saleOrderDetailRepository.sumRevenue(fromDt, toDt);
        BigDecimal cogs        = saleOrderDetailRepository.sumCogs(fromDt, toDt);
        BigDecimal margin      = revenue.subtract(cogs);
        BigDecimal marginPct   = computeMarginPct(revenue, margin);
        Long       orderCount  = saleOrderDetailRepository.countDeliveredOrders(fromDt, toDt);
        BigDecimal avgTicket   = computeAvgTicket(revenue, orderCount);

        return SalesProfitabilityDTO.builder()
                .from(from)
                .to(to)
                .totalRevenue(revenue)
                .totalCogs(cogs)
                .grossMargin(margin)
                .grossMarginPct(marginPct)
                .deliveredOrderCount(orderCount)
                .avgTicket(avgTicket)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Calcula (grossMargin / revenue) × 100, redondeado a 2 decimales.
     * Retorna null en lugar de dividir por cero cuando revenue es 0 o negativo.
     * null comunica al frontend que el porcentaje no es calculable, evitando
     * mostrar un "0%" engañoso cuando simplemente no hay datos.
     */
    private BigDecimal computeMarginPct(BigDecimal revenue, BigDecimal grossMargin) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return grossMargin.divide(revenue, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula revenue / orderCount, redondeado a 2 decimales.
     * Retorna null cuando orderCount == 0 para evitar división por cero.
     */
    private BigDecimal computeAvgTicket(BigDecimal revenue, Long orderCount) {
        if (orderCount == null || orderCount == 0L) {
            return null;
        }
        return revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
    }
}
