package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.inventory.repository.ProductRepository;
import com.codigo2enter.almacenes.modules.purchases.repository.PurchaseOrderRepository;
import com.codigo2enter.almacenes.modules.reports.dto.executive.ExecutiveDashboardDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.InventoryValuationDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.SalesProfitabilityDTO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios (Tipo A) para ExecutiveReportServiceImpl.
 *
 * Verifica la lógica de cálculo de KPIs financieros sin Spring ni BD real.
 * Los repositorios están mockeados — el foco es la aritmética del servicio:
 * margen bruto, porcentajes, ticket promedio y defaults para períodos sin datos.
 */
@ExtendWith(MockitoExtension.class)
class ExecutiveReportServiceImplTest {

    @Mock SaleOrderDetailRepository saleOrderDetailRepository;
    @Mock ProductRepository          productRepository;
    @Mock PurchaseOrderRepository    purchaseOrderRepository;
    @Mock SaleOrderRepository        saleOrderRepository;

    @InjectMocks ExecutiveReportServiceImpl executiveService;

    @BeforeEach
    void setUp() {
        // Defaults lenient para tests que no usan todas las dependencias
        lenient().when(saleOrderDetailRepository.sumRevenue(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(saleOrderDetailRepository.sumCogs(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(productRepository.totalInventoryValue())
                .thenReturn(BigDecimal.ZERO);
        lenient().when(purchaseOrderRepository.countPendingAndApproved())
                .thenReturn(0L);
        lenient().when(saleOrderRepository.countPendingAndApproved())
                .thenReturn(0L);
    }

    // ── Dashboard: cálculo de margen ─────────────────────────────────────────

    @Test
    void getExecutiveDashboard_conDatos_calculaMargenCorrectamente() {
        // Arrange: revenue=1000, cogs=600 → margin=400, pct=40%
        when(saleOrderDetailRepository.sumRevenue(any(), any()))
                .thenReturn(new BigDecimal("1000.00"));
        when(saleOrderDetailRepository.sumCogs(any(), any()))
                .thenReturn(new BigDecimal("600.00"));

        // Act
        ExecutiveDashboardDTO result = executiveService.getExecutiveDashboard(null, null);

        // Assert
        assertEquals(new BigDecimal("1000.00"), result.getTotalRevenue());
        assertEquals(new BigDecimal("600.00"),  result.getTotalCogs());
        assertEquals(new BigDecimal("400.00"),  result.getGrossMargin());
        assertEquals(new BigDecimal("40.00"),   result.getGrossMarginPct());
        assertNotNull(result.getGeneratedAt());
    }

    @Test
    void getExecutiveDashboard_sinVentas_retornaMargenCero() {
        // Arrange: revenue=0 → grossMarginPct debe ser null (no dividir por cero)
        when(saleOrderDetailRepository.sumRevenue(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(saleOrderDetailRepository.sumCogs(any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // Act
        ExecutiveDashboardDTO result = executiveService.getExecutiveDashboard(null, null);

        // Assert: pct es null, no 0 — indica que no hay datos suficientes para el cálculo
        assertNull(result.getGrossMarginPct(),
                "grossMarginPct debe ser null cuando revenue=0, no dividir por cero");
        assertEquals(BigDecimal.ZERO, result.getGrossMargin());
    }

    @Test
    void getExecutiveDashboard_contaOrdenesPendientes() {
        // Arrange
        when(purchaseOrderRepository.countPendingAndApproved()).thenReturn(3L);
        when(saleOrderRepository.countPendingAndApproved()).thenReturn(2L);

        // Act
        ExecutiveDashboardDTO result = executiveService.getExecutiveDashboard(null, null);

        // Assert
        assertEquals(3L, result.getPendingPurchaseOrders());
        assertEquals(2L, result.getPendingSaleOrders());
    }

    // ── Valuación de inventario ──────────────────────────────────────────────

    @Test
    void getInventoryValuation_conCategorias_calculaPct() {
        // Arrange: totalValue=1000, cat1=600 (60%), cat2=400 (40%)
        List<Object[]> catRows = new ArrayList<>();
        catRows.add(new Object[]{1L, "Herramientas", 5L, new BigDecimal("600.00")});
        catRows.add(new Object[]{2L, "Consumibles",  3L, new BigDecimal("400.00")});
        when(productRepository.totalInventoryValue()).thenReturn(new BigDecimal("1000.00"));
        when(productRepository.inventoryValueByCategory()).thenReturn(catRows);

        // Act
        InventoryValuationDTO result = executiveService.getInventoryValuation();

        // Assert
        assertEquals(new BigDecimal("1000.00"), result.getTotalValue());
        assertEquals(2, result.getCategories().size());
        assertEquals(new BigDecimal("60.00"), result.getCategories().get(0).getPct());
        assertEquals(new BigDecimal("40.00"), result.getCategories().get(1).getPct());
    }

    @Test
    void getInventoryValuation_sinProductos_retornaListaVacia() {
        // Arrange: sin productos activos
        when(productRepository.totalInventoryValue()).thenReturn(BigDecimal.ZERO);
        when(productRepository.inventoryValueByCategory()).thenReturn(Collections.emptyList());

        // Act
        InventoryValuationDTO result = executiveService.getInventoryValuation();

        // Assert
        assertEquals(BigDecimal.ZERO, result.getTotalValue());
        assertTrue(result.getCategories().isEmpty());
    }

    @Test
    void getInventoryValuation_unaSolaCat_pctEs100() {
        // Arrange: una sola categoría debe tener pct=100%
        List<Object[]> singleRow = new ArrayList<>();
        singleRow.add(new Object[]{1L, "Única", 2L, new BigDecimal("500.00")});
        when(productRepository.totalInventoryValue()).thenReturn(new BigDecimal("500.00"));
        when(productRepository.inventoryValueByCategory()).thenReturn(singleRow);

        // Act
        InventoryValuationDTO result = executiveService.getInventoryValuation();

        // Assert
        assertEquals(new BigDecimal("100.00"), result.getCategories().get(0).getPct());
    }

    // ── Rentabilidad de ventas ───────────────────────────────────────────────

    @Test
    void getSalesProfitability_calculaAvgTicket() {
        // Arrange: 2 órdenes, revenue=500 → avgTicket=250
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 1, 31);

        when(saleOrderDetailRepository.sumRevenue(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("500.00"));
        when(saleOrderDetailRepository.sumCogs(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("300.00"));
        when(saleOrderDetailRepository.countDeliveredOrders(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(2L);

        // Act
        SalesProfitabilityDTO result = executiveService.getSalesProfitability(from, to);

        // Assert
        assertEquals(new BigDecimal("250.00"), result.getAvgTicket());
        assertEquals(2L, result.getDeliveredOrderCount());
        assertEquals(from, result.getFrom());
        assertEquals(to, result.getTo());
    }

    @Test
    void getSalesProfitability_sinOrdenes_avgTicketNull() {
        // Arrange: 0 órdenes → avgTicket debe ser null (no dividir por cero)
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 1, 31);

        when(saleOrderDetailRepository.sumRevenue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(saleOrderDetailRepository.sumCogs(any(), any())).thenReturn(BigDecimal.ZERO);
        when(saleOrderDetailRepository.countDeliveredOrders(any(), any())).thenReturn(0L);

        // Act
        SalesProfitabilityDTO result = executiveService.getSalesProfitability(from, to);

        // Assert: avgTicket null comunica al frontend que no hay datos suficientes
        assertNull(result.getAvgTicket(),
                "avgTicket debe ser null cuando no hay órdenes entregadas en el período");
    }

    @Test
    void getSalesProfitability_fromNull_lanzaExcepcion() {
        // El período es obligatorio para este reporte — diferente al dashboard
        assertThrows(RuntimeException.class,
                () -> executiveService.getSalesProfitability(null, LocalDate.now()),
                "Debe lanzar excepción cuando from es null");
    }

    @Test
    void getSalesProfitability_toNull_lanzaExcepcion() {
        assertThrows(RuntimeException.class,
                () -> executiveService.getSalesProfitability(LocalDate.now(), null),
                "Debe lanzar excepción cuando to es null");
    }

    @Test
    void getSalesProfitability_fromMayorTo_lanzaExcepcion() {
        // Validación de rango: from no puede ser posterior a to
        LocalDate from = LocalDate.of(2026, 12, 31);
        LocalDate to   = LocalDate.of(2026, 1, 1);

        assertThrows(RuntimeException.class,
                () -> executiveService.getSalesProfitability(from, to),
                "Debe lanzar excepción cuando from > to");
    }
}
