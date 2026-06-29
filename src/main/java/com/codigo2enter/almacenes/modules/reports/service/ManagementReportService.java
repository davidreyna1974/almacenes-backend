package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.reports.dto.management.AbcProductDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.InventoryTurnoverItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.PurchaseBySupplierDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.SalesTrendItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.management.TopProductDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de los informes de gestión para mandos medios y analistas.
 *
 * Define cinco reportes orientados al análisis operativo y estratégico:
 *   - Top productos por revenue
 *   - Clasificación ABC
 *   - Rotación de inventario
 *   - Compras por proveedor
 *   - Tendencia de ventas
 *
 * Todos los métodos son de solo lectura.
 */
public interface ManagementReportService {

    /**
     * Retorna los N productos con mayor revenue en el período, ordenados DESC.
     *
     * @param from  inicio del período
     * @param to    fin del período
     * @param limit número máximo de productos a retornar (default 10, max 50)
     * @return lista de productos con rank, revenue, COGS y margen
     */
    List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, Integer limit);

    /**
     * Clasifica todos los productos con ventas en el período según el principio
     * de Pareto: A (top 80% revenue), B (siguiente 15%), C (resto 5%).
     * Lista vacía si no hay ventas en el período.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return lista de productos con su clasificación ABC y porcentajes acumulados
     */
    List<AbcProductDTO> getAbcAnalysis(LocalDate from, LocalDate to);

    /**
     * Calcula la tasa de rotación de inventario por producto en el período:
     * turnoverRate = COGS del período / valor actual del inventario.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return lista de productos con tasa de rotación e interpretación textual
     */
    List<InventoryTurnoverItemDTO> getInventoryTurnover(LocalDate from, LocalDate to);

    /**
     * Agrupa las órdenes de compra RECEIVED del período por proveedor.
     * Solo considera órdenes RECEIVED — las pendientes no representan dinero desembolsado.
     *
     * @param from inicio del período (por receivedAt)
     * @param to   fin del período
     * @return lista de proveedores con totales de compra, ordenada por monto DESC
     */
    List<PurchaseBySupplierDTO> getPurchasesBySupplier(LocalDate from, LocalDate to);

    /**
     * Tendencia de ventas (órdenes DELIVERED) agrupadas por día, semana o mes.
     *
     * @param from    inicio del período
     * @param to      fin del período
     * @param groupBy agrupamiento: "DAY", "WEEK" o "MONTH"
     * @return lista de puntos de tendencia ordenados cronológicamente
     * @throws RuntimeException si groupBy no es "DAY", "WEEK" ni "MONTH"
     */
    List<SalesTrendItemDTO> getSalesTrend(LocalDate from, LocalDate to, String groupBy);
}
