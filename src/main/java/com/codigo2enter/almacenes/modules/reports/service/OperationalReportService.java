package com.codigo2enter.almacenes.modules.reports.service;

import com.codigo2enter.almacenes.modules.reports.dto.operational.KardexReportDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.LowStockReportItemDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.MovementsSummaryDTO;
import com.codigo2enter.almacenes.modules.reports.dto.operational.PendingOperationsDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de los informes operativos para operadores de almacén.
 *
 * Define cuatro reportes de gestión diaria del inventario:
 *   - Stock bajo mínimo (alerta de reposición)
 *   - Kardex por producto y período
 *   - Operaciones pendientes (compras y ventas)
 *   - Resumen de movimientos del período
 *
 * Todos los métodos son de solo lectura.
 */
public interface OperationalReportService {

    /**
     * Retorna los productos cuyo stock disponible (currentStock - reservedStock)
     * es menor o igual al minimumStock, ordenados por déficit descendente.
     * Los más críticos aparecen primero.
     *
     * @return lista de productos bajo mínimo, vacía si ninguno está en alerta
     */
    List<LowStockReportItemDTO> getLowStock();

    /**
     * Retorna el Kardex (historial de movimientos) de un producto en el período
     * con el saldo acumulado por movimiento.
     *
     * El openingStock se reconstruye hacia atrás desde el currentStock actual.
     * El saldo de cada movimiento se calcula iterativamente en orden cronológico.
     *
     * @param productId ID del producto
     * @param from      inicio del período (obligatorio)
     * @param to        fin del período (obligatorio)
     * @return DTO con historial de movimientos y saldos calculados
     * @throws RuntimeException si el producto no existe
     */
    KardexReportDTO getKardex(Long productId, LocalDate from, LocalDate to);

    /**
     * Retorna todas las órdenes de compra y venta en estado PENDING o APPROVED.
     * Permite al operador ver todos los compromisos activos en una sola pantalla.
     *
     * @return DTO con listas separadas de compras y ventas pendientes con contadores
     */
    PendingOperationsDTO getPendingOperations();

    /**
     * Retorna el resumen de movimientos de stock (entradas y salidas) en el período.
     *
     * @param from inicio del período
     * @param to   fin del período
     * @return DTO con totalIn, totalOut y netMovement del período
     */
    MovementsSummaryDTO getMovementsSummary(LocalDate from, LocalDate to);
}
