package com.codigo2enter.almacenes.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de salida para un detalle de orden de venta.
 *
 * Incluye unitCost para que el futuro módulo financiero pueda calcular:
 *   margen_unitario = unitPrice - unitCost   (si unitCost != null)
 *   margen_total    = margen_unitario × quantity
 *
 * Si unitCost es null significa que el producto no tenía costo definido
 * al momento de crear o actualizar el detalle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderDetailResponseDTO {

    private Long id;
    private int quantity;
    private BigDecimal unitPrice;

    /** Costo capturado desde Product.unitCost al crear/actualizar el detalle. Null si no estaba definido. */
    private BigDecimal unitCost;

    /** quantity × unitPrice. Nunca involucra unitCost. */
    private BigDecimal subtotal;

    private Long productId;
    private String productSku;
    private String productName;
}
