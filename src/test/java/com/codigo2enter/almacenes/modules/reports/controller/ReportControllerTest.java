package com.codigo2enter.almacenes.modules.reports.controller;

import com.codigo2enter.almacenes.core.security.JwtUtils;
import com.codigo2enter.almacenes.modules.reports.dto.executive.ExecutiveDashboardDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.InventoryValuationDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.SalesProfitabilityDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.AbcProductDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.InventoryTurnoverItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.PurchaseBySupplierDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.SalesTrendItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.TopProductDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.KardexReportDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.LowStockReportItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.MovementsSummaryDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.PendingOperationsDTO;
import com.codigo2enter.almacenes.modules.reports.service.ExecutiveReportService;
import com.codigo2enter.almacenes.modules.reports.service.ManagementReportService;
import com.codigo2enter.almacenes.modules.reports.service.OperationalReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de controlador (Tipo B) para ReportController.
 *
 * Verifica que cada endpoint retorna HTTP 200 y delega correctamente al servicio.
 * Los filtros de Spring Security están deshabilitados (addFilters=false) —
 * la seguridad se verifica en SecurityFilterTest.
 *
 * JwtUtils se mockea siempre porque SecurityConfig lo necesita para construir
 * JwtAuthenticationFilter aunque los filtros estén desactivados en este test.
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ExecutiveReportService  executiveService;
    @MockBean ManagementReportService managementService;
    @MockBean OperationalReportService operationalService;
    @MockBean JwtUtils jwtUtils;

    private static final String BASE = "/api/v1/reports";

    // ── Ejecutivos ───────────────────────────────────────────────────────────

    @Test
    void dashboard_retorna200() throws Exception {
        when(executiveService.getExecutiveDashboard(any(), any()))
                .thenReturn(ExecutiveDashboardDTO.builder()
                        .totalRevenue(BigDecimal.ZERO)
                        .totalCogs(BigDecimal.ZERO)
                        .grossMargin(BigDecimal.ZERO)
                        .inventoryValue(BigDecimal.ZERO)
                        .pendingPurchaseOrders(0L)
                        .pendingSaleOrders(0L)
                        .generatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(get(BASE + "/dashboard/executive"))
                .andExpect(status().isOk());
    }

    @Test
    void valuation_retorna200() throws Exception {
        when(executiveService.getInventoryValuation())
                .thenReturn(InventoryValuationDTO.builder()
                        .totalValue(BigDecimal.ZERO)
                        .categories(Collections.emptyList())
                        .generatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(get(BASE + "/inventory/valuation"))
                .andExpect(status().isOk());
    }

    @Test
    void profitability_retorna200() throws Exception {
        when(executiveService.getSalesProfitability(any(), any()))
                .thenReturn(SalesProfitabilityDTO.builder()
                        .from(LocalDate.now().minusDays(30))
                        .to(LocalDate.now())
                        .totalRevenue(BigDecimal.ZERO)
                        .totalCogs(BigDecimal.ZERO)
                        .grossMargin(BigDecimal.ZERO)
                        .deliveredOrderCount(0L)
                        .generatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(get(BASE + "/sales/profitability")
                        .param("from", "2026-01-01")
                        .param("to",   "2026-01-31"))
                .andExpect(status().isOk());
    }

    // ── Gestión ──────────────────────────────────────────────────────────────

    @Test
    void topPerformers_retorna200() throws Exception {
        when(managementService.getTopProducts(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/products/top-performers"))
                .andExpect(status().isOk());
    }

    @Test
    void topPerformers_limitPersonalizado_retorna200() throws Exception {
        when(managementService.getTopProducts(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/products/top-performers")
                        .param("limit", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void abc_retorna200() throws Exception {
        when(managementService.getAbcAnalysis(any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/inventory/abc"))
                .andExpect(status().isOk());
    }

    @Test
    void turnover_retorna200() throws Exception {
        when(managementService.getInventoryTurnover(any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/inventory/turnover"))
                .andExpect(status().isOk());
    }

    @Test
    void bySupplier_retorna200() throws Exception {
        when(managementService.getPurchasesBySupplier(any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/purchases/by-supplier"))
                .andExpect(status().isOk());
    }

    @Test
    void trend_retorna200() throws Exception {
        when(managementService.getSalesTrend(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/sales/trend"))
                .andExpect(status().isOk());
    }

    @Test
    void trend_groupByInvalido_retorna500() throws Exception {
        // El servicio lanza RuntimeException para groupBy inválido
        // GlobalExceptionHandler lo convierte en 500
        when(managementService.getSalesTrend(any(), any(), anyString()))
                .thenThrow(new RuntimeException("Valor de groupBy no reconocido: 'YEARLY'"));

        mockMvc.perform(get(BASE + "/sales/trend")
                        .param("groupBy", "YEARLY"))
                .andExpect(status().isInternalServerError());
    }

    // ── Operativos ───────────────────────────────────────────────────────────

    @Test
    void lowStock_retorna200() throws Exception {
        when(operationalService.getLowStock())
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE + "/inventory/low-stock"))
                .andExpect(status().isOk());
    }

    @Test
    void kardex_retorna200() throws Exception {
        when(operationalService.getKardex(anyLong(), any(), any()))
                .thenReturn(KardexReportDTO.builder()
                        .productId(1L)
                        .sku("P-001")
                        .name("Taladro")
                        .openingStock(0)
                        .closingStock(0)
                        .totalIn(0)
                        .totalOut(0)
                        .movements(Collections.emptyList())
                        .build());

        mockMvc.perform(get(BASE + "/inventory/kardex/1"))
                .andExpect(status().isOk());
    }

    @Test
    void pending_retorna200() throws Exception {
        when(operationalService.getPendingOperations())
                .thenReturn(PendingOperationsDTO.builder()
                        .pendingPurchaseOrders(Collections.emptyList())
                        .pendingSaleOrders(Collections.emptyList())
                        .totalPendingPurchases(0)
                        .totalPendingSales(0)
                        .build());

        mockMvc.perform(get(BASE + "/operations/pending"))
                .andExpect(status().isOk());
    }

    @Test
    void movements_retorna200() throws Exception {
        when(operationalService.getMovementsSummary(any(), any()))
                .thenReturn(MovementsSummaryDTO.builder()
                        .totalIn(0)
                        .totalOut(0)
                        .netMovement(0)
                        .build());

        mockMvc.perform(get(BASE + "/inventory/movements"))
                .andExpect(status().isOk());
    }
}
