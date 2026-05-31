package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Kardex completo de un producto para un período.
 *
 * El Kardex es el historial de movimientos de inventario de un producto,
 * similar a un extracto bancario: cada línea muestra la operación y el saldo
 * resultante. Es la herramienta central de auditoría de inventario.
 *
 * El openingStock se reconstruye hacia atrás desde el estado actual:
 *   openingStock = currentStock - totalIn + totalOut
 * Esto garantiza consistencia con el stock actual sin requerir una columna
 * adicional en la BD.
 *
 * Criterio de éxito: si no hay movimientos en el período, la lista movements
 * está vacía y openingStock == closingStock == currentStock actual del producto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KardexReportDTO {

    /** ID del producto en la tabla products. */
    private Long productId;

    /** Stock Keeping Unit del producto. */
    private String sku;

    /** Nombre del producto para mostrar en la UI. */
    private String name;

    /** Inicio del período del Kardex. */
    private LocalDate from;

    /** Fin del período del Kardex. */
    private LocalDate to;

    /**
     * Stock al inicio del período, calculado hacia atrás:
     * openingStock = currentStock - totalIn + totalOut.
     * Permite verificar que los movimientos explican el cambio en stock.
     */
    private Integer openingStock;

    /**
     * Stock al cierre del período = currentStock actual del producto.
     * Snapshot en tiempo real; refleja todos los movimientos hasta ahora.
     */
    private Integer closingStock;

    /**
     * Suma de cantidades de todos los movimientos IN en el período.
     */
    private Integer totalIn;

    /**
     * Suma de cantidades de todos los movimientos OUT en el período.
     */
    private Integer totalOut;

    /**
     * Lista de movimientos individuales ordenados cronológicamente (ASC).
     * Cada elemento incluye el saldo acumulado después de aplicarse.
     */
    private List<KardexItemDTO> movements;
}
