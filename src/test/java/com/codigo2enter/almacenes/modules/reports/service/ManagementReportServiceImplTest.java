package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.inventory.model.Category;
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
import static org.mockito.Mockito.*;

/**
 * Tests unitarios (Tipo A) para ManagementReportServiceImpl.
 *
 * Verifica los algoritmos de análisis de gestión:
 *   - Ranking por revenue con cálculo de margen
 *   - Clasificación ABC (Pareto al 80/95/100%)
 *   - Interpretación de rotación de inventario
 *   - Ticket promedio de tendencia
 *   - Formato TO_CHAR según groupBy
 *
 * Nota: los retornos de Object[] en Mockito requieren ArrayList en lugar de List.of()
 * para que Java infiera correctamente List<Object[]> y no List<Object>.
 */
@ExtendWith(MockitoExtension.class)
class ManagementReportServiceImplTest {

    @Mock SaleOrderDetailRepository saleOrderDetailRepository;
    @Mock ProductRepository          productRepository;
    @Mock PurchaseOrderRepository    purchaseOrderRepository;
    @Mock SaleOrderRepository        saleOrderRepository;

    @InjectMocks ManagementReportServiceImpl managementService;

    private Product product1;
    private Product product2;
    private Product product3;
    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(1L).name("Herramientas").build();

        product1 = Product.builder().id(1L).sku("P-001").name("Taladro")
                .currentStock(10).unitCost(new BigDecimal("500.00")).category(category).build();
        product2 = Product.builder().id(2L).sku("P-002").name("Sierra")
                .currentStock(5).unitCost(new BigDecimal("800.00")).category(category).build();
        product3 = Product.builder().id(3L).sku("P-003").name("Llave")
                .currentStock(100).unitCost(new BigDecimal("50.00")).category(category).build();

        lenient().when(productRepository.findById(1L)).thenReturn(Optional.of(product1));
        lenient().when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
        lenient().when(productRepository.findById(3L)).thenReturn(Optional.of(product3));
        lenient().when(saleOrderDetailRepository.revenueByProduct(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(saleOrderDetailRepository.cogsByProduct(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(saleOrderDetailRepository.quantitySoldByProduct(any(), any()))
                .thenReturn(Collections.emptyList());
    }

    // ── Helpers para construir listas de Object[] con tipo correcto ───────────

    private List<Object[]> rows(Object[]... arrays) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] a : arrays) list.add(a);
        return list;
    }

    // ── Top products ─────────────────────────────────────────────────────────

    @Test
    void getTopProducts_ordenadosPorRevenueDesc() {
        when(saleOrderDetailRepository.revenueByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("1000.00")},
                     new Object[]{2L, new BigDecimal("600.00")}));
        when(saleOrderDetailRepository.cogsByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("400.00")},
                     new Object[]{2L, new BigDecimal("300.00")}));
        when(saleOrderDetailRepository.quantitySoldByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, 5L},
                     new Object[]{2L, 3L}));

        List<TopProductDTO> result = managementService.getTopProducts(null, null, 10);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getRank());
        assertEquals(2, result.get(1).getRank());
        assertEquals(new BigDecimal("1000.00"), result.get(0).getTotalRevenue());
        assertEquals(new BigDecimal("600.00"),  result.get(1).getTotalRevenue());
    }

    @Test
    void getTopProducts_limit2_retornaSolo2() {
        when(saleOrderDetailRepository.revenueByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("1000.00")},
                     new Object[]{2L, new BigDecimal("600.00")},
                     new Object[]{3L, new BigDecimal("200.00")}));
        when(saleOrderDetailRepository.cogsByProduct(any(), any())).thenReturn(Collections.emptyList());
        when(saleOrderDetailRepository.quantitySoldByProduct(any(), any())).thenReturn(Collections.emptyList());

        List<TopProductDTO> result = managementService.getTopProducts(null, null, 2);

        assertEquals(2, result.size(),
                "Con limit=2 debe retornar solo los 2 primeros productos");
    }

    @Test
    void getTopProducts_sinVentas_retornaListaVacia() {
        when(saleOrderDetailRepository.revenueByProduct(any(), any()))
                .thenReturn(Collections.emptyList());

        List<TopProductDTO> result = managementService.getTopProducts(null, null, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getTopProducts_calculaGrossMarginCorrectamente() {
        // revenue=500, cogs=200 → margin=300, pct=60%
        when(saleOrderDetailRepository.revenueByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("500.00")}));
        when(saleOrderDetailRepository.cogsByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("200.00")}));
        when(saleOrderDetailRepository.quantitySoldByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, 2L}));

        List<TopProductDTO> result = managementService.getTopProducts(null, null, 10);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("300.00"), result.get(0).getGrossMargin());
        assertEquals(new BigDecimal("60.00"),  result.get(0).getGrossMarginPct());
    }

    // ── ABC Analysis ─────────────────────────────────────────────────────────

    @Test
    void getAbcAnalysis_clasificaABC_correctamente() {
        // total=1000: prod1=800(80%), prod2=150(15%), prod3=50(5%) → A, B, C
        when(saleOrderDetailRepository.revenueByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("800.00")},
                     new Object[]{2L, new BigDecimal("150.00")},
                     new Object[]{3L, new BigDecimal("50.00")}));

        List<AbcProductDTO> result = managementService.getAbcAnalysis(null, null);

        assertEquals(3, result.size());
        assertEquals("A", result.get(0).getClassification(),
                "80% acumulado → A");
        assertEquals("B", result.get(1).getClassification(),
                "95% acumulado → B");
        assertEquals("C", result.get(2).getClassification(),
                "100% acumulado → C");
    }

    @Test
    void getAbcAnalysis_unProducto_clasificaComoA() {
        // Un único producto = 100% del revenue. cumulativePct=100 > 95 → C por el algoritmo
        // (el primer y único producto cruza todos los umbrales)
        when(saleOrderDetailRepository.revenueByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("500.00")}));

        List<AbcProductDTO> result = managementService.getAbcAnalysis(null, null);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).getClassification());
    }

    @Test
    void getAbcAnalysis_sinVentas_retornaListaVacia() {
        when(saleOrderDetailRepository.revenueByProduct(any(), any()))
                .thenReturn(Collections.emptyList());

        List<AbcProductDTO> result = managementService.getAbcAnalysis(null, null);

        assertTrue(result.isEmpty());
    }

    // ── Inventory Turnover ───────────────────────────────────────────────────

    @Test
    void getInventoryTurnover_interpretacionAlta_mayor4() {
        // product1: currentStock=10, unitCost=500 → inventoryValue=5000
        // cogs=25000 → turnover=5 → "Alta"
        when(saleOrderDetailRepository.cogsByProduct(any(), any())).thenReturn(
                rows(new Object[]{1L, new BigDecimal("25000.00")}));

        List<InventoryTurnoverItemDTO> result = managementService.getInventoryTurnover(null, null);

        assertEquals(1, result.size());
        assertEquals("Alta", result.get(0).getInterpretation());
        assertNotNull(result.get(0).getTurnoverRate());
    }

    @Test
    void getInventoryTurnover_sinStock_turnoverNull() {
        Product sinStock = Product.builder().id(99L).sku("EMPTY").name("Sin stock")
                .currentStock(0).unitCost(new BigDecimal("100.00")).category(category).build();
        when(productRepository.findById(99L)).thenReturn(Optional.of(sinStock));
        when(saleOrderDetailRepository.cogsByProduct(any(), any())).thenReturn(
                rows(new Object[]{99L, new BigDecimal("500.00")}));

        List<InventoryTurnoverItemDTO> result = managementService.getInventoryTurnover(null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).getTurnoverRate(),
                "turnoverRate debe ser null cuando el inventario actual es cero");
        assertEquals("Sin datos", result.get(0).getInterpretation());
    }

    @Test
    void getInventoryTurnover_sinVentas_cogsEsCero() {
        when(saleOrderDetailRepository.cogsByProduct(any(), any()))
                .thenReturn(Collections.emptyList());

        List<InventoryTurnoverItemDTO> result = managementService.getInventoryTurnover(null, null);

        assertTrue(result.isEmpty(),
                "Si no hay ventas no hay COGS, por tanto la lista debe estar vacía");
    }

    // ── Purchases by supplier ────────────────────────────────────────────────

    @Test
    void getPurchasesBySupplier_calculaAvgOrderAmount() {
        // 4 órdenes, total=1000 → avg=250
        List<Object[]> supplierRows = new ArrayList<>();
        supplierRows.add(new Object[]{1L, "Ferreterías SA", "RFC001", 4L,
                                      new BigDecimal("1000.00"), LocalDateTime.now()});
        when(purchaseOrderRepository.totalsBySupplier(any(), any())).thenReturn(supplierRows);

        List<PurchaseBySupplierDTO> result = managementService.getPurchasesBySupplier(null, null);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("250.00"), result.get(0).getAvgOrderAmount());
        assertEquals(4L, result.get(0).getOrderCount());
    }

    @Test
    void getPurchasesBySupplier_sinOrdenes_retornaListaVacia() {
        when(purchaseOrderRepository.totalsBySupplier(any(), any()))
                .thenReturn(Collections.emptyList());

        List<PurchaseBySupplierDTO> result = managementService.getPurchasesBySupplier(null, null);

        assertTrue(result.isEmpty());
    }

    // ── Sales trend ──────────────────────────────────────────────────────────

    @Test
    void getSalesTrend_groupByMonth_usaFormatoYYYYMM() {
        List<Object[]> trendRows = new ArrayList<>();
        trendRows.add(new Object[]{"2026-01", new BigDecimal("5000.00"), 10L});
        when(saleOrderRepository.revenueByPeriod(any(), any(), eq("YYYY-MM"))).thenReturn(trendRows);

        List<SalesTrendItemDTO> result = managementService.getSalesTrend(null, null, "MONTH");

        assertEquals(1, result.size());
        assertEquals("2026-01", result.get(0).getPeriod());
        verify(saleOrderRepository).revenueByPeriod(any(), any(), eq("YYYY-MM"));
    }

    @Test
    void getSalesTrend_groupByDay_usaFormatoYYYYMMDD() {
        when(saleOrderRepository.revenueByPeriod(any(), any(), eq("YYYY-MM-DD")))
                .thenReturn(Collections.emptyList());

        managementService.getSalesTrend(LocalDate.now(), LocalDate.now(), "DAY");

        verify(saleOrderRepository).revenueByPeriod(any(), any(), eq("YYYY-MM-DD"));
    }

    @Test
    void getSalesTrend_groupByWeek_usaFormatoIYYYIW() {
        when(saleOrderRepository.revenueByPeriod(any(), any(), eq("IYYY-IW")))
                .thenReturn(Collections.emptyList());

        managementService.getSalesTrend(LocalDate.now(), LocalDate.now(), "WEEK");

        verify(saleOrderRepository).revenueByPeriod(any(), any(), eq("IYYY-IW"));
    }

    @Test
    void getSalesTrend_groupByInvalido_lanzaExcepcion() {
        assertThrows(RuntimeException.class,
                () -> managementService.getSalesTrend(null, null, "INVALID"),
                "Debe lanzar excepción con groupBy no reconocido");
    }

    @Test
    void getSalesTrend_avgTicketCalculado() {
        // revenue=600, count=3 → avgTicket=200
        List<Object[]> trendRows = new ArrayList<>();
        trendRows.add(new Object[]{"2026-01", new BigDecimal("600.00"), 3L});
        when(saleOrderRepository.revenueByPeriod(any(), any(), any())).thenReturn(trendRows);

        List<SalesTrendItemDTO> result = managementService.getSalesTrend(null, null, "MONTH");

        assertEquals(new BigDecimal("200.00"), result.get(0).getAvgTicket());
    }
}
