package com.codigo2enter.almacenes.modules.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de entrada para agregar un detalle a una orden de venta.
 *
 * unitCost no se incluye — el servicio lo lee automáticamente de Product.unitCost
 * para evitar que el cliente pueda falsificar el costo histórico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderDetailRequestDTO {

    @NotNull(message = "El ID del producto es obligatorio")
    private Long productId;

    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a cero")
    private BigDecimal unitPrice;
}
