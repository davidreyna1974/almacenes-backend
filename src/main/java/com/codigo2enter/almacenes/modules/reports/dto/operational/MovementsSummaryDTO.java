package com.codigo2enter.almacenes.modules.reports.dto.operational;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Resumen de movimientos de stock (entradas y salidas) en un período.
 *
 * Responde la pregunta operativa: ¿cuánto entró y cuánto salió del almacén
 * en el período indicado? El netMovement positivo indica crecimiento de inventario;
 * negativo indica que salió más de lo que entró.
 *
 * Los movimientos incluyen todos los tipos: recepciones de órdenes de compra,
 * entregas de órdenes de venta y ajustes manuales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementsSummaryDTO {

    /** Inicio del período analizado (inclusivo). */
    private LocalDate from;

    /** Fin del período analizado (inclusivo). */
    private LocalDate to;

    /**
     * Total de unidades que ingresaron al almacén en el período
     * (movimientos de tipo IN).
     */
    private Integer totalIn;

    /**
     * Total de unidades que salieron del almacén en el período
     * (movimientos de tipo OUT).
     */
    private Integer totalOut;

    /**
     * Variación neta de inventario = totalIn - totalOut.
     * Positivo: el inventario creció en el período.
     * Negativo: el inventario decreció (más salidas que entradas).
     * Cero: entradas y salidas se compensaron exactamente.
     */
    private Integer netMovement;
}
