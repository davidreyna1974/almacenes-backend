package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.auth.model.User;
import com.codigo2enter.almacenes.modules.inventory.model.Category;
import com.codigo2enter.almacenes.modules.inventory.model.MovementType;
import com.codigo2enter.almacenes.modules.inventory.model.Product;
import com.codigo2enter.almacenes.modules.inventory.model.StockMovement;
import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.inventory.repository.StockMovementRepository;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrder;
import com.codigo2enter.almacenes.modules.purchases.model.PurchaseOrderStatus;
import com.codigo2enter.almacenes.modules.purchases.model.Supplier;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.reports.dto.operational.KardexReportDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.LowStockReportItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.MovementsSummaryDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.PendingOperationsDTO;
import com.codigo2enter.almacenes.modules.sales.model.Client;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrder;
import com.codigo2enter.almacenes.modules.sales.model.SaleOrderStatus;
import com.codigo2enter.almacenes.modules.sales.repository.SaleOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios (Tipo A) para OperationalReportServiceImpl.
 *
 * Verifica la lógica operativa:
 *   - Cálculo de deficit en low-stock
 *   - Saldo acumulado del Kardex (iteración cronológica)
 *   - Reconstrucción de openingStock hacia atrás
 *   - Conteo de operaciones pendientes
 *   - netMovement en resumen de movimientos
 */
@ExtendWith(MockitoExtension.class)
class OperationalReportServiceImplTest {

    @Mock ProductRepository          productRepository;
    @Mock StockMovementRepository    stockMovementRepository;
    @Mock PurchaseOrderRepository    purchaseOrderRepository;
    @Mock SaleOrderRepository        saleOrderRepository;

    @InjectMocks OperationalReportServiceImpl operationalService;

    private Category  category;
    private Product   product;
    private User      operator;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1L).name("Herramientas").build();
        product  = Product.builder()
                .id(1L).sku("P-001").name("Taladro")
                .currentStock(50).minimumStock(10).reservedStock(5)
                .unitCost(new BigDecimal("500.00"))
                .category(category).build();
        operator = User.builder().id(1L).username("operador01").build();

        lenient().when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(productRepository.findLowStockProducts()).thenReturn(Collections.emptyList());
        lenient().when(stockMovementRepository.findByProductAndPeriod(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(stockMovementRepository.sumInByPeriod(any(), any())).thenReturn(0);
        lenient().when(stockMovementRepository.sumOutByPeriod(any(), any())).thenReturn(0);
        lenient().when(purchaseOrderRepository.findPendingAndApproved()).thenReturn(Collections.emptyList());
        lenient().when(saleOrderRepository.findPendingAndApproved()).thenReturn(Collections.emptyList());
    }

    // ── Low stock ────────────────────────────────────────────────────────────

    @Test
    void getLowStock_retornaProductosBajoMinimo() {
        // Arrange: producto con stock bajo
        when(productRepository.findLowStockProducts()).thenReturn(List.of(product));

        // Act
        List<LowStockReportItemDTO> result = operationalService.getLowStock();

        // Assert
        assertEquals(1, result.size());
        assertEquals("P-001", result.get(0).getSku());
        assertEquals(50, result.get(0).getCurrentStock());
        assertEquals(10, result.get(0).getMinimumStock());
        assertEquals(45, result.get(0).getAvailableStock()); // 50 - 5 reservados
    }

    @Test
    void getLowStock_sinProductosBajoMinimo_retornaListaVacia() {
        when(productRepository.findLowStockProducts()).thenReturn(Collections.emptyList());

        List<LowStockReportItemDTO> result = operationalService.getLowStock();

        assertTrue(result.isEmpty());
    }

    @Test
    void getLowStock_calculaDeficitCorrectamente() {
        // Arrange: minimumStock=10, currentStock=3 → deficit=7
        Product critico = Product.builder()
                .id(2L).sku("P-002").name("Llave")
                .currentStock(3).minimumStock(10).reservedStock(0)
                .category(category).build();
        when(productRepository.findLowStockProducts()).thenReturn(List.of(critico));

        List<LowStockReportItemDTO> result = operationalService.getLowStock();

        assertEquals(1, result.size());
        assertEquals(7, result.get(0).getDeficit(),
                "deficit = minimumStock(10) - currentStock(3) = 7");
    }

    // ── Kardex ───────────────────────────────────────────────────────────────

    @Test
    void getKardex_calculaSaldoAcumuladoCorrectamente() {
        // Arrange: 3 movimientos: IN 10, OUT 3, IN 7
        // openingStock = currentStock(50) - totalIn(17) + totalOut(3) = 36
        // Saldos: 36+10=46, 46-3=43, 43+7=50 ✓ (cierra en currentStock)
        LocalDateTime t1 = LocalDateTime.now().minusDays(3);
        LocalDateTime t2 = LocalDateTime.now().minusDays(2);
        LocalDateTime t3 = LocalDateTime.now().minusDays(1);

        List<StockMovement> movements = List.of(
                buildMovement(MovementType.IN,  10, "Compra",    t1),
                buildMovement(MovementType.OUT,  3, "Venta",     t2),
                buildMovement(MovementType.IN,   7, "Reposición", t3)
        );
        when(stockMovementRepository.findByProductAndPeriod(eq(1L), any(), any()))
                .thenReturn(movements);

        // Act
        KardexReportDTO result = operationalService.getKardex(1L,
                LocalDate.now().minusDays(5), LocalDate.now());

        // Assert: saldos acumulados
        assertEquals(3, result.getMovements().size());
        assertEquals(46, result.getMovements().get(0).getBalance(), "Saldo tras IN 10");
        assertEquals(43, result.getMovements().get(1).getBalance(), "Saldo tras OUT 3");
        assertEquals(50, result.getMovements().get(2).getBalance(), "Saldo final = currentStock");
    }

    @Test
    void getKardex_calculaOpeningStock() {
        // openingStock = currentStock(50) - totalIn(10) + totalOut(5) = 45
        LocalDateTime t1 = LocalDateTime.now().minusDays(2);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);

        List<StockMovement> movements = List.of(
                buildMovement(MovementType.IN,  10, "Entrada",  t1),
                buildMovement(MovementType.OUT,  5, "Salida",   t2)
        );
        when(stockMovementRepository.findByProductAndPeriod(eq(1L), any(), any()))
                .thenReturn(movements);

        KardexReportDTO result = operationalService.getKardex(1L,
                LocalDate.now().minusDays(5), LocalDate.now());

        assertEquals(45, result.getOpeningStock(),
                "openingStock = currentStock(50) - totalIn(10) + totalOut(5) = 45");
        assertEquals(50, result.getClosingStock(),
                "closingStock = currentStock actual del producto");
        assertEquals(10, result.getTotalIn());
        assertEquals(5,  result.getTotalOut());
    }

    @Test
    void getKardex_sinMovimientos_retornaListaVacia() {
        when(stockMovementRepository.findByProductAndPeriod(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        KardexReportDTO result = operationalService.getKardex(1L, null, null);

        assertTrue(result.getMovements().isEmpty());
        assertEquals(50, result.getOpeningStock(), "Sin movimientos: opening = closing = currentStock");
        assertEquals(50, result.getClosingStock());
    }

    @Test
    void getKardex_productoNoExiste_lanzaExcepcion() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> operationalService.getKardex(999L, null, null),
                "Debe lanzar excepción cuando el producto no existe");
    }

    // ── Pending operations ───────────────────────────────────────────────────

    @Test
    void getPendingOperations_contaOrdenesPendientes() {
        // Arrange: 2 PO y 1 SO pendientes
        Supplier supplier = Supplier.builder().id(1L).companyName("Proveedor SA").build();
        Client   client   = Client.builder().id(1L).name("Cliente ABC").build();

        PurchaseOrder po1 = PurchaseOrder.builder()
                .id(1L).orderNumber("OC-2026-0001").status(PurchaseOrderStatus.PENDING)
                .supplier(supplier).totalAmount(BigDecimal.ZERO).details(new ArrayList<>()).build();
        PurchaseOrder po2 = PurchaseOrder.builder()
                .id(2L).orderNumber("OC-2026-0002").status(PurchaseOrderStatus.APPROVED)
                .supplier(supplier).totalAmount(BigDecimal.ZERO).details(new ArrayList<>()).build();
        SaleOrder so1 = SaleOrder.builder()
                .id(1L).orderNumber("OV-2026-0001").status(SaleOrderStatus.PENDING)
                .client(client).totalAmount(BigDecimal.ZERO).details(new ArrayList<>()).build();

        when(purchaseOrderRepository.findPendingAndApproved()).thenReturn(List.of(po1, po2));
        when(saleOrderRepository.findPendingAndApproved()).thenReturn(List.of(so1));

        // Act
        PendingOperationsDTO result = operationalService.getPendingOperations();

        // Assert
        assertEquals(2, result.getTotalPendingPurchases());
        assertEquals(1, result.getTotalPendingSales());
        assertEquals("Proveedor SA", result.getPendingPurchaseOrders().get(0).getCounterpartName());
        assertEquals("Cliente ABC",  result.getPendingSaleOrders().get(0).getCounterpartName());
    }

    @Test
    void getPendingOperations_listaVacia_retornaContadoresEnCero() {
        when(purchaseOrderRepository.findPendingAndApproved()).thenReturn(Collections.emptyList());
        when(saleOrderRepository.findPendingAndApproved()).thenReturn(Collections.emptyList());

        PendingOperationsDTO result = operationalService.getPendingOperations();

        assertEquals(0, result.getTotalPendingPurchases());
        assertEquals(0, result.getTotalPendingSales());
        assertTrue(result.getPendingPurchaseOrders().isEmpty());
        assertTrue(result.getPendingSaleOrders().isEmpty());
    }

    // ── Movements summary ────────────────────────────────────────────────────

    @Test
    void getMovementsSummary_calculaNetMovement() {
        // Arrange: in=100, out=60 → net=40
        when(stockMovementRepository.sumInByPeriod(any(), any())).thenReturn(100);
        when(stockMovementRepository.sumOutByPeriod(any(), any())).thenReturn(60);

        MovementsSummaryDTO result = operationalService.getMovementsSummary(null, null);

        assertEquals(100, result.getTotalIn());
        assertEquals(60,  result.getTotalOut());
        assertEquals(40,  result.getNetMovement(),
                "netMovement = totalIn(100) - totalOut(60) = 40");
    }

    @Test
    void getMovementsSummary_soloEntradas_outEsCero() {
        when(stockMovementRepository.sumInByPeriod(any(), any())).thenReturn(50);
        when(stockMovementRepository.sumOutByPeriod(any(), any())).thenReturn(0);

        MovementsSummaryDTO result = operationalService.getMovementsSummary(null, null);

        assertEquals(50, result.getTotalIn());
        assertEquals(0,  result.getTotalOut());
        assertEquals(50, result.getNetMovement());
    }

    @Test
    void getMovementsSummary_sinMovimientos_retornaCeros() {
        // Los COALESCE en las queries garantizan 0, no null
        when(stockMovementRepository.sumInByPeriod(any(), any())).thenReturn(0);
        when(stockMovementRepository.sumOutByPeriod(any(), any())).thenReturn(0);

        MovementsSummaryDTO result = operationalService.getMovementsSummary(null, null);

        assertEquals(0, result.getTotalIn());
        assertEquals(0, result.getTotalOut());
        assertEquals(0, result.getNetMovement());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private StockMovement buildMovement(MovementType type, int qty, String reason, LocalDateTime at) {
        return StockMovement.builder()
                .id(System.nanoTime())
                .type(type)
                .quantity(qty)
                .reason(reason)
                .createdAt(at)
                .product(product)
                .createdBy(operator)
                .build();
    }
}
