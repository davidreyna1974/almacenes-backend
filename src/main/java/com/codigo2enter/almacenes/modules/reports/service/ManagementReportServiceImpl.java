package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.core.exception.BusinessRuleException;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.reports.dto.management.AbcProductDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.InventoryTurnoverItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.PurchaseBySupplierDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.SalesTrendItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.TopProductDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación de los informes de gestión para mandos medios y analistas.
 *
 * Todos los métodos son readOnly — no generan efectos laterales.
 *
 * Dependencias:
 *   - SaleOrderDetailRepository: revenue, COGS, cantidades por producto
 *   - ProductRepository: datos de catálogo (SKU, nombre, categoría, stock actual)
 *   - PurchaseOrderRepository: totales por proveedor
 *   - SaleOrderRepository: tendencia de ventas por período
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ManagementReportServiceImpl implements ManagementReportService {

    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SaleOrderRepository saleOrderRepository;

    /**
     * Top N productos por revenue en el período.
     *
     * Flujo:
     *   1. Ejecutar 3 queries: revenueByProduct (ya ordenado DESC), cogsByProduct,
     *      quantitySoldByProduct
     *   2. Construir maps productId → cogs y productId → qty para O(1) lookup
     *   3. Iterar sobre la lista de revenue (ya ordenada) para construir DTOs con rank
     *   4. Enriquecer con datos de catálogo del producto (SKU, nombre, categoría)
     *   5. Truncar al límite solicitado
     *
     * El rank se asigna por posición en la iteración (revenueByProduct viene ORDER BY DESC).
     * Si limit supera el tamaño de la lista, se retorna la lista completa sin error.
     *
     * @param from  inicio del período
     * @param to    fin del período
     * @param limit número máximo de resultados (default 10, max 50 aplicado en controlador)
     */
    @Override
    public List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, Integer limit) {
        LocalDateTime fromDt = toStart(from);
        LocalDateTime toDt   = toEnd(to);
        int maxLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        List<Object[]> revenueRows = saleOrderDetailRepository.revenueByProduct(fromDt, toDt);
        List<Object[]> cogsRows    = saleOrderDetailRepository.cogsByProduct(fromDt, toDt);
        List<Object[]> qtyRows     = saleOrderDetailRepository.quantitySoldByProduct(fromDt, toDt);

        // Construir maps para lookup eficiente
        Map<Long, BigDecimal> cogsMap = toMap(cogsRows);
        Map<Long, Long>       qtyMap  = toLongMap(qtyRows);

        List<TopProductDTO> result = new ArrayList<>();
        int rank = 1;

        for (Object[] row : revenueRows) {
            if (result.size() >= maxLimit) break;

            Long       productId = (Long)       row[0];
            BigDecimal revenue   = (BigDecimal) row[1];
            BigDecimal cogs      = cogsMap.getOrDefault(productId, BigDecimal.ZERO);
            Long       qty       = qtyMap.getOrDefault(productId, 0L);
            BigDecimal margin    = revenue.subtract(cogs);
            BigDecimal marginPct = computeMarginPct(revenue, margin);

            Optional<Product> productOpt = productRepository.findById(productId);
            String sku          = productOpt.map(Product::getSku).orElse("N/A");
            String name         = productOpt.map(Product::getName).orElse("N/A");
            String categoryName = productOpt
                    .map(p -> p.getCategory() != null ? p.getCategory().getName() : "Sin categoría")
                    .orElse("N/A");

            result.add(TopProductDTO.builder()
                    .rank(rank++)
                    .productId(productId)
                    .sku(sku)
                    .name(name)
                    .categoryName(categoryName)
                    .totalQuantitySold(qty)
                    .totalRevenue(revenue)
                    .totalCogs(cogs)
                    .grossMargin(margin)
                    .grossMarginPct(marginPct)
                    .build());
        }

        return result;
    }

    /**
     * Clasificación ABC de productos según el principio de Pareto aplicado al revenue.
     *
     * Flujo:
     *   1. Obtener revenueByProduct (ordenado DESC) → calcular revenue total
     *   2. Si lista vacía → retornar lista vacía (no hay ventas en el período)
     *   3. Iterar acumulando cumulativePct:
     *        A: cumulativePct <= 80
     *        B: cumulativePct <= 95
     *        C: el resto
     *   4. Enriquecer con datos de catálogo
     *
     * El porcentaje individual (revenuePct) y el acumulado (cumulativePct) se calculan
     * con precisión de 2 decimales para que la suma visual sea coherente.
     */
    @Override
    public List<AbcProductDTO> getAbcAnalysis(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = toStart(from);
        LocalDateTime toDt   = toEnd(to);

        List<Object[]> revenueRows = saleOrderDetailRepository.revenueByProduct(fromDt, toDt);
        if (revenueRows.isEmpty()) return new ArrayList<>();

        BigDecimal totalRevenue = revenueRows.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRevenue.compareTo(BigDecimal.ZERO) <= 0) return new ArrayList<>();

        List<AbcProductDTO> result      = new ArrayList<>();
        BigDecimal          cumulativePct = BigDecimal.ZERO;

        for (Object[] row : revenueRows) {
            Long       productId  = (Long)       row[0];
            BigDecimal revenue    = (BigDecimal) row[1];
            BigDecimal revPct     = revenue.divide(totalRevenue, 10, RoundingMode.HALF_UP)
                                         .multiply(BigDecimal.valueOf(100))
                                         .setScale(2, RoundingMode.HALF_UP);
            cumulativePct = cumulativePct.add(revPct);

            String classification;
            if (cumulativePct.compareTo(new BigDecimal("80")) <= 0) {
                classification = "A";
            } else if (cumulativePct.compareTo(new BigDecimal("95")) <= 0) {
                classification = "B";
            } else {
                classification = "C";
            }

            Optional<Product> productOpt = productRepository.findById(productId);
            String sku  = productOpt.map(Product::getSku).orElse("N/A");
            String name = productOpt.map(Product::getName).orElse("N/A");

            result.add(AbcProductDTO.builder()
                    .classification(classification)
                    .productId(productId)
                    .sku(sku)
                    .name(name)
                    .totalRevenue(revenue)
                    .revenuePct(revPct)
                    .cumulativePct(cumulativePct.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return result;
    }

    /**
     * Tasa de rotación de inventario por producto en el período.
     *
     * Flujo:
     *   1. Obtener cogsByProduct del período
     *   2. Para cada producto con COGS, calcular currentInventoryValue = currentStock × unitCost
     *   3. turnoverRate = cogs / currentInventoryValue (null si inventoryValue == 0)
     *   4. Asignar interpretación textual: Alta (>4), Media (>=1), Baja (<1), Sin datos (null)
     *
     * Criterio de éxito: si un producto tiene COGS pero su inventario actual es 0
     * (se agotó o desactivó), turnoverRate es null con interpretación "Sin datos".
     */
    @Override
    public List<InventoryTurnoverItemDTO> getInventoryTurnover(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = toStart(from);
        LocalDateTime toDt   = toEnd(to);

        List<Object[]> cogsRows = saleOrderDetailRepository.cogsByProduct(fromDt, toDt);
        List<InventoryTurnoverItemDTO> result = new ArrayList<>();

        for (Object[] row : cogsRows) {
            Long       productId       = (Long)       row[0];
            BigDecimal cogsInPeriod    = (BigDecimal) row[1];

            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) continue;
            Product p = productOpt.get();

            BigDecimal inventoryValue = BigDecimal.valueOf(p.getCurrentStock())
                    .multiply(p.getUnitCost() != null ? p.getUnitCost() : BigDecimal.ZERO);
            BigDecimal turnoverRate   = null;
            String     interpretation = "Sin datos";

            if (inventoryValue.compareTo(BigDecimal.ZERO) > 0) {
                turnoverRate = cogsInPeriod.divide(inventoryValue, 4, RoundingMode.HALF_UP);
                if (turnoverRate.compareTo(new BigDecimal("4")) > 0) {
                    interpretation = "Alta";
                } else if (turnoverRate.compareTo(BigDecimal.ONE) >= 0) {
                    interpretation = "Media";
                } else {
                    interpretation = "Baja";
                }
            }

            String categoryName = p.getCategory() != null ? p.getCategory().getName() : "Sin categoría";

            result.add(InventoryTurnoverItemDTO.builder()
                    .productId(productId)
                    .sku(p.getSku())
                    .name(p.getName())
                    .categoryName(categoryName)
                    .cogsInPeriod(cogsInPeriod)
                    .currentInventoryValue(inventoryValue)
                    .turnoverRate(turnoverRate)
                    .interpretation(interpretation)
                    .build());
        }

        return result;
    }

    /**
     * Compras agrupadas por proveedor para órdenes RECEIVED en el período.
     *
     * Flujo:
     *   1. totalsBySupplier retorna Object[] con todos los datos necesarios
     *   2. avgOrderAmount = totalAmount / count (BigDecimal.ZERO si count == 0)
     *
     * Solo órdenes RECEIVED — las PENDING y APPROVED no representan dinero desembolsado.
     * La lista viene ordenada por totalAmount DESC desde la query (proveedor más importante primero).
     */
    @Override
    public List<PurchaseBySupplierDTO> getPurchasesBySupplier(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = toStart(from);
        LocalDateTime toDt   = toEnd(to);

        List<Object[]> rows = purchaseOrderRepository.totalsBySupplier(fromDt, toDt);
        List<PurchaseBySupplierDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long           supplierId   = (Long)           row[0];
            String         supplierName = (String)         row[1];
            String         rfc          = (String)         row[2];
            Long           count        = (Long)           row[3];
            BigDecimal     total        = (BigDecimal)     row[4];
            LocalDateTime  lastDate     = (LocalDateTime)  row[5];
            BigDecimal     avg          = count > 0
                    ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(PurchaseBySupplierDTO.builder()
                    .supplierId(supplierId)
                    .supplierName(supplierName)
                    .rfc(rfc)
                    .orderCount(count)
                    .totalAmount(total)
                    .avgOrderAmount(avg)
                    .lastOrderDate(lastDate)
                    .build());
        }

        return result;
    }

    /**
     * Tendencia de ventas agrupadas por período usando TO_CHAR de PostgreSQL.
     *
     * El formato TO_CHAR depende del groupBy:
     *   DAY   → "YYYY-MM-DD"  (cada día del período)
     *   WEEK  → "IYYY-IW"    (semana ISO: año-semana)
     *   MONTH → "YYYY-MM"    (cada mes del período)
     *
     * Se usa FUNCTION('TO_CHAR', ...) en JPQL que el dialecto PostgreSQL resuelve
     * correctamente. Si el motor de BD cambia, esta función debe revisarse.
     *
     * avgTicket es null cuando count == 0 para evitar división por cero.
     *
     * @throws RuntimeException si groupBy no es "DAY", "WEEK" ni "MONTH"
     */
    @Override
    public List<SalesTrendItemDTO> getSalesTrend(LocalDate from, LocalDate to, String groupBy) {
        LocalDateTime fromDt = toStart(from);
        LocalDateTime toDt   = toEnd(to);

        String format = resolveFormat(groupBy);
        List<Object[]> rows = saleOrderRepository.revenueByPeriod(fromDt, toDt, format);
        List<SalesTrendItemDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            String     period  = (String)     row[0];
            BigDecimal revenue = (BigDecimal) row[1];
            Long       count   = (Long)       row[2];
            BigDecimal avg     = count > 0
                    ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : null;

            result.add(SalesTrendItemDTO.builder()
                    .period(period)
                    .revenue(revenue)
                    .orderCount(count)
                    .avgTicket(avg)
                    .build());
        }

        return result;
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /** Convierte LocalDate al inicio del día (00:00:00). */
    private LocalDateTime toStart(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
    }

    /** Convierte LocalDate al inicio del día siguiente (exclusivo). */
    private LocalDateTime toEnd(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);
    }

    /**
     * Resuelve el formato TO_CHAR según el agrupamiento solicitado.
     * Lanza excepción si el valor no es reconocido para dar feedback claro al cliente.
     */
    private String resolveFormat(String groupBy) {
        if (groupBy == null) return "YYYY-MM";
        return switch (groupBy.toUpperCase()) {
            case "DAY"   -> "YYYY-MM-DD";
            case "WEEK"  -> "IYYY-IW";
            case "MONTH" -> "YYYY-MM";
            default -> throw new BusinessRuleException(
                    "Valor de groupBy no reconocido: '" + groupBy + "'. Use DAY, WEEK o MONTH.");
        };
    }

    /**
     * Convierte una lista de Object[]{Long productId, BigDecimal value} a un Map
     * para lookups en O(1) al cruzar resultados de múltiples queries.
     */
    private Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    /**
     * Convierte una lista de Object[]{Long productId, Long quantity} a un Map.
     * La suma de quantitySoldByProduct puede retornar un tipo numérico (Long o BigDecimal)
     * dependiendo del dialecto — se convierte a Long de forma segura.
     */
    private Map<Long, Long> toLongMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            Long productId = (Long) row[0];
            Long qty = ((Number) row[1]).longValue();
            map.put(productId, qty);
        }
        return map;
    }

    /**
     * Calcula (grossMargin / revenue) × 100, redondeado a 2 decimales.
     * Retorna null en lugar de dividir por cero cuando revenue es 0.
     */
    private BigDecimal computeMarginPct(BigDecimal revenue, BigDecimal grossMargin) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return grossMargin.divide(revenue, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
