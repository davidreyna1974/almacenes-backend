package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.core.exception.ResourceNotFoundException;
import com.codigo2enter.almacenes.modules.inventory.model.MovementType;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.StockMovementRepository;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.reports.dto.operational.KardexItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.KardexReportDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.LowStockReportItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.MovementsSummaryDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.PendingOperationsDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.PendingOrderSummaryDTO;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementación de los informes operativos de gestión diaria del almacén.
 *
 * Todos los métodos son readOnly — no generan efectos laterales.
 *
 * Dependencias:
 *   - ProductRepository: stock bajo mínimo y datos de productos para Kardex
 *   - StockMovementRepository: movimientos por período (Kardex y resumen)
 *   - PurchaseOrderRepository: órdenes de compra pendientes
 *   - SaleOrderRepository: órdenes de venta pendientes
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OperationalReportServiceImpl implements OperationalReportService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SaleOrderRepository saleOrderRepository;

    /**
     * Retorna productos con stock disponible en nivel crítico (availableStock <= minimumStock).
     *
     * Flujo:
     *   1. findLowStockProducts() — ya filtra por (currentStock - reservedStock) <= minimumStock
     *   2. Construir DTOs calculando availableStock y deficit
     *   3. Ordenar por deficit DESC (más crítico primero)
     *
     * deficit = minimumStock - currentStock puede ser negativo cuando reservedStock
     * es la causa del bajo disponible pero el stock físico supera el mínimo.
     * En ese caso el déficit real existe a nivel disponible, no físico.
     */
    @Override
    public List<LowStockReportItemDTO> getLowStock() {
        List<Product> lowStockProducts = productRepository.findLowStockProducts();
        List<LowStockReportItemDTO> result = new ArrayList<>();

        for (Product p : lowStockProducts) {
            int availableStock = p.getCurrentStock() - p.getReservedStock();
            int deficit        = p.getMinimumStock() - p.getCurrentStock();
            String categoryName = p.getCategory() != null ? p.getCategory().getName() : "Sin categoría";

            result.add(LowStockReportItemDTO.builder()
                    .productId(p.getId())
                    .sku(p.getSku())
                    .name(p.getName())
                    .categoryName(categoryName)
                    .currentStock(p.getCurrentStock())
                    .minimumStock(p.getMinimumStock())
                    .availableStock(availableStock)
                    .reservedStock(p.getReservedStock())
                    .deficit(deficit)
                    .build());
        }

        result.sort(Comparator.comparingInt(LowStockReportItemDTO::getDeficit).reversed());
        return result;
    }

    /**
     * Kardex de un producto: historial de movimientos con saldo acumulado.
     *
     * Flujo:
     *   1. Verificar que el producto existe (lanzar excepción si no)
     *   2. Obtener movimientos del período en orden ASC (el más antiguo primero)
     *   3. Calcular totales: totalIn y totalOut
     *   4. Reconstruir openingStock hacia atrás desde currentStock:
     *        openingStock = currentStock - totalIn + totalOut
     *      Justificación: partimos del estado actual conocido y "deshacemos" los
     *      movimientos del período. Si entraron 10 y salieron 3, y el stock actual
     *      es 50, entonces al inicio del período había 50 - 10 + 3 = 43.
     *   5. Calcular saldo acumulado por movimiento iterando desde openingStock:
     *        IN → balance += quantity
     *        OUT → balance -= quantity
     *
     * Criterio de éxito: si no hay movimientos en el período, la lista está vacía
     * y openingStock == closingStock == currentStock del producto.
     *
     * @throws RuntimeException si el producto no existe
     */
    @Override
    public KardexReportDTO getKardex(Long productId, LocalDate from, LocalDate to) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto no encontrado con ID: " + productId));

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt   = to   != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        List<StockMovement> movements = stockMovementRepository.findByProductAndPeriod(productId, fromDt, toDt);

        int totalIn  = movements.stream()
                .filter(m -> m.getType() == MovementType.IN)
                .mapToInt(StockMovement::getQuantity).sum();
        int totalOut = movements.stream()
                .filter(m -> m.getType() == MovementType.OUT)
                .mapToInt(StockMovement::getQuantity).sum();

        // Reconstrucción hacia atrás: deshacer los movimientos del período sobre el stock actual
        int openingStock = product.getCurrentStock() - totalIn + totalOut;
        int closingStock = product.getCurrentStock();

        // Calcular saldo acumulado iterando desde openingStock en orden cronológico (ASC)
        List<KardexItemDTO> kardexItems = new ArrayList<>();
        int balance = openingStock;

        for (StockMovement m : movements) {
            if (m.getType() == MovementType.IN) {
                balance += m.getQuantity();
            } else {
                balance -= m.getQuantity();
            }
            String createdByUsername = m.getCreatedBy() != null
                    ? m.getCreatedBy().getUsername() : "Sistema";

            kardexItems.add(KardexItemDTO.builder()
                    .date(m.getCreatedAt())
                    .type(m.getType().name())
                    .quantity(m.getQuantity())
                    .reason(m.getReason())
                    .balance(balance)
                    .createdByUsername(createdByUsername)
                    .build());
        }

        return KardexReportDTO.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .from(from)
                .to(to)
                .openingStock(openingStock)
                .closingStock(closingStock)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .movements(kardexItems)
                .build();
    }

    /**
     * Operaciones pendientes: órdenes de compra y venta en PENDING o APPROVED.
     *
     * Flujo:
     *   1. findPendingAndApproved() de PurchaseOrderRepository → convertir a PendingOrderSummaryDTO
     *      counterpartName = supplier.companyName
     *   2. findPendingAndApproved() de SaleOrderRepository → convertir a PendingOrderSummaryDTO
     *      counterpartName = client.name
     *   3. Construir PendingOperationsDTO con ambas listas y sus contadores
     *
     * detailCount = order.getDetails().size() — carga los detalles via LAZY pero
     * como estamos en el mismo contexto transaccional readOnly, Hibernate los carga
     * en la misma sesión sin problema.
     */
    @Override
    public PendingOperationsDTO getPendingOperations() {
        List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findPendingAndApproved();
        List<SaleOrder>     pendingSOs = saleOrderRepository.findPendingAndApproved();

        List<PendingOrderSummaryDTO> purchaseDTOs = new ArrayList<>();
        for (PurchaseOrder po : pendingPOs) {
            purchaseDTOs.add(PendingOrderSummaryDTO.builder()
                    .orderId(po.getId())
                    .orderNumber(po.getOrderNumber())
                    .status(po.getStatus().name())
                    .counterpartName(po.getSupplier() != null ? po.getSupplier().getCompanyName() : "N/A")
                    .createdAt(po.getCreatedAt())
                    .totalAmount(po.getTotalAmount())
                    .detailCount(po.getDetails().size())
                    .build());
        }

        List<PendingOrderSummaryDTO> saleDTOs = new ArrayList<>();
        for (SaleOrder so : pendingSOs) {
            saleDTOs.add(PendingOrderSummaryDTO.builder()
                    .orderId(so.getId())
                    .orderNumber(so.getOrderNumber())
                    .status(so.getStatus().name())
                    .counterpartName(so.getClient() != null ? so.getClient().getName() : "N/A")
                    .createdAt(so.getCreatedAt())
                    .totalAmount(so.getTotalAmount())
                    .detailCount(so.getDetails().size())
                    .build());
        }

        return PendingOperationsDTO.builder()
                .pendingPurchaseOrders(purchaseDTOs)
                .pendingSaleOrders(saleDTOs)
                .totalPendingPurchases(purchaseDTOs.size())
                .totalPendingSales(saleDTOs.size())
                .build();
    }

    /**
     * Resumen de movimientos de stock (entradas y salidas) en el período.
     *
     * Flujo:
     *   1. sumInByPeriod y sumOutByPeriod retornan Integer (0 si no hay datos)
     *   2. netMovement = totalIn - totalOut
     *
     * Criterio de éxito: todos los campos son 0 cuando no hay movimientos en el período,
     * nunca null — los repositorios usan COALESCE en las queries.
     */
    @Override
    public MovementsSummaryDTO getMovementsSummary(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt   = to   != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        Integer totalIn  = stockMovementRepository.sumInByPeriod(fromDt, toDt);
        Integer totalOut = stockMovementRepository.sumOutByPeriod(fromDt, toDt);
        int     net      = totalIn - totalOut;

        return MovementsSummaryDTO.builder()
                .from(from)
                .to(to)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .netMovement(net)
                .build();
    }
}
