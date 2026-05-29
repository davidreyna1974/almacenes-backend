package com.codigo2enter.almacenes.modules.sales.model;

/**
 * Estados del ciclo de vida de una orden de venta.
 *
 * Máquina de estados:
 *   PENDING  → APPROVED  : approveOrder()  — reserva stock (reservedStock += qty)
 *   APPROVED → DELIVERED : deliverOrder()  — decrementa stock físico (currentStock -= qty)
 *   PENDING  → CANCELLED : cancelOrder()   — sin impacto en stock
 *   APPROVED → CANCELLED : cancelOrder()   — libera reservas (reservedStock -= qty)
 *
 * Estados terminales: DELIVERED y CANCELLED — no admiten transiciones adicionales.
 */
public enum SaleOrderStatus {
    PENDING,
    APPROVED,
    DELIVERED,
    CANCELLED
}
