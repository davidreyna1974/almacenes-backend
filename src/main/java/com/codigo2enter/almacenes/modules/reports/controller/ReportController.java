package com.codigo2enter.almacenes.modules.reports.controller;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador REST del módulo de reportes.
 *
 * Expone 12 endpoints GET de solo lectura — no hay escritura en este controlador.
 * Cero lógica de negocio: delegación pura a los tres servicios de reportes.
 *
 * Control de acceso definido en SecurityConfig (6 reglas antes de anyRequest):
 *   - /dashboard/** → solo ADMIN
 *   - /inventory/low-stock, /inventory/kardex/** → ADMIN, MANAGER, WAREHOUSEMAN
 *   - /operations/** → ADMIN, MANAGER, WAREHOUSEMAN, SALES
 *   - /inventory/** (resto) → ADMIN, MANAGER, WAREHOUSEMAN
 *   - /reports/** (resto) → ADMIN, MANAGER
 *
 * Los parámetros from/to se reciben como LocalDate (sin hora) — el servicio
 * los convierte internamente a LocalDateTime para los rangos de las queries.
 */
@Tag(name = "Reportes", description = "Reportes analíticos por audiencia")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ExecutiveReportService executiveService;
    private final ManagementReportService managementService;
    private final OperationalReportService operationalService;

    // ── REPORTES EJECUTIVOS ──────────────────────────────────────────────────

    /**
     * Dashboard ejecutivo con KPIs financieros del período.
     * El período es opcional — sin fechas se usa todo el historial.
     * Acceso: solo ADMIN (datos financieros sensibles de alto nivel).
     */
    @GetMapping("/dashboard/executive")
    public ResponseEntity<ExecutiveDashboardDTO> getExecutiveDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(executiveService.getExecutiveDashboard(from, to));
    }

    /**
     * Valuación del inventario activo por categoría.
     * Snapshot en tiempo real — no requiere período.
     * Acceso: ADMIN, MANAGER (visión estratégica del capital).
     */
    @GetMapping("/inventory/valuation")
    public ResponseEntity<InventoryValuationDTO> getInventoryValuation() {
        return ResponseEntity.ok(executiveService.getInventoryValuation());
    }

    /**
     * Análisis de rentabilidad de ventas del período.
     * El período (from/to) es obligatorio — el servicio lanza excepción si se omite.
     * Acceso: ADMIN, MANAGER.
     */
    @GetMapping("/sales/profitability")
    public ResponseEntity<SalesProfitabilityDTO> getSalesProfitability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(executiveService.getSalesProfitability(from, to));
    }

    // ── REPORTES DE GESTIÓN ──────────────────────────────────────────────────

    /**
     * Top N productos por revenue en el período.
     * El parámetro limit es opcional, por defecto 10, máximo 50 aplicado en el servicio.
     * Acceso: ADMIN, MANAGER.
     */
    @GetMapping("/products/top-performers")
    public ResponseEntity<List<TopProductDTO>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(managementService.getTopProducts(from, to, limit));
    }

    /**
     * Clasificación ABC de productos por revenue del período.
     * Acceso: ADMIN, MANAGER.
     */
    @GetMapping("/inventory/abc")
    public ResponseEntity<List<AbcProductDTO>> getAbcAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(managementService.getAbcAnalysis(from, to));
    }

    /**
     * Tasa de rotación de inventario por producto en el período.
     * Acceso: ADMIN, MANAGER, WAREHOUSEMAN (regla /inventory/** en SecurityConfig).
     */
    @GetMapping("/inventory/turnover")
    public ResponseEntity<List<InventoryTurnoverItemDTO>> getInventoryTurnover(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(managementService.getInventoryTurnover(from, to));
    }

    /**
     * Compras agrupadas por proveedor en el período (solo órdenes RECEIVED).
     * Acceso: ADMIN, MANAGER.
     */
    @GetMapping("/purchases/by-supplier")
    public ResponseEntity<List<PurchaseBySupplierDTO>> getPurchasesBySupplier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(managementService.getPurchasesBySupplier(from, to));
    }

    /**
     * Tendencia de ventas agrupada por período.
     * El parámetro groupBy acepta: DAY, WEEK, MONTH (default MONTH).
     * Acceso: ADMIN, MANAGER.
     */
    @GetMapping("/sales/trend")
    public ResponseEntity<List<SalesTrendItemDTO>> getSalesTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTH") String groupBy) {
        return ResponseEntity.ok(managementService.getSalesTrend(from, to, groupBy));
    }

    // ── REPORTES OPERATIVOS ──────────────────────────────────────────────────

    /**
     * Productos con stock disponible bajo el mínimo configurado.
     * Acceso: ADMIN, MANAGER, WAREHOUSEMAN.
     */
    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<LowStockReportItemDTO>> getLowStock() {
        return ResponseEntity.ok(operationalService.getLowStock());
    }

    /**
     * Kardex de un producto: historial de movimientos con saldo acumulado.
     * El período es opcional — sin fechas se retorna todo el historial.
     * Acceso: ADMIN, MANAGER, WAREHOUSEMAN.
     */
    @GetMapping("/inventory/kardex/{productId}")
    public ResponseEntity<KardexReportDTO> getKardex(
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(operationalService.getKardex(productId, from, to));
    }

    /**
     * Operaciones pendientes: órdenes de compra y venta en PENDING o APPROVED.
     * Acceso: ADMIN, MANAGER, WAREHOUSEMAN, SALES.
     */
    @GetMapping("/operations/pending")
    public ResponseEntity<PendingOperationsDTO> getPendingOperations() {
        return ResponseEntity.ok(operationalService.getPendingOperations());
    }

    /**
     * Resumen de movimientos de stock (entradas y salidas) en el período.
     * Acceso: ADMIN, MANAGER, WAREHOUSEMAN (regla /inventory/** en SecurityConfig).
     */
    @GetMapping("/inventory/movements")
    public ResponseEntity<MovementsSummaryDTO> getMovementsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(operationalService.getMovementsSummary(from, to));
    }
}
