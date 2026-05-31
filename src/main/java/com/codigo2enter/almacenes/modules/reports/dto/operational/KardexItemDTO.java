package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Línea individual del Kardex de un producto.
 *
 * Representa un movimiento de stock (entrada o salida) con su saldo acumulado
 * posterior. El saldo se calcula iterativamente en el servicio — no está
 * almacenado en la BD, pero se reconstruye siempre de forma determinista
 * a partir de los movimientos ordenados cronológicamente.
 *
 * Los movimientos se ordenan ASC por createdAt para que el saldo sea
 * acumulativo de izquierda a derecha en la tabla del Kardex.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KardexItemDTO {

    /** Fecha y hora del movimiento. */
    private LocalDateTime date;

    /**
     * Tipo de movimiento como String: "IN" (entrada) o "OUT" (salida).
     * Se usa String (no enum) para que el frontend no dependa del enum Java.
     */
    private String type;

    /**
     * Número de unidades del movimiento. Siempre positivo:
     * el campo type determina si sumó o restó al saldo.
     */
    private Integer quantity;

    /**
     * Motivo o descripción del movimiento.
     * Ejemplos: "Recepción orden de compra OC-2026-0001", "Ajuste de inventario".
     */
    private String reason;

    /**
     * Saldo acumulado después de aplicar este movimiento.
     * Calculado iterativamente: balance anterior + quantity (IN) o - quantity (OUT).
     * Permite verificar visualmente la evolución del stock sin cálculos adicionales.
     */
    private Integer balance;

    /**
     * Username del usuario que registró el movimiento.
     * Aplana la relación @ManyToOne createdBy → User.username.
     */
    private String createdByUsername;
}
