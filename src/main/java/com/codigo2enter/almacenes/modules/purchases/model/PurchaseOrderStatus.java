package com.codigo2enter.almacenes.modules.purchases.model;

/**
 * Enumerado que define los estados posibles de una orden de compra.
 *
 * Se almacena como String en la base de datos (@Enumerated(EnumType.STRING))
 * para que los registros históricos sean legibles sin necesidad de traducción
 * y para resistir cambios en el orden de declaración del enum.
 *
 * Máquina de estados:
 *
 *   CREATE → PENDING → APPROVED → RECEIVED  (terminal, impacta inventario)
 *                  ↘           ↘
 *               CANCELLED     CANCELLED     (terminal, no impacta inventario)
 *
 * Solo PENDING permite editar la orden y sus detalles.
 * RECEIVED y CANCELLED son estados terminales sin transiciones posibles.
 */
public enum PurchaseOrderStatus {

    /**
     * Orden creada, pendiente de autorización.
     * En este estado la orden puede editarse (notas, proveedor, detalles)
     * y puede transicionar a APPROVED o CANCELLED.
     * No impacta el inventario.
     */
    PENDING,

    /**
     * Orden autorizada, en espera de recepción física de la mercancía.
     * Los detalles quedan bloqueados — no se pueden agregar, editar ni quitar.
     * Puede transicionar a RECEIVED o CANCELLED.
     * No impacta el inventario.
     */
    APPROVED,

    /**
     * Mercancía recibida en el almacén.
     * Al alcanzar este estado, el servicio dispara registerStockMovement(IN)
     * por cada detalle de la orden, incrementando el stock automáticamente.
     * Estado terminal — no admite más transiciones.
     */
    RECEIVED,

    /**
     * Orden cancelada antes de ser recibida.
     * Puede cancelarse desde PENDING o APPROVED.
     * No impacta el inventario en ningún caso.
     * Estado terminal — no admite más transiciones.
     */
    CANCELLED
}
