package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.reports.dto.executive.ExecutiveDashboardDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.InventoryValuationDTO;
import com.codigo2enter.almacenes.modules.reports.dto.executive.SalesProfitabilityDTO;

import java.time.LocalDate;

/**
 * Contrato de los informes ejecutivos de alto nivel.
 *
 * Define tres reportes financieros orientados a gerencia:
 *   - Dashboard con KPIs globales (período opcional)
 *   - Valuación del inventario por categoría (snapshot actual)
 *   - Rentabilidad de ventas para un período explícito
 *
 * Todos los métodos son de solo lectura — no generan efectos laterales en la BD.
 */
public interface ExecutiveReportService {

    /**
     * Retorna el dashboard ejecutivo con KPIs financieros del período indicado.
     * Si from o to son null, se usa un rango desde el inicio del tiempo hasta ahora.
     *
     * @param from inicio del período (opcional, null = inicio del tiempo)
     * @param to   fin del período (opcional, null = hoy + 1 día)
     * @return DTO con revenue, COGS, margen bruto, valor de inventario y pendientes
     */
    ExecutiveDashboardDTO getExecutiveDashboard(LocalDate from, LocalDate to);

    /**
     * Retorna la valuación actual del inventario desglosada por categoría.
     * El snapshot es en tiempo real — no requiere período.
     *
     * @return DTO con valor total y lista por categoría con porcentajes
     */
    InventoryValuationDTO getInventoryValuation();

    /**
     * Retorna el análisis de rentabilidad de ventas en el período indicado.
     * El período es obligatorio — el servicio lanza excepción si from o to son null
     * o si from > to.
     *
     * @param from inicio del período (obligatorio)
     * @param to   fin del período (obligatorio, debe ser >= from)
     * @return DTO con revenue, COGS, margen y ticket promedio del período
     * @throws RuntimeException si from o to son null, o si from > to
     */
    SalesProfitabilityDTO getSalesProfitability(LocalDate from, LocalDate to);
}
