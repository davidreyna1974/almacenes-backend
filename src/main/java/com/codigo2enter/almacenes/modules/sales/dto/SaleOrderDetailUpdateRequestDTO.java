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
 * DTO de entrada para actualizar cantidad y precio de un detalle existente.
 * El servicio re-lee Product.unitCost en cada actualización (mientras la orden
 * esté en PENDING) para reflejar cambios de costo desde que se creó el detalle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderDetailUpdateRequestDTO {

    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private int quantity;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a cero")
    private BigDecimal unitPrice;
}
