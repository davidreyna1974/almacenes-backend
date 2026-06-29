package com.codigo2enter.almacenes.modules.inventory.model;

/**
 * Enumerado que define los dos tipos posibles de movimiento de inventario.
 *
 * Se almacena como String en la base de datos (EnumType.STRING en StockMovement)
 * para que los registros históricos sean legibles sin necesidad de traducción.
 *
 *   IN  → entrada de mercancía al almacén (compra, devolución, ajuste positivo)
 *   OUT → salida de mercancía del almacén (venta, merma, ajuste negativo)
 */
public enum MovementType {

    /** Incrementa el stock disponible del producto. */
    IN,

    /** Reduce el stock disponible del producto. El servicio valida que
     *  el resultado no sea negativo antes de aplicar el movimiento. */
    OUT
}
